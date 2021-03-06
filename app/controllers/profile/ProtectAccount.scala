/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.profile

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import domain.models.connectors.UpdateLoggedInStateRequest
import domain.models.developers.LoggedInState
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import qr.{OtpAuthUri, QRCode}
import service.{MfaMandateService, MFAService, SessionService}
import views.html.protectaccount._

import scala.concurrent.{ExecutionContext, Future}
import controllers.LoggedInController
import controllers.Remove2SVConfirmForm
import controllers.FormKeys

@Singleton
class ProtectAccount @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                               val otpAuthUri: OtpAuthUri,
                               val mfaService: MFAService,
                               val sessionService: SessionService,
                               mcc: MessagesControllerComponents,
                               val errorHandler: ErrorHandler,
                               val mfaMandateService: MfaMandateService,
                               val cookieSigner : CookieSigner,
                               protectAccountSetupView: ProtectAccountSetupView,
                               protectedAccountView: ProtectedAccountView,
                               protectAccountView: ProtectAccountView,
                               protectAccountAccessCodeView: ProtectAccountAccessCodeView,
                               protectAccountCompletedView: ProtectAccountCompletedView,
                               protectAccountRemovalConfirmationView: ProtectAccountRemovalConfirmationView,
                               protectAccountRemovalAccessCodeView: ProtectAccountRemovalAccessCodeView,
                               protectAccountRemovalCompleteView: ProtectAccountRemovalCompleteView
                              )
                              (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedInController(mcc) {

  private val scale = 4
  val qrCode = QRCode(scale)

  def getQrCode: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.createMfaSecret(loggedIn.developer.userId).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", loggedIn.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(protectAccountSetupView(secret.toLowerCase().grouped(4).mkString(" "), qrImg))
    })
  }

  def getProtectAccount: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(loggedIn.developer.userId).map(dev => {
      dev.getOrElse(throw new RuntimeException).mfaEnabled.getOrElse(false) match {
        case true => Ok(protectedAccountView())
        case false => Ok(protectAccountView())
      }
    })
  }

  def getAccessCodePage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(protectAccountAccessCodeView(ProtectAccountForm.form)))
  }

  def getProtectAccountCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    Future.successful(Ok(protectAccountCompletedView()))
  }

  def protectAccount: Action[AnyContent] = atLeastPartLoggedInEnablingMfaAction { implicit request =>

    def logonAndComplete(): Result = {
      thirdPartyDeveloperConnector.updateSessionLoggedInState(loggedIn.session.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
      Redirect(controllers.profile.routes.ProtectAccount.getProtectAccountCompletedPage())
    }

    def invalidCode(form: ProtectAccountForm): Result = {
      val protectAccountForm = ProtectAccountForm
        .form
        .fill(form)
        .withError(key = "accessCode", message = "You have entered an incorrect access code")

      BadRequest(protectAccountAccessCodeView(protectAccountForm))
    }

    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountAccessCodeView(form)))
  },
      (form: ProtectAccountForm) => {
        for {
          mfaResponse <- mfaService.enableMfa(loggedIn.developer.userId, form.accessCode)
          result = {
            if (mfaResponse.totpVerified) logonAndComplete()
            else invalidCode(form)
          }
        } yield result
      })
  }

  def get2SVRemovalConfirmationPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalConfirmationView(Remove2SVConfirmForm.form)))
  }

  def confirm2SVRemoval: Action[AnyContent] = loggedInAction { implicit request =>
    Remove2SVConfirmForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountRemovalConfirmationView(form)))
    },
      form => {
        form.removeConfirm match {
          case Some("Yes") => Future.successful(Redirect(controllers.profile.routes.ProtectAccount.get2SVRemovalAccessCodePage()))
          case _ => Future.successful(Redirect(controllers.profile.routes.ProtectAccount.getProtectAccount()))
        }
      })
  }

  def get2SVRemovalAccessCodePage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalAccessCodeView(ProtectAccountForm.form)))
  }

  def remove2SV(): Action[AnyContent] = loggedInAction { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountRemovalAccessCodeView(form)))
    },
      form => {
        mfaService.removeMfa(loggedIn.developer.userId, loggedIn.email, form.accessCode).map(r =>
          r.totpVerified match {
            case true => Redirect(controllers.profile.routes.ProtectAccount.get2SVRemovalCompletePage())
            case _ =>
              val protectAccountForm = ProtectAccountForm.form.fill(form)
                .withError("accessCode", "You have entered an incorrect access code")

              BadRequest(protectAccountRemovalAccessCodeView(protectAccountForm))
          }
        )
      })
  }

  def get2SVRemovalCompletePage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalCompleteView()))
  }
}

final case class ProtectAccountForm(accessCode: String)

object ProtectAccountForm {
  def form: Form[ProtectAccountForm] = Form(
    mapping(
      "accessCode" -> text.verifying(FormKeys.accessCodeInvalidKey, s => s.matches("^[0-9]{6}$"))
    )(ProtectAccountForm.apply)(ProtectAccountForm.unapply)
  )
}

