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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WireMockExtensions}

class ThirdPartyApplicationConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions
    with CollaboratorTracker
    with LocalUserIdTracker
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  private val apiKey: String = UUID.randomUUID().toString
  private val clientId       = ClientId(UUID.randomUUID().toString)
  private val applicationId  = ApplicationId.random

  private val stubConfig = Configuration(
    "microservice.services.third-party-application-production.port"      -> stubPort,
    "microservice.services.third-party-application-production.use-proxy" -> false,
    "microservice.services.third-party-application-production.api-key"   -> "",
    "microservice.services.third-party-application-sandbox.port"         -> stubPort,
    "microservice.services.third-party-application-sandbox.use-proxy"    -> true,
    "microservice.services.third-party-application-sandbox.api-key"      -> apiKey,
    "proxy.username"                                                     -> "test",
    "proxy.password"                                                     -> "test",
    "proxy.host"                                                         -> "localhost",
    "proxy.port"                                                         -> stubPort,
    "proxy.protocol"                                                     -> "http",
    "proxy.proxyRequiredForThisEnvironment"                              -> true,
    "hasSandbox"                                                         -> true
  )

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait BaseSetup {
    def connector: ThirdPartyApplicationConnector

    def applicationResponse(appId: ApplicationId, clientId: ClientId, appName: ApplicationName = ApplicationName("My Application")) =
      standardApp.withId(appId).modify(_.copy(clientId = clientId, name = appName))

    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  trait Setup extends BaseSetup {
    val connector = app.injector.instanceOf[ThirdPartyApplicationProductionConnector]
  }

  trait SandboxSetup extends BaseSetup {
    val connector = app.injector.instanceOf[ThirdPartyApplicationSandboxConnector]
  }

  "api" should {
    "be third-party-application" in new Setup {
      connector.api shouldBe API("third-party-application")
    }
  }

  "fetch credentials for application" should {
    val tokens = ApplicationToken(List(aClientSecret()), "pToken")
    val url    = s"/application/${applicationId}/credentials"

    "return credentials" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(tokens)
          )
      )
      val result = await(connector.fetchCredentials(applicationId))

      result shouldBe tokens
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[ApplicationNotFound](
        await(connector.fetchCredentials(applicationId))
      )
    }
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, UUID.randomUUID.toString, FixedClock.instant)
}
