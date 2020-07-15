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

package connectors

import java.util.UUID

import akka.actor.ActorSystem
import config.ApplicationConfig
import domain.Environment
import org.mockito.ArgumentMatchers.any
import helpers.FutureTimeoutSupportImpl
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionFieldsConnectorProxySpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with WithFakeApplication {
  private val baseUrl = "https://example.com"
  private val environmentName = "ENVIRONMENT"
  private val bearer = "TestBearerToken"

  implicit val hc = HeaderCarrier()
  val clientId: String = UUID.randomUUID().toString
  val apiContext: String = "i-am-a-test"
  val apiVersion: String = "1.0"
  private val futureTimeoutSupport = new FutureTimeoutSupportImpl
  private val testActorSystem = ActorSystem("test-actor-system")

  class Setup(proxyEnabled: Boolean = false) {
    val testApiKey: String = UUID.randomUUID().toString
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockEnvironment: Environment = mock[Environment]
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]

    when(mockEnvironment.toString).thenReturn(environmentName)
    when(mockProxiedHttpClient.withHeaders(any(), any())).thenReturn(mockProxiedHttpClient)

    val connector: AbstractSubscriptionFieldsConnector = new AbstractSubscriptionFieldsConnector {
      val httpClient = mockHttpClient
      val proxiedHttpClient = mockProxiedHttpClient
      val serviceBaseUrl = baseUrl
      val useProxy = proxyEnabled
      val bearerToken = bearer
      val environment = mockEnvironment
      val apiKey = testApiKey
      val actorSystem = testActorSystem
      val futureTimeout = futureTimeoutSupport
      val appConfig = mockAppConfig
      implicit val ec : ExecutionContext = global
    }
  }

  "http" when {
    "configured not to use the proxy" should {
      "use the HttpClient" in new Setup(proxyEnabled = false) {
        connector.http shouldBe mockHttpClient
      }
    }

    "configured to use the proxy" should {
      "use the ProxiedHttpClient with the correct authorisation" in new Setup(proxyEnabled = true) {
        connector.http shouldBe mockProxiedHttpClient

        verify(mockProxiedHttpClient).withHeaders(bearer, testApiKey)
      }
    }
  }
}
