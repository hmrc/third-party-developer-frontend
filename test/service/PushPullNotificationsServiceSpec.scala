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

import java.util.UUID

import connectors.PushPullNotificationsConnector
import domain.models.applications.ClientId
import org.scalatest.Matchers
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class PushPullNotificationsServiceSpec extends AsyncHmrcSpec with Matchers {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val clientId: ClientId = ClientId(UUID.randomUUID.toString)
    val pushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val underTest = new PushPullNotificationsService(pushPullNotificationsConnector)
  }

  "fetchPushSecrets" should {
    "return push secrets from the conector" in new Setup {
      val expectedPushSecrets = Seq("123", "abc")
      when(pushPullNotificationsConnector.fetchPushSecrets(clientId)).thenReturn(successful(expectedPushSecrets))

      val result: Seq[String] = await(underTest.fetchPushSecrets(clientId))

      result shouldBe expectedPushSecrets
    }

    "propagate exception from the connector" in new Setup {
      val expectedErrorMessage = "failed"
      when(pushPullNotificationsConnector.fetchPushSecrets(clientId))
        .thenReturn(failed(Upstream5xxResponse(expectedErrorMessage, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))

      val exception: Upstream5xxResponse = intercept[Upstream5xxResponse](await(underTest.fetchPushSecrets(clientId)))

      exception.getMessage shouldBe expectedErrorMessage
    }
  }
}
