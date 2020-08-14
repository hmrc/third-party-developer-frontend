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

package service

import config.ApplicationConfig
import connectors._
import domain.models.applications.{Application, Environment}
import play.api.http.Status
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import utils.AsyncHmrcSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{successful,failed}

class ConnectorsWrapperSpec extends AsyncHmrcSpec {

  val mockAppConfig = mock[ApplicationConfig]

  trait Setup {
    implicit val hc = HeaderCarrier()

    val connectors = new ConnectorsWrapper(
      mock[ThirdPartyApplicationSandboxConnector],
      mock[ThirdPartyApplicationProductionConnector],
      mock[SubscriptionFieldsConnector],
      mock[SubscriptionFieldsConnector],
      mockAppConfig
    )

    def theProductionConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      when(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(Some(application)))
      when(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(None))
    }

    def theSandboxConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      when(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(None))
      when(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(Some(application)))
    }

    def givenProductionSuccess() = {
      when(connectors.productionApplicationConnector.fetchApplicationById(productionApplicationId)).thenReturn(Future.successful(Some(productionApplication)))
    }

    def givenSandboxFailure(errorCode: Int): Any = {
      val error = if (errorCode / 100 == 4) Upstream4xxResponse("message", errorCode, errorCode) else Upstream5xxResponse("message", errorCode, errorCode)
      when(connectors.sandboxApplicationConnector.fetchApplicationById(productionApplicationId)).thenReturn(failed(error))
    }
  }

  val productionApplicationId = "Application ID"
  val productionClientId = "hBnFo14C0y4SckYUbcoL2PbFA40a"
  val productionApplication =
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("description"))
  val sandboxApplicationId = "Application ID"
  val sandboxClientId = "Client ID"
  val sandboxApplication = Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.SANDBOX, Some("description"))

  "fetchByApplicationId" when {
    "return the application fetched from the production connector when it exists there" in new Setup {
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      val result = await(connectors.fetchApplicationById(productionApplicationId))
      result shouldBe Some(productionApplication)
    }

    "return the application fetched from the production connector when it exists there and sandbox throws 4xx" in new Setup {
      givenProductionSuccess()
      givenSandboxFailure(Status.BAD_REQUEST)

      val result = await(connectors.fetchApplicationById(productionApplicationId))
      result shouldBe Some(productionApplication)
    }

    "return the application fetched from the production connector when it exists there and sandbox throws 5xx" in new Setup {
      givenProductionSuccess()
      givenSandboxFailure(Status.BAD_REQUEST)

      val result = await(connectors.fetchApplicationById(productionApplicationId))
      result shouldBe Some(productionApplication)
    }

    "return the application fetched from the sandbox connector when it exists there" in new Setup {
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      val result = await(connectors.fetchApplicationById(sandboxApplicationId))
      result shouldBe Some(sandboxApplication)
    }
  }
}
