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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptionsFixtures
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorCommandModuleMockModule, ApmConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonEmailData
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class SubscriptionsServiceSpec extends AsyncHmrcSpec with ApplicationWithSubscriptionsFixtures {

  trait Setup extends ApmConnectorMockModule with ApmConnectorCommandModuleMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val mockApiPlatformDeskproConnector: ApiPlatformDeskproConnector = mock[ApiPlatformDeskproConnector]
    val mockApmConnector: ApmConnector                               = mock[ApmConnector]

    val subscriptionsService = new SubscriptionsService(
      mockApiPlatformDeskproConnector,
      mockApmConnector,
      ApmConnectorCommandModuleMock.aMock,
      FixedClock.clock
    )

  }

  "isSubscribedToApi" should {
    val appWithSubs = standardApp.withSubscriptions(Set(apiIdentifierOne, apiIdentifierTwo)).withFieldValues(Map.empty)

    "return false when the application has no subscriptions to the requested api version" in new Setup {
      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithSubs)))

      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithSubs.id, apiIdentifierFour))

      result shouldBe false
    }

    "return true when the application is subscribed to the requested api version" in new Setup {
      when(mockApmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(successful(Some(appWithSubs)))

      private val result =
        await(subscriptionsService.isSubscribedToApi(appWithSubs.id, apiIdentifierOne))

      result shouldBe true
    }
  }

  "Subscribe to API" should {
    "with no subscription fields definitions" in new Setup {

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccess(standardApp)

      private val result =
        await(subscriptionsService.subscribeToApi(standardApp, apiIdentifierFour, CommonEmailData.altDev))

      result.isRight shouldBe true
      result shouldBe Right(DispatchSuccessResult(standardApp))
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccess(standardApp)

      private val result =
        await(subscriptionsService.unsubscribeFromApi(standardApp, apiIdentifierOne, CommonEmailData.altDev))

      result.isRight shouldBe true
      result shouldBe Right(DispatchSuccessResult(standardApp))
    }
  }
}
