/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.connectors

import java.net.URLEncoder
import java.net.URLEncoder.encode

import com.github.tomakehurst.wiremock.client.WireMock._
import config.WSHttp
import connectors.ThirdPartyApplicationConnector
import domain._
import org.joda.time.DateTimeZone
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.http.metrics.{API, NoopMetrics}
import uk.gov.hmrc.time.DateTimeUtils
import domain.DefinitionFormats._
import uk.gov.hmrc.http.HeaderCarrier

class ThirdPartyApplicationConnectorIntegrationTest extends BaseConnectorSpec {

  trait Setup {
    implicit val hc = HeaderCarrier()

    val connector = new ThirdPartyApplicationConnector {
      val http = WSHttp
      val serviceBaseUrl = wireMockUrl
      val metrics = NoopMetrics
      val environment = Environment.PRODUCTION
    }
  }

  val updateApplicationRequest = new UpdateApplicationRequest(
    "My Id",
    Environment.PRODUCTION,
    "My Application",
    Some("Description"),
    Standard(Seq("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
  )

  val updateApplicationRequestJson = Json.toJson(updateApplicationRequest).toString()

  val createApplicationRequest = new CreateApplicationRequest(
    "My Application",
    Environment.PRODUCTION,
    Some("Description"),
    Seq(Collaborator("admin@example.com", Role.ADMINISTRATOR)),
    Standard(Seq("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
  )

  val createApplicationRequestJson = Json.toJson(createApplicationRequest).toString()

  def applicationResponse(appId: String, clientId: String, appName: String = "My Application") = new Application(
    appId,
    clientId,
    appName,
    DateTimeUtils.now,
    Environment.PRODUCTION,
    Some("Description"),
    Set(Collaborator("john@example.com", Role.ADMINISTRATOR)),
    Standard(Seq("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy")),
    state = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some("john@example.com"))
  )

  private def errorResponse(errorCode: String, message: JsValueWrapper): JsObject = {
    Json.obj(
      "code" -> errorCode,
      "message" -> message
    )
  }

  "api" should {
    "be third-party-application" in new Setup {
      connector.api shouldBe API("third-party-application")
    }
  }

  "create application" should {
    "successfully create an application" in new Setup {
      val applicationId = "applicationId"
      stubFor(post(urlEqualTo("/application"))
        .withRequestBody(equalToJson(createApplicationRequestJson))
        .willReturn(aResponse().withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(applicationResponse(applicationId, "appName")).toString())
        ))

      val result = await(connector.create(createApplicationRequest))

      verify(1, postRequestedFor(urlMatching("/application")).withRequestBody(equalToJson(createApplicationRequestJson)))

      result shouldBe ApplicationCreatedResponse(applicationId)
    }
  }

  "update application" should {
    val applicationId = "applicationId"
    "successfully update an application" in new Setup {
      stubFor(post(urlEqualTo(s"/application/$applicationId"))
        .withRequestBody(equalToJson(updateApplicationRequestJson))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.update(applicationId, updateApplicationRequest))

      verify(1, postRequestedFor(urlMatching(s"/application/$applicationId")).withRequestBody(equalToJson(updateApplicationRequestJson)))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "fetch by teamMember email" should {
    val email = "email@email.com"
    val encodedEmail = URLEncoder.encode(email, "UTF-8")
    "return list of applications" in new Setup {
      stubFor(get(urlEqualTo(s"/developer/applications?emailAddress=$encodedEmail&environment=PRODUCTION"))
        .willReturn(aResponse().withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(List(applicationResponse("app id 1", "client id 1", "app 1"), applicationResponse("app id 2", "client id 2","app 2"))).toString())
        ))

      val result = await(connector.fetchByTeamMemberEmail(email))
      verify(1, getRequestedFor(urlPathMatching(s"/developer/applications")).withQueryParam("emailAddress", equalTo(email)))
      result.size shouldBe 2
      result.map(_.name) shouldBe Seq("app 1", "app 2")
    }

    val email2 = "email+alias@example.com"
    val encodedEmail2 = URLEncoder.encode(email2, "UTF-8")
    "return list of applications even for a unusual email address" in new Setup {
      stubFor(get(urlEqualTo(s"/developer/applications?emailAddress=$encodedEmail2&environment=PRODUCTION"))
        .willReturn(aResponse().withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(List(applicationResponse("app id 1", "client id 1", "app 1"), applicationResponse("app id 2", "client id 2", "app 2"))).toString())
        ))

      val result = await(connector.fetchByTeamMemberEmail(email2))
      verify(1, getRequestedFor(urlPathMatching(s"/developer/applications")).withQueryParam("emailAddress", equalTo(email2)))
      result.size shouldBe 2
      result.map(_.name) shouldBe Seq("app 1", "app 2")
    }
  }

  "fetch by id" should {
    "return an application" in new Setup {
      val applicationId = "applicationId"
      stubFor(get(urlEqualTo(s"/application/$applicationId"))
        .willReturn(aResponse().withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(applicationResponse(applicationId, "client-id", "app name")).toString())
        ))

      val result = await(connector.fetchApplicationById(applicationId))

      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId")))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe "app name"
    }

    "return None if the application cannot be found" in new Setup {
      val applicationId = "applicationId"
      stubFor(get(urlEqualTo(s"/application/$applicationId")).willReturn(aResponse().withStatus(404)))

      val result = await(connector.fetchApplicationById(applicationId))

      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId")))

      result shouldBe empty
    }
  }

  "fetch credentials for application" should {
    val applicationId = "applicationId"
    val tokens = ApplicationTokens(EnvironmentToken("pId", Seq(aSecret("pSecret")), "pToken"))

    "return credentials" in new Setup {
      stubFor(get(urlPathEqualTo(s"/application/$applicationId/credentials"))
        .willReturn(aResponse().withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(Json.toJson(tokens).toString())
        ))
      val result = await(connector.fetchCredentials(applicationId))
      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId/credentials")))
      Json.toJson(result) shouldBe Json.toJson(tokens)
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      val applicationId = "applicationId"
      stubFor(get(urlEqualTo(s"/application/$applicationId")).willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound](await(connector.fetchCredentials(applicationId)))

      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId/credentials")))
    }
  }

  private def aSecret(value: String) = ClientSecret(value, value, DateTimeUtils.now)

  "fetch subscriptions for application" should {
    val applicationId = "applicationId"
    val apiSubscription1 = anApiSubscription("api", "1.0", subscribed = true)
    val apiSubscription2 = anApiSubscription("api", "2.0", subscribed = false)
    val subscriptions = Seq(apiSubscription1, apiSubscription2)

    "return the subscriptions when they are successfully retrieved" in new Setup {
      stubFor(get(urlPathEqualTo(s"/application/$applicationId/subscription"))
        .willReturn(
          aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.toJson(subscriptions).toString())
        ))
      val result = await(connector.fetchSubscriptions(applicationId))
      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId/subscription")))
      result shouldBe subscriptions
    }

    "return an empty sequence when an error occurs retrieving the subscriptions" in new Setup {
      val applicationId = "applicationId"
      stubFor(get(urlEqualTo(s"/application/$applicationId/subscription")).willReturn(aResponse().withStatus(500)))

      val result = await(connector.fetchSubscriptions(applicationId))
      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId/subscription")))
      result shouldBe Seq.empty
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      val applicationId = "applicationId"
      stubFor(get(urlEqualTo(s"/application/$applicationId/subscription")).willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound](await(connector.fetchSubscriptions(applicationId)))

      verify(1, getRequestedFor(urlPathMatching(s"/application/$applicationId/subscription")))
    }

  }

