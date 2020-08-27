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
import domain.models.views.UserNavLinks
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{ApplicationService, SessionService, ApplicationActionService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Navigation @Inject()(
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    mcc: MessagesControllerComponents,
    val cookieSigner : CookieSigner)
    (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) {

  def navLinks: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request: MaybeUserRequest[AnyContent] =>
    val username = request.developerSession.flatMap(_.loggedInName)

    Future.successful(Ok(Json.toJson(UserNavLinks(username))))
  }
}
