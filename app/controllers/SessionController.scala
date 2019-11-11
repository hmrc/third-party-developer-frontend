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
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import service.{AuditService, SessionService}

import scala.concurrent.Future.successful

@Singleton
class SessionController @Inject()(val auditService: AuditService,
                        val sessionService: SessionService,
                        val connector: ThirdPartyDeveloperConnector,
                        val errorHandler: ErrorHandler,
                        val messagesApi: MessagesApi,
                        implicit val appConfig: ApplicationConfig)
  extends LoggedInController with PasswordChange {

  def keepAlive(): Action[AnyContent] = loggedInAction { implicit request =>
    // TODO : Test me
    successful(NoContent)
  }
}
