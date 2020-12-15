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

import java.util.UUID.randomUUID

import com.github.tomakehurst.wiremock.client.WireMock._
import domain.models.applications.ClientId
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.API

class PushPullNotificationsConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val authorizationKey = randomUUID.toString
  private val stubConfig = Configuration(
    "Test.microservice.services.push-pull-notifications-api-production.port" -> stubPort,
    "Test.microservice.services.push-pull-notifications-api-production.authorizationKey" -> authorizationKey
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector: ProductionPushPullNotificationsConnector = app.injector.instanceOf[ProductionPushPullNotificationsConnector]
  }

  "api" should {
    "be push-pull-notifications-api" in new Setup {
      connector.api shouldEqual API("push-pull-notifications-api")
    }
  }

  "PushPullNotificationsConnector" when {
    val clientId = ClientId(randomUUID.toString)

    "Fetching push secrets" should {
      val fetchPushSecretsPath = s"/client/${clientId.value}/secrets"

      "return a sequence of secrets" in new Setup {
        stubFor(
          get(urlEqualTo(fetchPushSecretsPath)).willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody("""[{"value":"someRandomSecret"}]""")
          )
        )

        val result: Seq[String] = await(connector.fetchPushSecrets(clientId))

        result should contain only "someRandomSecret"
      }

      "include an authorization key in the request" in new Setup {
        stubFor(
          get(urlEqualTo(fetchPushSecretsPath)).willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody("""[{"value":"someRandomSecret"}]""")
          )
        )

        await(connector.fetchPushSecrets(clientId))

        verify(getRequestedFor(urlEqualTo(fetchPushSecretsPath)).withHeader(AUTHORIZATION, equalTo(authorizationKey)));
      }

      "return an empty sequence for a 404 response" in new Setup {
        stubFor(get(urlEqualTo(fetchPushSecretsPath)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result: Seq[String] = await(connector.fetchPushSecrets(clientId))

        result shouldBe empty
      }

      "throw Upstream5xxResponse for a 500 response" in new Setup {
        stubFor(get(urlEqualTo(fetchPushSecretsPath)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        intercept[UpstreamErrorResponse](await(connector.fetchPushSecrets(clientId)))
      }
    }
  }
}
