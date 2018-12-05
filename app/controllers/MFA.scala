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
import service.SessionService

import scala.concurrent.Future

trait MFA extends LoggedInController {

  val connector: ThirdPartyDeveloperConnector
  val qrCode: QRCode
  val otpAuthUri: OTPAuthURI

  def start2SVSetup() = loggedInAction { implicit request =>
    connector.createMfaSecret(loggedIn.email).map(secret => {
      val uri = otpAuthUri(secret.toLowerCase, "HMRC Developer Hub", loggedIn.email)
      val qrImg = qrCode.generateDataImageBase64(uri.toString)
      Ok(views.html.secureAccountSetup(secret.toLowerCase().grouped(4).mkString(" "), qrImg))
    })
  }

  def show2SVPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccount()))
  }

  def show2SVAccessCodePage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccountAccessCode(Enable2SVForm.form)))
  }

  def show2SVCompletedPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccountCompleted()))
  }

  def enable2SV() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccountCompleted()))
  }
}

object MFA extends MFA with WithAppConfig {
  override val sessionService = SessionService
  override val connector = ThirdPartyDeveloperConnector
  private val scale = 7
  override val qrCode = QRCode(scale)
  override val otpAuthUri = OTPAuthURI
}

final case class Enable2SVForm(totpCode: String)

object Enable2SVForm {
  def form: Form[Enable2SVForm] = Form(
    mapping(
      "totp" -> text
    )(Enable2SVForm.apply)(Enable2SVForm.unapply)
  )
}

