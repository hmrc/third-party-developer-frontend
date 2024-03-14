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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SupportEnquiryForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.DeskproTicketCreationFailed
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._

class DeskproHorizonConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

  private val stubConfig = Configuration(
    "deskpro-horizon.uri"   -> s"http://localhost:$stubPort",
    "deskpro-horizon.brand" -> 5
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector = app.injector.instanceOf[DeskproHorizonConnector]
  }

  "api" should {
    "be deskpro horizon" in new Setup {
      connector.api shouldEqual API("deskpro-horizon")
    }
  }

  "DeskproHorizonConnector" when {
    "Creating a Deskpro Horizon ticket" should {
      implicit val fakeRequest = FakeRequest()
      val supportEnquiryForm   = SupportEnquiryForm("Joe Bloggs", "joe.bloggs@example.com", "I need help please")
      val ticket               = DeskproTicket.createFromSupportEnquiry(supportEnquiryForm, "Test App")
      val deskproHorizonTicket = DeskproHorizonTicket.fromDeskproTicket(ticket, 5)
      val ticketPath           = "/api/v2/tickets"
      val expectedBody         = Json.toJson(deskproHorizonTicket).toString()

      "create a ticket when DeskPro returns Created (201)" in new Setup {
        stubFor(
          post(urlEqualTo(ticketPath)).willReturn(
            aResponse()
              .withStatus(CREATED)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"data":{"id":12345,"ref":"SDST-1234"}}""")
          )
        )

        val resp = await(connector.createTicket(ticket))
        resp shouldBe TicketCreated
        verify(1, postRequestedFor(urlEqualTo(ticketPath)).withRequestBody(equalTo(expectedBody)))
      }

      "create a ticket when DeskPro Horizon returns Created (201)" in new Setup {
        stubFor(
          post(urlEqualTo(ticketPath)).willReturn(
            aResponse()
              .withStatus(CREATED)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"data":{"id":12345,"ref":"SDST-1234"}}""")
          )
        )

        val resp = await(connector.createTicket(deskproHorizonTicket))
        resp shouldBe HorizonTicket("SDST-1234")
        verify(1, postRequestedFor(urlEqualTo(ticketPath)).withRequestBody(equalTo(expectedBody)))
      }

      "throw Upstream5xxResponse for an 500 response" in new Setup {
        stubFor(post(urlEqualTo(ticketPath)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        intercept[DeskproTicketCreationFailed](await(connector.createTicket(ticket)))
        verify(1, postRequestedFor(urlEqualTo(ticketPath)).withRequestBody(equalTo(expectedBody)))
      }

      "throw Upstream5xxResponse for an 401 response" in new Setup {
        stubFor(post(urlEqualTo(ticketPath)).willReturn(aResponse().withStatus(UNAUTHORIZED)))

        intercept[DeskproTicketCreationFailed](await(connector.createTicket(ticket)))
        verify(1, postRequestedFor(urlEqualTo(ticketPath)).withRequestBody(equalTo(expectedBody)))
      }
    }
  }
}
