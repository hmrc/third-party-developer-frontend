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

package uk.gov.hmrc.apiplatform.modules.dynamics.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions

import java.util.UUID

class ThirdPartyDeveloperDynamicsConnectorISpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions {

  private val stubConfig = Configuration(
    "microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key" -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val url = "/incidents"

    val tickets = List(
      Ticket("CAS-1", "Title1", Some("Desc1"), 0, "id1"),
      Ticket("CAS-2", "Title2", None, 1, "id2")
    )

    val customerId = UUID.randomUUID().toString
    val title = "The Title"
    val description = "The description"

    val createIncidentRequest = CreateIncidentRequest(s"/accounts($customerId)", title, description)

    val underTest: ThirdPartyDeveloperDynamicsConnector = app.injector.instanceOf[ThirdPartyDeveloperDynamicsConnector]

  }

  "ThirdPartyDeveloperDynamicsConnector" when {

    "getTickets()" should {
      "return a list of tickets" in new Setup {
        stubFor(
          get(urlEqualTo(url))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(tickets).toString)
            )
        )
        await(underTest.getTickets()) mustBe tickets
      }
    }

    "createTicket()" should {
      "return Unit when successful" in new Setup {
        stubFor(
          post(urlEqualTo(url))
            .withJsonRequestBody(Json.toJson(createIncidentRequest))
            .willReturn(
              aResponse()
                .withStatus(CREATED)
            )
        )
        await(underTest.createTicket(customerId, title, description)).isRight mustBe true
      }

      "throw an exception if there is an error in the backend" in new Setup {
        stubFor(
          post(urlEqualTo(url))
            .withJsonRequestBody(Json.toJson(createIncidentRequest))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody("error message")
            )
        )
        await(underTest.createTicket(customerId, title, description)) match {
          case Left(x)  => x mustBe s"POST of 'http://localhost:$stubPort$url' returned $BAD_REQUEST. Response body: 'error message'"
          case Right(_) => fail()
        }
      }
    }
  }
}
