/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.test_only.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import views.helper.EnvironmentNameService

import play.api.libs.crypto.CookieSigner
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.test_only.connectors.TestOnlyTpdConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

@Singleton
class TestOnlyUserController @Inject() (
    connector: TestOnlyTpdConnector,
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,
    mcc: MessagesControllerComponents
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig,
    val environmentNameService: EnvironmentNameService
  ) extends LoggedInController(mcc) {

  def cloneUser(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[LaxEmailAddress] { emailAddress =>
      connector.clone(emailAddress).map(u => Created(Json.toJson(u)))
    }
  }

  def findUserByEmail(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[LaxEmailAddress] { emailAddress =>
      connector.findUserByEmail(emailAddress).map(_ match {
        case None       => NotFound
        case Some(user) => Ok(Json.toJson(user))
      })
    }
  }

  def peekAtPasswordResetCode(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[LaxEmailAddress] { emailAddress =>
      connector.peekAtPasswordResetCode(emailAddress).map(_ match {
        case None       => NotFound
        case Some(code) => Ok(Json.toJson(code))
      })
    }
  }

  def peekAtRegistrationVerificationCode(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[LaxEmailAddress] { emailAddress =>
      connector.peekAtRegistrationVerificationCode(emailAddress).map(_ match {
        case None       => NotFound
        case Some(code) => Ok(Json.toJson(code))
      })
    }
  }

  def peekAtSmsAccessCode(): Action[JsValue] = Action.async(parse.tolerantJson) { implicit request =>
    withJsonBody[LaxEmailAddress] { emailAddress =>
      connector.peekAtSmsAccessCode(emailAddress).map {
        case None             => NotFound
        case Some(accessCode) => Ok(Json.toJson(accessCode))
      }
    }
  }
}
