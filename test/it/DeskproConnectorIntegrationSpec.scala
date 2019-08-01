/*
 * Copyright 2019 HM Revenue & Customs
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

package it

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors.{ConnectorMetrics, DeskproConnector, NoopConnectorMetrics}
import domain.{DeskproTicket, Feedback, TicketCreated, TicketId}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.http.metrics.API

class DeskproConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration("Test.microservice.services.hmrc-deskpro.port" -> stubPort)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()

    val connector = app.injector.instanceOf[DeskproConnector]
  }

  "api" should {
    "be deskpro" in new Setup {
      connector.api shouldEqual API("deskpro")
    }
  }

  "DeskproConnector" when {

    "Creating a Deskpro ticket" should {
      val ticket = DeskproTicket.createForUplift("Joe Bloggs", "joe.bloggs@example.com", "Test App", "appId")
      val ticketPath = "/deskpro/ticket"
      val expectedBody = Json.toJson(ticket).toString()

      "create a ticket when DeskPro returns Ok (200)" in new Setup {
        stubFor(post(urlEqualTo(ticketPath)).willReturn(
          aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody( """{"ticket_id":12345}""")))

        await(connector.createTicket(ticket)) shouldBe TicketCreated
        verify(1, postRequestedFor(urlEqualTo(ticketPath)).withRequestBody(equalTo(expectedBody)))
      }
    }

    "Submitting feedback" should {

      val feedback = Feedback(name = "Test", email = "Test@example.com", subject = "subject",
        rating = "5", message = "Test feedback", referrer = "Referrer", javascriptEnabled = "true",
        userAgent = "userAgent", authId = "authId", areaOfTax= "areaOfTax", sessionId = "sessionId",
        service = Some("Test Service"))


      val feedbackPath = "/deskpro/feedback"
      val expectedBody = Json.toJson(feedback).toString()

      "create a ticket when DeskPro returns Ok (200)" in new Setup {
        stubFor(post(urlEqualTo(feedbackPath)).willReturn(
          aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody( """{"ticket_id":12345}""")))

        await(connector.createFeedback(feedback)) shouldBe TicketId(12345)
        verify(1, postRequestedFor(urlEqualTo(feedbackPath)).withRequestBody(equalTo(expectedBody)))
      }

      "throw Upstream5xxResponse for an 500 response" in new Setup {
        stubFor(post(urlEqualTo(feedbackPath)).willReturn(
          aResponse().withStatus(500)))

        intercept[Upstream5xxResponse](await(connector.createFeedback(feedback)))
        verify(1, postRequestedFor(urlEqualTo(feedbackPath)).withRequestBody(equalTo(expectedBody)))
      }

      "throw BadRequestException for an 400 response" in new Setup {
        stubFor(post(urlEqualTo(feedbackPath)).willReturn(
          aResponse().withStatus(400)))

        intercept[BadRequestException](await(connector.createFeedback(feedback)))
        verify(1, postRequestedFor(urlEqualTo(feedbackPath)).withRequestBody(equalTo(expectedBody)))
      }
    }
  }
}
