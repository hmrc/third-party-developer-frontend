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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import uk.gov.hmrc.http.{HttpClient, _}

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.FutureTimeoutSupportImpl
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class PushPullNotificationsConnectorProxySpec extends AsyncHmrcSpec with BeforeAndAfterEach with GuiceOneAppPerSuite {
  private val baseUrl         = "https://example.com"
  private val environmentName = "ENVIRONMENT"

  implicit val hc: HeaderCarrier                  = HeaderCarrier()
  val clientId: ClientId           = ClientId(UUID.randomUUID().toString)
  val apiContext: ApiContext       = ApiContext("i-am-a-test")
  val apiVersion: ApiVersionNbr    = ApiVersionNbr("1.0")
  private val futureTimeoutSupport = new FutureTimeoutSupportImpl

  class Setup(proxyEnabled: Boolean = false) {
    val testApiKey: String                       = UUID.randomUUID().toString
    val mockHttpClient: HttpClient               = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockEnvironment: Environment             = mock[Environment]
    val mockAppConfig: ApplicationConfig         = mock[ApplicationConfig]

    when(mockEnvironment.toString).thenReturn(environmentName)
    when(mockProxiedHttpClient.withHeaders(*)).thenReturn(mockProxiedHttpClient)

    val connector: AbstractPushPullNotificationsConnector = new AbstractPushPullNotificationsConnector {
      val httpClient                    = mockHttpClient
      val proxiedHttpClient             = mockProxiedHttpClient
      val serviceBaseUrl                = baseUrl
      val useProxy                      = proxyEnabled
      val environment                   = mockEnvironment
      val apiKey                        = testApiKey
      val actorSystem                   = app.actorSystem
      val futureTimeout                 = futureTimeoutSupport
      val appConfig                     = mockAppConfig
      val authorizationKey              = "random auth key"
      implicit val ec: ExecutionContext = global
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

        verify(mockProxiedHttpClient).withHeaders(testApiKey)
      }
    }
  }
}