  "subscribe to api" should {
    val applicationId = "applicationId"
    val apiIdentifier = APIIdentifier("app1", "2.0")
    val apiIdentifierJson = Json.toJson(apiIdentifier).toString()

    "subscribe application to an api" in new Setup {

      stubFor(post(urlPathEqualTo(s"/application/$applicationId/subscription"))
        .withRequestBody(equalToJson(apiIdentifierJson))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.subscribeToApi(applicationId, apiIdentifier))

      verify(1, postRequestedFor(urlMatching(s"/application/$applicationId/subscription")).withRequestBody(equalToJson(apiIdentifierJson)))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/subscription"))
        .withRequestBody(equalToJson(apiIdentifierJson))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound](await(connector.subscribeToApi(applicationId, apiIdentifier)))

      verify(1, postRequestedFor(urlPathMatching(s"/application/$applicationId/subscription")))
    }
  }

  "unsubscribe from api" should {
    val applicationId = "applicationId"
    val context = "app1"
    val version = "2.0"

    "unsubscribe application from an api" in new Setup {

      stubFor(delete(urlPathEqualTo(s"/application/$applicationId/subscription"))
        .withQueryParam("context", equalTo(context))
        .withQueryParam("version", equalTo(version))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.unsubscribeFromApi(applicationId, context, version))

      verify(1, deleteRequestedFor(urlPathMatching(s"/application/$applicationId/subscription"))
        .withQueryParam("context", equalTo(context))
        .withQueryParam("version", equalTo(version)))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/application/$applicationId/subscription"))
        .withQueryParam("context", equalTo(context))
        .withQueryParam("version", equalTo(version))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound](await(connector.unsubscribeFromApi(applicationId, context, version)))

      verify(1, deleteRequestedFor(urlPathMatching(s"/application/$applicationId/subscription"))
        .withQueryParam("context", equalTo(context))
        .withQueryParam("version", equalTo(version)))
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"
    "return success response in case of a 204 on backend" in new Setup {
      stubFor(post(urlPathEqualTo(s"/verify-uplift/$verificationCode"))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationSuccessful
    }

    "return failure response in case of a 400 on backend" in new Setup {
      stubFor(post(urlPathEqualTo(s"/verify-uplift/$verificationCode"))
        .willReturn(aResponse().withStatus(400)))

      intercept[ApplicationVerificationFailed](await(connector.verify(verificationCode)))
    }
  }

  "requestUplift" should {
    val applicationId = "applicationId"
    val applicationName = "applicationName"
    val email = "john.requestor@example.com"
    val upliftRequest = UpliftRequest(applicationName, email)

    "return success response in case of a 204 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/request-uplift"))
        .withRequestBody(equalToJson(Json.toJson(upliftRequest).toString()))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.requestUplift(applicationId, upliftRequest))
      result shouldEqual ApplicationUpliftSuccessful
    }


    "return ApplicationAlreadyExistsResponse response in case of a 409 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/request-uplift"))
        .withRequestBody(equalToJson(Json.toJson(upliftRequest).toString()))
        .willReturn(aResponse().withStatus(409)))

      intercept[ApplicationAlreadyExists] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/request-uplift"))
        .withRequestBody(equalToJson(Json.toJson(upliftRequest).toString()))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }
  }
  "updateApproval" should {
    val applicationId = "applicationId"
    val updateRequest = CheckInformation(contactDetails = Some(ContactDetails("name", "email", "telephone")))
    "return success response in case of a 204 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/check-information"))
        .withRequestBody(equalToJson(Json.toJson(updateRequest).toString()))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.updateApproval(applicationId, updateRequest))
      result shouldEqual ApplicationUpdateSuccessful
    }


    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/check-information"))
        .withRequestBody(equalToJson(Json.toJson(updateRequest).toString()))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound] {
        await(connector.updateApproval(applicationId, updateRequest))
      }
    }
  }

  "add teamMember" should {
    val applicationId = "applicationId"
    val admin = "john.requestor@example.com"
    val teamMember = "john.teamMember@example.com"
    val role = Role.ADMINISTRATOR
    val adminsToEmail = Set("bobby@example.com", "daisy@example.com")
    val addTeamMemberRequest =
      AddTeamMemberRequest(admin, Collaborator(teamMember, role), isRegistered = true, adminsToEmail)

    "return success" in new Setup {
      val addTeamMemberResponse = AddTeamMemberResponse(true)
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/collaborator"))
        .withRequestBody(equalToJson(Json.toJson(addTeamMemberRequest).toString()))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(addTeamMemberResponse).toString)))

      val result = await(connector.addTeamMember(applicationId, addTeamMemberRequest))

      result shouldEqual addTeamMemberResponse
    }

    "return teamMember already exists response" in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/collaborator"))
        .withRequestBody(equalToJson(Json.toJson(addTeamMemberRequest).toString()))
        .willReturn(aResponse().withStatus(409)))

      intercept[TeamMemberAlreadyExists] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }

    "return application not found response" in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/teamMember"))
        .withRequestBody(equalToJson(Json.toJson(addTeamMemberRequest).toString()))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }
  }

  "remove teamMember" should {
    val applicationId = "applicationId"
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq("otheradmin@example.com", "anotheradmin@example.com")

    "return success" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/application/$applicationId/collaborator/${urlEncode(email)}"))
        .withQueryParam("admin", equalTo(urlEncode(admin)))
        .withQueryParam("adminsToEmail", equalTo(urlEncode("otheradmin@example.com,anotheradmin@example.com")))
        .willReturn(aResponse().withStatus(204)))

      val result = await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return application needs administrator response" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/application/$applicationId/collaborator/${urlEncode(email)}"))
        .withQueryParam("admin", equalTo(urlEncode(admin)))
        .withQueryParam("adminsToEmail", equalTo(urlEncode("otheradmin@example.com,anotheradmin@example.com")))
        .willReturn(aResponse().withStatus(403)))

      intercept[ApplicationNeedsAdmin](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }

    "return application not found response" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/application/$applicationId/teamMember/${urlEncode(email)}"))
        .withQueryParam("admin", equalTo(urlEncode(admin)))
        .withQueryParam("adminsToEmail", equalTo(urlEncode("otheradmin@example.com,anotheradmin@example.com")))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }
  }

  "addClientSecret" should {
    val applicationId = "applicationId"
    val applicationTokens = ApplicationTokens(
      EnvironmentToken("prodId", Seq(aClientSecret("prodSecret1"), aClientSecret("prodSecret2")), "prodToken"))
    val clientSecretRequest = ClientSecretRequest("")

    "generate the client secret" in new Setup {
      val environment = "production"

      stubFor(post(urlPathEqualTo(s"/application/$applicationId/client-secret"))
        .withRequestBody(equalToJson(Json.toJson(clientSecretRequest).toString()))
        .willReturn(aResponse().withStatus(200).withBody(Json.toJson(applicationTokens).toString())))

      val result = await(connector.addClientSecrets(applicationId, clientSecretRequest))

      result shouldEqual applicationTokens
    }

    "throw an ApplicationNotFound exception when the application does not exist" in new Setup {
      val environment = "production"

      stubFor(post(urlPathEqualTo(s"/application/$applicationId/client-secret"))
        .withRequestBody(equalToJson(Json.toJson(clientSecretRequest).toString()))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }

    "throw a ClientSecretLimitExceeded exception when the max number of client secret has been exceeded" in new Setup {
      val environment = "production"

      stubFor(post(urlPathEqualTo(s"/application/$applicationId/client-secret"))
        .withRequestBody(equalToJson(Json.toJson(clientSecretRequest).toString()))
        .willReturn(aResponse().withStatus(403)
          .withBody(errorResponse("CLIENT_SECRET_LIMIT_EXCEEDED", "Client secret limit has been exceeded").toString())
        ))

      intercept[ClientSecretLimitExceeded] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }
  }

  "deleteClientSecrets" should {
    val applicationId = "applicationId"
    val applicationTokens = ApplicationTokens(
      EnvironmentToken("", Seq(aClientSecret("secret1"), aClientSecret("secret2")), ""))
    val deleteClientSecretsRequest = DeleteClientSecretsRequest(Seq("secret1"))

    "delete a client secret" in new Setup {

      stubFor(post(urlPathEqualTo(s"/application/$applicationId/revoke-client-secrets"))
        .withRequestBody(equalToJson(Json.toJson(deleteClientSecretsRequest).toString()))
        .willReturn(aResponse().withStatus(204).withBody(Json.toJson(applicationTokens).toString)))

      val result = await(connector.deleteClientSecrets(applicationId, deleteClientSecretsRequest))

      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      stubFor(post(urlPathEqualTo(s"/application/$applicationId/revoke-client-secrets"))
        .withRequestBody(equalToJson(Json.toJson(deleteClientSecretsRequest).toString()))
        .willReturn(aResponse().withStatus(404)))

      intercept[ApplicationNotFound] {
        await(connector.deleteClientSecrets(applicationId, deleteClientSecretsRequest))
      }
    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def anApiSubscription(context: String, version: String, subscribed: Boolean) = {
    APISubscription(
      "a",
      "b",
      context,
      Seq(VersionSubscription(APIVersion(version, APIStatus.STABLE, None), subscribed)),
      None
    )
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = {
    encode(str, encoding)
  }

}
