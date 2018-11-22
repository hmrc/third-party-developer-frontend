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
import play.api.i18n.Messages.Implicits._
import service.SessionService

import scala.concurrent.Future

trait MFA extends LoggedInController {

  val connector: ThirdPartyDeveloperConnector

  def start2SVSetup() = loggedInAction { implicit request =>
      connector.createMfaSecret(loggedIn.email).map(secret => Ok(views.html.secureAccountSetup(secret.toLowerCase().grouped(4).mkString(" "))))
  }


  def show2SVPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccount()))
  }

  def show2SVAccessCodePage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccountAccessCode()))
  }

  def show2SVCompletedPage() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.secureAccountCompleted()))
  }

}

object MFA extends MFA with WithAppConfig {
  override val sessionService = SessionService
  override val connector = ThirdPartyDeveloperConnector
}


