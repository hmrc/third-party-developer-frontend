/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import domain.{LoggedInState, UpdateLoggedInStateRequest}
import javax.inject.{Inject, Singleton}
import model.MfaMandateDetails
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import qr.{OtpAuthUri, QRCode}
import service.{MFAService, MfaMandateService, SessionService}
import views.html.protectaccount._
import views.html.{add2SV, protectaccount, userDidNotAdd2SV}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProtectAccount @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                               val otpAuthUri: OtpAuthUri,
                               val mfaService: MFAService,
                               val sessionService: SessionService,
                               val messagesApi: MessagesApi,
                               val errorHandler: ErrorHandler,
                               val mfaMandateService: MfaMandateService)
                              (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedInController {

  private val scale = 4
  val qrCode = QRCode(scale)

  def getQrCode: Action[AnyContent] = atLeastPartLoggedInEnablingMfa { implicit request =>
    thirdPartyDeveloperConnector.createMfaSecret(loggedIn.email).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", loggedIn.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(protectAccountSetup(secret.toLowerCase().grouped(4).mkString(" "), qrImg))
    })
  }

  def getProtectAccount: Action[AnyContent] = atLeastPartLoggedInEnablingMfa { implicit request =>
    thirdPartyDeveloperConnector.fetchDeveloper(loggedIn.email).map(dev => {
      dev.getOrElse(throw new RuntimeException).mfaEnabled.getOrElse(false) match {
        case true => Ok(protectedAccount())
        case false => Ok(protectaccount.protectAccount())
      }
    })
  }

  def getAccessCodePage: Action[AnyContent] = atLeastPartLoggedInEnablingMfa { implicit request =>
    Future.successful(Ok(protectAccountAccessCode(ProtectAccountForm.form)))
  }

  def getProtectAccountCompletedPage: Action[AnyContent] = atLeastPartLoggedInEnablingMfa { implicit request =>
    Future.successful(Ok(protectAccountCompleted()))
  }

  def protectAccount: Action[AnyContent] = atLeastPartLoggedInEnablingMfa { implicit request =>

    def logonAndComplete(): Result = {
      thirdPartyDeveloperConnector.updateSessionLoggedInState(loggedIn.session.sessionId, UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN))
      Redirect(routes.ProtectAccount.getProtectAccountCompletedPage())
    }

    def invalidCode(form: ProtectAccountForm): Result = {
      val protectAccountForm = ProtectAccountForm
        .form
        .fill(form)
        .withError(key = "accessCode", message = "You have entered an incorrect access code")

      BadRequest(protectAccountAccessCode(protectAccountForm))
    }

    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountAccessCode(form)))
  },
      (form: ProtectAccountForm) => {
        for {
          mfaResponse <- mfaService.enableMfa(loggedIn.email, form.accessCode)
          result = {
            if (mfaResponse.totpVerified) logonAndComplete()
            else invalidCode(form)
          }
        } yield result
      })
  }

  def get2SVRemovalConfirmationPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalConfirmation(Remove2SVConfirmForm.form)))
  }

  def confirm2SVRemoval: Action[AnyContent] = loggedInAction { implicit request =>
    Remove2SVConfirmForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountRemovalConfirmation(form)))
    },
      form => {
        form.removeConfirm match {
          case Some("Yes") => Future.successful(Redirect(routes.ProtectAccount.get2SVRemovalAccessCodePage()))
          case _ => Future.successful(Redirect(routes.ProtectAccount.getProtectAccount()))
        }
      })
  }

  def get2SVRemovalAccessCodePage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalAccessCode(ProtectAccountForm.form)))
  }

  def remove2SV(): Action[AnyContent] = loggedInAction { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountRemovalAccessCode(form)))
    },
      form => {
        mfaService.removeMfa(loggedIn.email, form.accessCode).map(r =>
          r.totpVerified match {
            case true => Redirect(routes.ProtectAccount.get2SVRemovalCompletePage())
            case _ =>
              val protectAccountForm = ProtectAccountForm.form.fill(form)
                .withError("accessCode", "You have entered an incorrect access code")

              BadRequest(protectAccountRemovalAccessCode(protectAccountForm))
          }
        )
      })
  }

  def get2SVRemovalCompletePage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalComplete()))
  }

  def get2SVNotSetPage(): Action[AnyContent] = loggedInAction { implicit request =>
    successful(Ok(userDidNotAdd2SV()))
  }
  
  def get2svRecommendationPage(): Action[AnyContent] = loggedInAction {
    implicit request => {

      for {
        showAdminMfaMandateMessage <- mfaMandateService.showAdminMfaMandatedMessage(loggedIn.email)
        mfaMandateDetails = MfaMandateDetails(showAdminMfaMandateMessage, mfaMandateService.daysTillAdminMfaMandate.getOrElse(0))
      }  yield (Ok(add2SV(mfaMandateDetails)))
    }
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

