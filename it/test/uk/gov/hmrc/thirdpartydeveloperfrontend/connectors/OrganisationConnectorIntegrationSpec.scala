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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{OrganisationId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}

class OrganisationConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration("microservice.services.api-platform-organisation.port" -> stubPort)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup extends FixedClock {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val connector                  = app.injector.instanceOf[OrganisationConnector]

    val userId       = UserId.random
    val orgId        = OrganisationId.random
    val organisation = Organisation(orgId, OrganisationName("Org name"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(userId)))
  }

  "api" should {
    "be organisation" in new Setup {
      connector.api shouldEqual API("organisation")
    }
  }

  "fetchOrganisationByUserId" should {
    "successfully get one" in new Setup {
      stubFor(
        get(urlEqualTo(s"/organisation/user/$userId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(List(organisation)).toString())
          )
      )

      val result = await(connector.fetchOrganisationsByUserId(userId))

      result shouldBe List(organisation)
    }

    "return empty list when none found" in new Setup {
      stubFor(
        get(urlEqualTo(s"/organisation/user/$userId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(List.empty[Organisation]).toString())
          )
      )

      val result = await(connector.fetchOrganisationsByUserId(userId))

      result shouldBe List.empty
    }

    "fail when the call returns an error" in new Setup {
      stubFor(
        get(urlEqualTo(s"/organisation/user/$userId"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      val result = await(connector.fetchOrganisationsByUserId(userId))

      result shouldBe List.empty
    }
  }
}
