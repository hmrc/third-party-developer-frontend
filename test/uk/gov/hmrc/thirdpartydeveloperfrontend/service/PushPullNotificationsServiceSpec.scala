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
import java.util.UUID
import scala.concurrent.Future.{failed, successful}

import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment.PRODUCTION
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

class PushPullNotificationsServiceSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val clientId: ClientId         = ClientId(UUID.randomUUID.toString)
    val grantLength: Period        = Period.ofDays(547)

    val anApplication: Application                                     = Application(
      ApplicationId("appId"),
      clientId,
      "App name 1",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength,
      deployedTo = PRODUCTION
    )
    val pushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]
    val mockConnectorsWrapper: ConnectorsWrapper                       = mock[ConnectorsWrapper]
    when(mockConnectorsWrapper.forEnvironment(*))
      .thenReturn(Connectors(mock[ThirdPartyApplicationConnector], mock[SubscriptionFieldsConnector], pushPullNotificationsConnector))

    val underTest = new PushPullNotificationsService(mockConnectorsWrapper)
  }

  "fetchPushSecrets" should {
    "return push secrets from the conector" in new Setup {
      val expectedPushSecrets = Seq("123", "abc")
      when(pushPullNotificationsConnector.fetchPushSecrets(clientId)).thenReturn(successful(expectedPushSecrets))

      val result: Seq[String] = await(underTest.fetchPushSecrets(anApplication))

      result shouldBe expectedPushSecrets
    }

    "propagate exception from the connector" in new Setup {
      val expectedErrorMessage = "failed"
      when(pushPullNotificationsConnector.fetchPushSecrets(clientId))
        .thenReturn(failed(UpstreamErrorResponse(expectedErrorMessage, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      val exception: UpstreamErrorResponse = intercept[UpstreamErrorResponse](await(underTest.fetchPushSecrets(anApplication)))

      exception.getMessage shouldBe expectedErrorMessage
    }
  }
}
