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

import config.{ApplicationConfig, AuthConfigImpl}
import domain.UserNavLinks
import jp.t2v.lab.play2.auth.OptionalAuthElement
import play.api.libs.json._
import service.SessionService
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

trait Navigation extends FrontendController with HeaderEnricher with AuthConfigImpl with OptionalAuthElement {

  def navLinks() = AsyncStack { implicit request =>
    val username = loggedIn.map(_.displayedName)
    Future.successful(Ok(Json.toJson(UserNavLinks(username))))
  }
}

object Navigation extends Navigation {
  override val sessionService = SessionService
  override val appConfig = ApplicationConfig
}
