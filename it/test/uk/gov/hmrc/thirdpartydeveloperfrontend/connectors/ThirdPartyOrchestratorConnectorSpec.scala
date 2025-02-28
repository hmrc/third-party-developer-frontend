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

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.{CreateApplicationRequestV1, CreationAccess}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationCreatedResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WireMockExtensions}

class ThirdPartyOrchestratorConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions
    with CollaboratorTracker
    with LocalUserIdTracker
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  private val applicationId = ApplicationId.random

  private val stubConfig = Configuration(
    "microservice.services.third-party-orchestrator.port" -> stubPort
  )

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    val connector = app.injector.instanceOf[ThirdPartyOrchestratorConnector]

    lazy val createApplicationRequest = new CreateApplicationRequestV1(
      ApplicationName("My Application"),
      CreationAccess.Standard,
      Some("Description"),
      Environment.SANDBOX,
      Set("admin@example.com".toLaxEmail.asAdministratorCollaborator),
      None
    )

    def applicationResponse(appId: ApplicationId, clientId: ClientId, appName: ApplicationName = ApplicationName("My Application")) =
      standardApp.withId(appId).modify(_.copy(clientId = clientId, name = appName))

    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  "api" should {
    "be third-party-orchestrator" in new Setup {
      connector.api shouldBe API("third-party-orchestrator")
    }
  }

  "create application" should {
    val url = "/application"

    "successfully create an application" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(createApplicationRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(applicationResponse(applicationId, ClientId("appName")))
          )
      )

      val result = await(connector.create(createApplicationRequest))

      result shouldBe ApplicationCreatedResponse(applicationId)
    }
  }
}
