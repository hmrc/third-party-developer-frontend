/*
 * Copyright 2018 HM Revenue & Customs
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

import connectors.ThirdPartyDeveloperConnector
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages.Implicits._
import qr.{OTPAuthURI, QRCode}
import service.{EnableMFAService, SessionService}

import scala.concurrent.Future

trait MFA extends LoggedInController {

  val connector: ThirdPartyDeveloperConnector
  val qrCode: QRCode
  val otpAuthUri: OTPAuthURI
  val enableMFAService: EnableMFAService

  def start2SVSetup() = loggedInAction { implicit request =>
    connector.createMfaSecret(loggedIn.email).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", loggedIn.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(views.html.protectAccountSetup(secret.toLowerCase().grouped(4).mkString(" "), qrImg))
    })
  }

  def show2SVPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccount()))
  }

  def show2SVAccessCodePage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountAccessCode(Enable2SVForm.form)))
  }

  def show2SVCompletedPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountCompleted()))
  }

  def enable2SV() = loggedInAction { implicit request =>
    Enable2SVForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(views.html.protectAccountAccessCode(form)))
    },
    form => {
      enableMFAService.enableMfa(loggedIn.email, form.totpCode).map(r => {
        r.totpVerified match{
          case true => Redirect(routes.MFA.show2SVCompletedPage())
          case _ => BadRequest(views.html.protectAccountAccessCode(Enable2SVForm.form.fill(form).withError("totp", "You have entered an incorrect access code")))
        }
      })

    })
  }
}

object MFA extends MFA with WithAppConfig {
  override val sessionService = SessionService
  override val connector = ThirdPartyDeveloperConnector
  private val scale = 7
  override val qrCode = QRCode(scale)
  override val otpAuthUri = OTPAuthURI
  override val enableMFAService = EnableMFAService
}

final case class Enable2SVForm(totpCode: String)

object Enable2SVForm {
  def form: Form[Enable2SVForm] = Form(
    mapping(
      "totp" -> text.verifying(FormKeys.totpInvalidKey, s => s.matches("^[0-9]{6}$"))
    )(Enable2SVForm.apply)(Enable2SVForm.unapply)
  )
}

