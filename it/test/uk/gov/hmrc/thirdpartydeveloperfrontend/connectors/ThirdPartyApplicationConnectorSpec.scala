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

import java.time.Period
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnectorJsonFormatters._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, LocalUserIdTracker, WireMockExtensions}

class ThirdPartyApplicationConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions
    with CollaboratorTracker with LocalUserIdTracker with FixedClock {

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

    lazy val updateApplicationRequest = new UpdateApplicationRequest(
      ApplicationId.random,
      connector.environment,
      "My Application",
      Some("Description"),
      Access.Standard(List(RedirectUri.unsafeApply("https://example.com/redirect")), Some("http://example.com/terms"), Some("http://example.com/privacy"))
    )

    lazy val createApplicationRequest = new CreateApplicationRequest(
      "My Application",
      connector.environment,
      Some("Description"),
      List("admin@example.com".toLaxEmail.asAdministratorCollaborator),
      Access.Standard(List(RedirectUri.unsafeApply("https://example.com/redirect")), Some("http://example.com/terms"), Some("http://example.com/privacy"))
    )

    def applicationResponse(appId: ApplicationId, clientId: ClientId, appName: String = "My Application") = new Application(
      appId,
      clientId,
      appName,
      instant,
      Some(instant),
      None,
      Period.ofDays(547),
      connector.environment,
      Some("Description"),
      Set("john@example.com".toLaxEmail.asAdministratorCollaborator),
      Access.Standard(List(RedirectUri.unsafeApply("https://example.com/redirect")), Some("http://example.com/terms"), Some("http://example.com/privacy")),
      state = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some("john@example.com"), Some("John Dory"), None, instant)
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  trait Setup extends BaseSetup {
    val connector = app.injector.instanceOf[ThirdPartyApplicationProductionConnector]
  }

  trait ProxiedSetup extends BaseSetup {
    val connector = app.injector.instanceOf[ThirdPartyApplicationSandboxConnector]
  }

  "api" should {
    "be third-party-application" in new Setup {
      connector.api shouldBe API("third-party-application")
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

  "update application" should {
    val url = s"/application/${applicationId}"

    "successfully update an application" in new Setup {

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(updateApplicationRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = await(connector.update(applicationId, updateApplicationRequest))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "fetch application by id" should {
    val url     = s"/application/${applicationId}"
    val appName = "app name"

    "return an application" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(applicationResponse(applicationId, clientId, appName))
          )
      )

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
    }

    "return None if the application cannot be found" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe empty
    }

    "when useProxy is enabled returns an application from proxy" in new ProxiedSetup {
      stubFor(
        get(urlEqualTo("/third-party-application" + url))
          .withHeader(ProxiedHttpClient.API_KEY_HEADER_NAME, equalTo(apiKey))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(applicationResponse(applicationId, clientId, appName))
          )
      )

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
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

  "unsubscribe from api" should {
    val context       = ApiContext("app1")
    val version       = ApiVersionNbr("2.0")
    val apiIdentifier = ApiIdentifier(context, version)
    val url           = s"/application/${applicationId}/subscription?context=${context.value}&version=${version.value}"

    "unsubscribe application from an api" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      val result = await(connector.unsubscribeFromApi(applicationId, apiIdentifier))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[ApplicationNotFound](
        await(connector.unsubscribeFromApi(applicationId, apiIdentifier))
      )
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"
    val url              = s"/verify-uplift/$verificationCode"

    "return success response in case of a 204 NO CONTENT on backend" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationSuccessful
    }

    "return failure response in case of a 400 on backend" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )
      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationFailed
    }
  }

  "updateApproval" should {
    val updateRequest = CheckInformation(contactDetails = Some(ContactDetails(FullName("name"), "email".toLaxEmail, "telephone")))
    val url           = s"/application/${applicationId}/check-information"

    "return success response in case of a 204 on backend " in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(updateRequest)
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )
      val result = await(connector.updateApproval(applicationId, updateRequest))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(updateRequest)
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[ApplicationNotFound] {
        await(connector.updateApproval(applicationId, updateRequest))
      }
    }
  }

  "validateName" should {
    val url = s"/application/name/validate"

    "returns a valid response" in new Setup {
      val applicationName = "my valid application name"
      val appId           = ApplicationId.random
      val expectedRequest = ApplicationNameValidationJson.ApplicationNameValidationRequest(applicationName, Some(appId))

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(expectedRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(ApplicationNameValidationJson.ApplicationNameValidationResult(None))
          )
      )
      val result = await(connector.validateName(applicationName, Some(appId)))
      result shouldBe Valid
    }

    "returns a invalid response" in new Setup {

      val applicationName = "my invalid application name"
      val expectedRequest = ApplicationNameValidationJson.ApplicationNameValidationRequest(applicationName, None)

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(expectedRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(ApplicationNameValidationJson.ApplicationNameValidationResult(Some(ApplicationNameValidationJson.Errors(invalidName = true, duplicateName = false))))
          )
      )
      val result = await(connector.validateName(applicationName, None))
      result shouldBe Invalid(invalidName = true, duplicateName = false)
    }

  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, UUID.randomUUID.toString, FixedClock.instant)
}
