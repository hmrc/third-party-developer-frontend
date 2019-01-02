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
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import qr.{OtpAuthUri, QRCode}
import service.{MFAService, SessionService}
import views.html.protectAccountRemovalAccessCode

import scala.concurrent.Future

@Singleton
class ProtectAccount @Inject()(val connector: ThirdPartyDeveloperConnector,
                               val otpAuthUri: OtpAuthUri,
                               val mfaService: MFAService,
                               val sessionService: SessionService,
                               val errorHandler: ErrorHandler)(
                               implicit val appConfig: ApplicationConfig) extends LoggedInController {

  private val scale = 7
  val qrCode = QRCode(scale)
 
  def getQrCode() = loggedInAction { implicit request =>
    connector.createMfaSecret(loggedIn.email).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", loggedIn.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(views.html.protectAccountSetup(secret.toLowerCase().grouped(4).mkString(" "), qrImg))
    })
  }

  def getProtectAccount() = loggedInAction { implicit request =>
    connector.fetchDeveloper(loggedIn.email).map(dev => {
      dev.getOrElse(throw new RuntimeException).mfaEnabled.getOrElse(false) match {
        case true => Ok(views.html.protectedAccount())
        case false => Ok(views.html.protectAccount())
      }
    })
  }

  def getAccessCodePage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountAccessCode(ProtectAccountForm.form)))
  }

  def getProtectAccountCompletedPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountCompleted()))
  }

  def protectAccount() = loggedInAction { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(views.html.protectAccountAccessCode(form)))
    },
    form => {
      mfaService.enableMfa(loggedIn.email, form.accessCode).map(r => {
        r.totpVerified match{
          case true => Redirect(routes.ProtectAccount.getProtectAccountCompletedPage())
          case _ => BadRequest(views.html.protectAccountAccessCode(ProtectAccountForm.form.fill(form).withError("accessCode", "You have entered an incorrect access code")))
        }
      })

    })
  }

  def get2SVRemovalConfirmationPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountRemovalConfirmation(Remove2SVConfirmForm.form)))
  }

  def confirm2SVRemoval() = loggedInAction { implicit request =>
    Remove2SVConfirmForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(views.html.protectAccountRemovalConfirmation(form)))
    },
      form => {
        form.removeConfirm match {
          case Some("Yes") => Future.successful(Redirect(routes.ProtectAccount.get2SVRemovalAccessCodePage()))
          case _ => Future.successful(Redirect(routes.ProtectAccount.getProtectAccount()))
        }
      })
  }

  def get2SVRemovalAccessCodePage() = loggedInAction { implicit request =>
    Future.successful(Ok(protectAccountRemovalAccessCode(ProtectAccountForm.form)))
  }

  def remove2SV() = loggedInAction { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(protectAccountRemovalAccessCode(form)))
    },
      form => {
        mfaService.removeMfa(loggedIn.email, form.accessCode).map(r =>
          r.totpVerified match {
            case true => Redirect(routes.ProtectAccount.get2SVRemovalCompletePage())
            case _ => BadRequest(protectAccountRemovalAccessCode(ProtectAccountForm.form.fill(form).withError("accessCode", "You have entered an incorrect access code")))
          }
        )
    })
  }

  def get2SVRemovalCompletePage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountRemovalComplete()))
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

