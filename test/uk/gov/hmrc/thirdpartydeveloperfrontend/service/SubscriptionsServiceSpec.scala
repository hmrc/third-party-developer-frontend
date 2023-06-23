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

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}

class SubscriptionsServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne          = ApiVersion("1.0")
  val versionTwo          = ApiVersion("2.0")
  val grantLength: Period = Period.ofDays(547)

  val email = "bob@example.com".toLaxEmail

  trait Setup
      extends ApplicationCommandConnectorMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector    = mock[SubscriptionFieldsConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector     = mock[PushPullNotificationsConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]
    val mockApmConnector: ApmConnector         = mock[ApmConnector]

    val subscriptionsService = new SubscriptionsService(
      mockDeskproConnector,
      mockApmConnector,
      ApplicationCommandConnectorMock.aMock,
      FixedClock.clock
    )

    def theProductionConnectorthenReturnTheApplication(applicationId: ApplicationId, application: Application): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
    }

  }

  val productionApplicationId = ApplicationId.random
  val productionClientId      = ClientId(s"client-id-${randomUUID().toString}")

  val productionApplication: Application =
    Application(
      productionApplicationId,
      productionClientId,
      "name",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("description"),
      Set()
    )

  "isSubscribedToApi" should {
    val subscriptions = Set(
      ApiIdentifier(ApiContext("first context"), versionOne),
      ApiIdentifier(ApiContext("second context"), versionOne)
    )
    val appWithData   = ApplicationWithSubscriptionData(buildApplication("email@example.com".toLaxEmail), subscriptions)

    "return false when the application has no subscriptions to the requested api version" in new Setup {
      val apiContext   = ApiContext("third context")
      val apiVersion   = ApiVersion("3.0")
      val subscription = ApiIdentifier(apiContext, apiVersion)

      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithData)))

      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithData.application.id, subscription))

      result shouldBe false
    }

    "return true when the application is subscribed to the requested api version" in new Setup {
      val apiContext   = ApiContext("first context")
      val apiVersion   = versionOne
      val subscription = ApiIdentifier(apiContext, apiVersion)

      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithData)))

      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithData.application.id, subscription))

      result shouldBe true
    }
  }

  "Subscribe to API" should {
    "with no subscription fields definitions" in new Setup {

      private val context = ApiContext("api1")
      private val version = versionOne

      private val subscription = ApiIdentifier(context, version)

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(productionApplication)

      private val result =
        await(subscriptionsService.subscribeToApi(productionApplication, subscription, email))

      result.isRight shouldBe true
      result shouldBe Right(DispatchSuccessResult(productionApplication))
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {
      private val context       = ApiContext("api1")
      private val version       = versionOne
      private val apiIdentifier = ApiIdentifier(context, version)

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(productionApplication)

      when(mockProductionSubscriptionFieldsConnector.deleteFieldValues(productionClientId, context, version))
        .thenReturn(successful(FieldsDeleteSuccessResult))

      private val result =
        await(subscriptionsService.unsubscribeFromApi(productionApplication, apiIdentifier, email))

      result.isRight shouldBe true
      result shouldBe Right(DispatchSuccessResult(productionApplication))
    }
  }
}
