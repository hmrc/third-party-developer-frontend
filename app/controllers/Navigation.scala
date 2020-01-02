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

import config.{ApplicationConfig, AuthConfigImpl, ErrorHandler}
import domain.LoggedInState.{LOGGED_IN, PART_LOGGED_IN_ENABLING_MFA}
import domain.{DeveloperSession, UserNavLinks}
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.OptionalAuthElement
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}
import service.SessionService
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

@Singleton
class Navigation @Inject()(val sessionService: SessionService,
                           val errorHandler: ErrorHandler,
                           implicit val appConfig: ApplicationConfig)
  extends FrontendController with AuthConfigImpl with HeaderEnricher with OptionalAuthElement {

  def navLinks(): Action[AnyContent] = AsyncStack {
    implicit request => {
      val username =
        loggedIn.flatMap {
          session: DeveloperSession => {
            session.loggedInState match {
              case LOGGED_IN => session.loggedInName
              case PART_LOGGED_IN_ENABLING_MFA => None
            }
          }
        }

      Future.successful(Ok(Json.toJson(UserNavLinks(username))))
    }
  }
}
