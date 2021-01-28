/*
 * Copyright 2021 HM Revenue & Customs
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

import config.ApplicationConfig
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications.{ClientId, Environment}
import helpers.FutureTimeoutSupportImpl
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionFieldsConnectorProxySpec extends AsyncHmrcSpec with BeforeAndAfterEach with GuiceOneAppPerSuite {
  private val baseUrl = "https://example.com"
  private val environmentName = "ENVIRONMENT"

  implicit val hc = HeaderCarrier()
  val clientId: ClientId = ClientId(UUID.randomUUID().toString)
  val apiContext: ApiContext = ApiContext("i-am-a-test")
  val apiVersion: ApiVersion = ApiVersion("1.0")
  private val futureTimeoutSupport = new FutureTimeoutSupportImpl

  class Setup(proxyEnabled: Boolean = false) {
    val testApiKey: String = UUID.randomUUID().toString
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockEnvironment: Environment = mock[Environment]
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]

    when(mockEnvironment.toString).thenReturn(environmentName)
    when(mockProxiedHttpClient.withHeaders(*)).thenReturn(mockProxiedHttpClient)

    val connector: AbstractSubscriptionFieldsConnector = new AbstractSubscriptionFieldsConnector {
      val httpClient = mockHttpClient
      val proxiedHttpClient = mockProxiedHttpClient
      val serviceBaseUrl = baseUrl
      val useProxy = proxyEnabled
      val environment = mockEnvironment
      val apiKey = testApiKey
      val actorSystem = app.actorSystem
      val futureTimeout = futureTimeoutSupport
      val appConfig = mockAppConfig
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
