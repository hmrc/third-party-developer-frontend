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

package unit.service

import config.ApplicationConfig
import connectors.{ApiSubscriptionFieldsConnector, ThirdPartyApplicationConnector}
import domain._
import org.mockito.BDDMockito.given
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import service.{Connectors, ConnectorsWrapper}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.Future

class ConnectorsWrapperSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  val mockAppConfig = mock[ApplicationConfig]

  trait Setup {
    implicit val hc = new HeaderCarrier()

    val connectors = new ConnectorsWrapper {
      override val sandboxSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
      override val sandboxApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
      override val applicationConfig = mockAppConfig
      override val productionApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
      override val productionSubscriptionFieldsConnector: ApiSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsConnector]
    }


    def theProductionConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      given(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(Some(application)))
      given(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
    }

    def theSandboxConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      given(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
      given(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(Some(application)))
    }

    def givenProductionSuccess() = {
      given(connectors.productionApplicationConnector.fetchApplicationById(productionApplicationId)).willReturn(Future.successful(Some(productionApplication)))
    }

    def givenSandboxFailure(errorCode: Int): Any = {
      val error = if (errorCode / 100 == 4) Upstream4xxResponse("message", errorCode, errorCode) else Upstream5xxResponse("message", errorCode, errorCode)
      given(connectors.sandboxApplicationConnector.fetchApplicationById(productionApplicationId)).willReturn(Future.failed(error))
    }
  }

  val productionApplicationId = "Application ID"
  val productionClientId = "hBnFo14C0y4SckYUbcoL2PbFA40a"
  val productionApplication = Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, Environment.PRODUCTION, Some("description"))
  val sandboxApplicationId = "Application ID"
  val sandboxClientId = "Client ID"
  val sandboxApplication = Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, Environment.SANDBOX, Some("description"))

  "fetchByApplicationId" should {
    "when strategic sandbox is enabled" should {
      "return the application fetched from the production connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(connectors.fetchApplicationById(productionApplicationId))
        result shouldBe productionApplication
      }

      "return the application fetched from the production connector when it exists there and sandbox throws 4xx" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)

        givenProductionSuccess()
        givenSandboxFailure(400)

        val result = await(connectors.fetchApplicationById(productionApplicationId))
        result shouldBe productionApplication
      }

      "return the application fetched from the production connector when it exists there and sandbox throws 5xx" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)

        givenProductionSuccess()
        givenSandboxFailure(400)

        val result = await(connectors.fetchApplicationById(productionApplicationId))
        result shouldBe productionApplication
      }

      "return the application fetched from the sandbox connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
        val result = await(connectors.fetchApplicationById(sandboxApplicationId))
        result shouldBe sandboxApplication
      }
    }

    "when strategic sandbox is not enabled" should {
      "return the application fetched from the production connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(false)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(connectors.fetchApplicationById(productionApplicationId))
        result shouldBe productionApplication
      }
    }
  }

  "connectorsForApplication" should {
    "when strategic sandbox is enabled" should {
      "return production connectors if defined" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(connectors.forApplication(productionApplicationId))
        result shouldBe Connectors(connectors.productionApplicationConnector, connectors.productionSubscriptionFieldsConnector)
      }

      "return sandbox connectors if defined" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
        val result = await(connectors.forApplication(sandboxApplicationId))
        result shouldBe Connectors(connectors.sandboxApplicationConnector, connectors.sandboxSubscriptionFieldsConnector)
      }
    }

    "when strategic sandbox is not enabled" should {
      "return production connectors when they exist" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(false)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(connectors.forApplication(productionApplicationId))
        result shouldBe Connectors(connectors.productionApplicationConnector, connectors.productionSubscriptionFieldsConnector)
      }
    }
  }

}
