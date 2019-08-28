/*
 * Copyright 2019 HM Revenue & Customs
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
import domain.{PartAuthenticatedUser, PartAuthenticatedUserEnablingMfa}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request}
import qr.{OtpAuthUri, QRCode}
import service.{MFAService, SessionService}
import views.html.protectaccount
import views.html.protectaccount._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ProtectAccount @Inject()(val connector: ThirdPartyDeveloperConnector,
                               val otpAuthUri: OtpAuthUri,
                               val mfaService: MFAService,
                               val sessionService: SessionService,
                               val messagesApi: MessagesApi,
                               val errorHandler: ErrorHandler)
                              (implicit val appConfig: ApplicationConfig,
                               ec: ExecutionContext) extends LoggedInController {

  private val scale = 4
  val qrCode = QRCode(scale)
 
  def getQrCode: Action[AnyContent] = Action.async { implicit request =>
    val email = request.session.get("emailAddress").get // TODO : Naked get
    val partAuthenticatedUser: PartAuthenticatedUser = PartAuthenticatedUserEnablingMfa(email)

    connector.createMfaSecret(partAuthenticatedUser.email).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      val chunkSize = 4
      Ok(protectAccountSetup(secret.toLowerCase().grouped(chunkSize).mkString(" "), qrImg))
    })
  }


  def getProtectAccount: Action[AnyContent] = Action.async { implicit request =>
    // TODO: Fix to work when logged in
    val email = request.session.get("emailAddress").get // TODO : Naked get
    val partAuthenticatedUser: PartAuthenticatedUser = PartAuthenticatedUserEnablingMfa(email)

    connector.fetchDeveloper(partAuthenticatedUser.email).map(dev => {
      if (dev.getOrElse(throw new RuntimeException).mfaEnabled.getOrElse(false)) {
        Ok(protectedAccount())
          // TODO: Do we need to do this? Is it already in the session?
          .withSession(request.session + "emailAddress" -> partAuthenticatedUser.email)
      } else {
        Ok(protectaccount.protectAccount())

      }
    })
  }

  def getAccessCodePage: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(protectAccountAccessCode(ProtectAccountForm.form)))
  }

  def getProtectAccountCompletedPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountCompleted()))
  }

  //TODO if enabling mfa during login it should log you in at this point
  def protectAccount(): Action[AnyContent] = Action.async { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountAccessCode(form)))
    },
    form => {
      val partAuthenticatedUserEnablingMfa = PartAuthenticatedUserEnablingMfa(request.session.get("emailAddress").get)

      mfaService.enableMfa(partAuthenticatedUserEnablingMfa.email, form.accessCode).map(r => {
        if (r.totpVerified) {
          // TODO - Sort the session (actually login. Check session nonce and call auth - all in TPD?)
          Redirect(routes.ProtectAccount.getProtectAccountCompletedPage())
        } else {
          BadRequest(protectAccountAccessCode(ProtectAccountForm.form.fill(form)
            .withError("accessCode", "You have entered an incorrect access code")))
        }
      })
    })
  }

  def get2SVRemovalConfirmationPage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalConfirmation(Remove2SVConfirmForm.form)))
  }

  def confirm2SVRemoval(): Action[AnyContent] = loggedInAction { implicit request =>
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
          if (r.totpVerified) {
            Redirect(routes.ProtectAccount.get2SVRemovalCompletePage())
          } else {
            BadRequest(protectAccountRemovalAccessCode(ProtectAccountForm.form.fill(form).withError("accessCode", "You have entered an incorrect access code")))
          }
        )
    })
  }

  def get2SVRemovalCompletePage(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalComplete()))
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

