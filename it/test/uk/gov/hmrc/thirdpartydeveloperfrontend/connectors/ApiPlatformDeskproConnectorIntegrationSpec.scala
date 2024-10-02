/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApiPlatformDeskproConnector.{UpdateProfileFailed, UpdateProfileSuccess}

class ApiPlatformDeskproConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration("microservice.services.api-platform-deskpro.port" -> stubPort)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector = app.injector.instanceOf[ApiPlatformDeskproConnector]
  }

  "api" should {
    "be api-platform-deskpro" in new Setup {
      connector.api shouldEqual API("api-platform-deskpro")
    }
  }

  "ApiPlatformDeskproConnector" when {

    "Updating person name" should {

      val email   = LaxEmailAddress("user@domain.com")
      val name    = "Bob Fleming"
      val request = ApiPlatformDeskproConnector.UpdatePersonRequest(email, name)

      val expectedBody = Json.toJson(request).toString()

      "Return UpdateProfileSuccess when DeskPro returns Ok (200)" in new Setup {
        stubFor(
          put(urlEqualTo("/person")).willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
          )
        )

        await(connector.updatePersonName(email, name, hc)) shouldBe UpdateProfileSuccess
        verify(1, putRequestedFor(urlEqualTo("/person")).withRequestBody(equalTo(expectedBody)))
      }

      "Return UpdateProfileFailed for an 500 response" in new Setup {
        stubFor(put(urlEqualTo("/person")).willReturn(aResponse().withStatus(500)))

        await(connector.updatePersonName(email, name, hc)) shouldBe UpdateProfileFailed
        verify(1, putRequestedFor(urlEqualTo("/person")).withRequestBody(equalTo(expectedBody)))
      }

      "Return UpdateProfileFailed for an 404 response" in new Setup {
        stubFor(put(urlEqualTo("/person")).willReturn(aResponse().withStatus(404)))

        await(connector.updatePersonName(email, name, hc)) shouldBe UpdateProfileFailed
        verify(1, putRequestedFor(urlEqualTo("/person")).withRequestBody(equalTo(expectedBody)))
      }

      "Return UpdateProfileFailed for an 401 response" in new Setup {
        stubFor(put(urlEqualTo("/person")).willReturn(aResponse().withStatus(401)))

        await(connector.updatePersonName(email, name, hc)) shouldBe UpdateProfileFailed
        verify(1, putRequestedFor(urlEqualTo("/person")).withRequestBody(equalTo(expectedBody)))
      }
    }
  }
}
