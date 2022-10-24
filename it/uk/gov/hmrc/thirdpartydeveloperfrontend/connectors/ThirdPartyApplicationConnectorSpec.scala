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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID
import java.util.UUID.randomUUID
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnectorDomain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import play.api.http.Status._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.common.API
import ThirdPartyApplicationConnectorJsonFormatters._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, FixedClock, LocalUserIdTracker, WireMockExtensions}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.Mode
import play.api.libs.json.Json
import play.api.{Application => PlayApplication}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId


class ThirdPartyApplicationConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions
  with CollaboratorTracker with LocalUserIdTracker with FixedClock {

  private val apiKey: String = UUID.randomUUID().toString
  private val clientId = ClientId(UUID.randomUUID().toString)
  private val applicationId = ApplicationId("applicationId")
  
  private val stubConfig = Configuration(
    "microservice.services.third-party-application-production.port" -> stubPort,
    "microservice.services.third-party-application-production.use-proxy" -> false,
    "microservice.services.third-party-application-production.api-key" -> "",

    "microservice.services.third-party-application-sandbox.port" -> stubPort,
    "microservice.services.third-party-application-sandbox.use-proxy" -> true,
    "microservice.services.third-party-application-sandbox.api-key" -> apiKey,

    "proxy.username" -> "test",
    "proxy.password" -> "test",
    "proxy.host" -> "localhost",
    "proxy.port" -> stubPort,
    "proxy.protocol" -> "http",
    "proxy.proxyRequiredForThisEnvironment" -> true,

    "hasSandbox" -> true
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
      ApplicationId("My Id"),
      connector.environment,
      "My Application",
      Some("Description"),
      Standard(List("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
      )
      
    lazy val createApplicationRequest = new CreateApplicationRequest(
      "My Application",
      connector.environment,
      Some("Description"),
      List("admin@example.com".asAdministratorCollaborator),
      Standard(List("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
    )

    def applicationResponse(appId: ApplicationId, clientId: ClientId, appName: String = "My Application") = new Application(
      appId,
      clientId,
      appName,
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      Period.ofDays(547),
      connector.environment,
      Some("Description"),
      Set("john@example.com".asAdministratorCollaborator),
      Standard(List("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy")),
      state = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some("john@example.com"), Some("John Dory"))
    )

    implicit val hc = HeaderCarrier()
  }

  trait Setup extends BaseSetup with ApplicationUpdateFormatters {
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
    val url = s"/application/${applicationId.value}"

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

  "applicationUpdate" should {
    val url = s"/application/${applicationId.value}"
    val updateRequest = ChangeProductionApplicationPrivacyPolicyLocation(UserId.random, LocalDateTime.now, PrivacyPolicyLocation.Url("http://example.com"))
    "successfully update an application using the PATCH endpoint" in new Setup {
      stubFor(
        patch(urlEqualTo(url))
          .withJsonRequestBody(Json.toJsObject(updateRequest) ++ Json.obj("updateType" -> "changeProductionApplicationPrivacyPolicyLocation"))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = await(connector.applicationUpdate(applicationId, updateRequest))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "fetch application by id" should {
    val url = s"/application/${applicationId.value}"
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
        get(urlEqualTo("/third-party-application"+url))
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
    val url = s"/application/${applicationId.value}/credentials"

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
    val context = ApiContext("app1")
    val version = ApiVersion("2.0")
    val apiIdentifier = ApiIdentifier(context,version)
    val url = s"/application/${applicationId.value}/subscription?context=${context.value}&version=${version.value}"

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
    val url = s"/verify-uplift/$verificationCode"

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

  "requestUplift" should {
    val applicationName = "applicationName"
    val email = "john.requestor@example.com"
    val upliftRequest = UpliftRequest(applicationName, email)
    val url = s"/application/${applicationId.value}/request-uplift"

    "return success response in case of a 204 NO CONTENT on backend " in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(upliftRequest)
        .willReturn(
            aResponse()
            .withStatus(NO_CONTENT)
        )
      )
      val result = await(connector.requestUplift(applicationId, upliftRequest))

      result shouldEqual ApplicationUpliftSuccessful
    }

    "return ApplicationAlreadyExistsResponse response in case of a 409 CONFLICT on backend " in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(upliftRequest)
        .willReturn(
            aResponse()
            .withStatus(CONFLICT)
        )
      )
      intercept[ApplicationAlreadyExists] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(upliftRequest)
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )
      intercept[ApplicationNotFound] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }
  }

  "updateApproval" should {
    val updateRequest = CheckInformation(contactDetails = Some(ContactDetails("name", "email", "telephone")))
    val url = s"/application/${applicationId.value}/check-information"

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

  "removeTeamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Set("otheradmin@example.com", "anotheradmin@example.com")
    val url = s"/application/${applicationId.value}/collaborator/delete"

    "return success" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(OK)
        )
      )

      val result = await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return application needs administrator response" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(FORBIDDEN)
        )
      ) 
      intercept[ApplicationNeedsAdmin](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }

    "return application not found response" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      ) 
      intercept[ApplicationNotFound](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }

    "other upstream error response should be rethrown" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      ) 
      intercept[Exception](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }
  }

  "addClientSecret" should {
    def tpaClientSecret(clientSecretId: String, clientSecretValue: Option[String] = None): TPAClientSecret =
      TPAClientSecret(clientSecretId, "secret-name", clientSecretValue, LocalDateTime.now(ZoneOffset.UTC), None)

    val actorEmailAddress = "john.requestor@example.com"
    val userId = idOf(actorEmailAddress)
    val timestamp = LocalDateTime.now(clock)
    val clientSecretRequest = ClientSecretRequest(userId, actorEmailAddress, timestamp)
    val url = s"/application/${applicationId.value}/client-secret"

    "generate the client secret" in new Setup {
      val newClientSecretId = UUID.randomUUID().toString
      val newClientSecretValue = UUID.randomUUID().toString
      val newClientSecret = tpaClientSecret(newClientSecretId, Some(newClientSecretValue))
      val response =
        AddClientSecretResponse(
          ClientId(UUID.randomUUID().toString),
          UUID.randomUUID().toString,
          List(tpaClientSecret("old-secret-1"), tpaClientSecret("old-secret-2"), newClientSecret)
        )

      stubFor(
        patch(urlEqualTo(url))
        .withJsonRequestBody(clientSecretRequest)
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(response)
        )
      )
      val result = await(connector.addClientSecrets(applicationId, clientSecretRequest))

      result._1 shouldEqual newClientSecretId
      result._2 shouldEqual newClientSecretValue
    }

    "throw an ApplicationNotFound exception when the application does not exist" in new Setup {
      stubFor(
        patch(urlEqualTo(url))
        .withJsonRequestBody(clientSecretRequest)
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      ) 
      intercept[ApplicationNotFound] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }

    "throw a ClientSecretLimitExceeded exception when the max number of client secret has been exceeded" in new Setup {
      stubFor(
        patch(urlEqualTo(url))
        .withJsonRequestBody(clientSecretRequest)
        .willReturn(
            aResponse()
            .withStatus(FORBIDDEN)
        )
      ) 
      intercept[ClientSecretLimitExceeded] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }
  }

  "validateName" should {
    val url = s"/application/name/validate"

    "returns a valid response" in new Setup {
      val applicationName = "my valid application name"
      val appId = ApplicationId(randomUUID().toString)
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

  "updateIpAllowlist" should {
    val allowlist = Set("1.1.1.1/24")
    val updateRequest = UpdateIpAllowlistRequest(required = false, allowlist)
    val url = s"/application/${applicationId.value}/ipAllowlist"

    "return success response in case of a 204 on backend " in new Setup {
      stubFor(
        put(urlEqualTo(url))
        .withJsonRequestBody(updateRequest)
        .willReturn(
            aResponse()
            .withStatus(OK)
        )
      )
      val result: ApplicationUpdateSuccessful = await(connector.updateIpAllowlist(applicationId, required = false, allowlist))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(
        put(urlEqualTo(url))
        .withJsonRequestBody(updateRequest)
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )
      intercept[ApplicationNotFound] {
        await(connector.updateIpAllowlist(applicationId, required = false, allowlist))
      }
    }
  }

   "deleteApplication" should {
    val url = s"/application/${applicationId.value}/delete"

    "successfully delete the application" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NO_CONTENT)
        )
      )
      val result = await(connector.deleteApplication(applicationId))
      result shouldEqual (())
    }

    "throw exception response if error on back end" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )
      intercept[Exception] {
        await(connector.deleteApplication(applicationId))
      }.getMessage shouldBe "error deleting application"
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, LocalDateTime.now())
}
