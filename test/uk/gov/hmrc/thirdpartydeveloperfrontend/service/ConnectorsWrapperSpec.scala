/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.Period
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

import play.api.http.Status
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ConnectorsWrapperSpec extends AsyncHmrcSpec with FixedClock {

  val mockAppConfig = mock[ApplicationConfig]

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connectors = new ConnectorsWrapper(
      mock[ThirdPartyApplicationSandboxConnector],
      mock[ThirdPartyApplicationProductionConnector],
      mock[SubscriptionFieldsConnector],
      mock[SubscriptionFieldsConnector],
      mock[PushPullNotificationsConnector],
      mock[PushPullNotificationsConnector],
      mockAppConfig
    )

    def theProductionConnectorWillReturnTheApplication(applicationId: ApplicationId, application: Application) = {
      when(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(Some(application)))
      when(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(None))
    }

    def theSandboxConnectorWillReturnTheApplication(applicationId: ApplicationId, application: Application) = {
      when(connectors.productionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(None))
      when(connectors.sandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(Future.successful(Some(application)))
    }

    def givenProductionSuccess() = {
      when(connectors.productionApplicationConnector.fetchApplicationById(productionApplicationId)).thenReturn(Future.successful(Some(productionApplication)))
    }

    def givenSandboxFailure(errorCode: Int): Any = {
      val error = if (errorCode / 100 == 4) UpstreamErrorResponse("message", errorCode, errorCode) else UpstreamErrorResponse("message", errorCode, errorCode)
      when(connectors.sandboxApplicationConnector.fetchApplicationById(productionApplicationId)).thenReturn(failed(error))
    }
  }

  val productionApplicationId = ApplicationId.random
  val productionClientId      = ClientId("hBnFo14C0y4SckYUbcoL2PbFA40a")
  val grantLength             = Period.ofDays(547)

  val productionApplication =
    Application(
      productionApplicationId,
      productionClientId,
      "name",
      instant,
      Some(instant),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("description")
    )
  val sandboxApplicationId  = ApplicationId.random
  val sandboxClientId       = ClientId("Client ID")

  val sandboxApplication = Application(
    sandboxApplicationId,
    sandboxClientId,
    "name",
    instant,
    Some(instant),
    None,
    grantLength,
    Environment.SANDBOX,
    Some("description")
  )

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
