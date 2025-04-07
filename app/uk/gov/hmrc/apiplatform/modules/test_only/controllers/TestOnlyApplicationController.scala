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
import play.api.libs.json.Json
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment.PRODUCTION
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.test_only.connectors.{TestOnlyTpaProductionConnector, TestOnlyTpaSandboxConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoggedInController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

@Singleton
class TestOnlyApplicationController @Inject() (
    sandboxConnector: TestOnlyTpaSandboxConnector,
    productionConnector: TestOnlyTpaProductionConnector,
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,
    mcc: MessagesControllerComponents
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig,
    val environmentNameService: EnvironmentNameService
  ) extends LoggedInController(mcc) {

  def cloneApplication(environment: Environment, appId: ApplicationId): Action[AnyContent] = Action.async { implicit request =>
    val connector = environment match {
      case PRODUCTION => productionConnector
      case _          => sandboxConnector
    }
    connector.clone(environment)(appId).map(app => Ok(Json.toJson(app)))
  }
}
