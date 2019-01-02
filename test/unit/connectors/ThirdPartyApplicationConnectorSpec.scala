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

package unit.connectors

import config.ApplicationConfig
import connectors.{ProxiedHttpClient, ThirdPartyApplicationConnector}
import domain._
import org.joda.time.DateTimeZone
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.when
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.{API, NoopMetrics}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.URLEncoder.encode

class ThirdPartyApplicationConnectorSpec extends BaseConnectorSpec {

  private val applicationId = "applicationId"
  private val baseUrl = "https://example.com"
  private val environmentName = "ENVIRONMENT"

  trait Setup {
    implicit val hc = HeaderCarrier()
    protected val mockHttpClient = mock[HttpClient]
    protected val mockProxiedHttpClient = mock[ProxiedHttpClient]
    protected val mockAppConfig = mock[ApplicationConfig]
    protected val mockEnvironment = mock[Environment]

    val connector = new ThirdPartyApplicationConnector(mockAppConfig, mockMetrics) {
      val httpClient = mockHttpClient
      val proxiedHttpClient = mockProxiedHttpClient
      val serviceBaseUrl = baseUrl
      val useProxy = false
      val bearerToken = "TestBearerToken"
      val environment = mockEnvironment
    }

    when(mockEnvironment.toString).thenReturn(environmentName)
  }

  private val upstream500Response = Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
  private val upstream409Response = Upstream4xxResponse("", CONFLICT, CONFLICT)

  private val updateApplicationRequest = new UpdateApplicationRequest(
    "My Id",
    Environment.PRODUCTION,
    "My Application",
    Some("Description"),
    Standard(Seq("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
  )

  private val updateApplicationRequestJsValue = Json.toJson(updateApplicationRequest)

  private val createApplicationRequest = new CreateApplicationRequest(
    "My Application",
    Environment.PRODUCTION,
    Some("Description"),
    Seq(Collaborator("admin@example.com", Role.ADMINISTRATOR)),
    Standard(Seq("http://example.com/redirect"), Some("http://example.com/terms"), Some("http://example.com/privacy"))
  )

  private val createApplicationRequestJsValue = Json.toJson(createApplicationRequest)

  private def applicationResponse(appId: String, clientId: String, appName: String = "My Application") = new Application(
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

  "api" should {
    "be third-party-application" in new Setup {
      connector.api shouldBe API("third-party-application")
    }
  }

  "create application" should {

    "successfully create an application" in new Setup {
      val applicationId = "applicationId"
      val url = baseUrl + "/application"

      when(mockHttpClient
        .POST[JsValue, HttpResponse](meq(url), meq(createApplicationRequestJsValue), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(applicationResponse(applicationId, "appName"))))))

      val result = await(connector.create(createApplicationRequest))

      result shouldBe ApplicationCreatedResponse(applicationId)
    }
  }

  "update application" should {

    val applicationId = "applicationId"

    "successfully update an application" in new Setup {

      val url = baseUrl + s"/application/$applicationId"
      when(mockHttpClient
        .POST[JsValue, HttpResponse](meq(url), meq(updateApplicationRequestJsValue), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.update(applicationId, updateApplicationRequest))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "fetch by teamMember email" should {
    val email = "email@email.com"

    "return list of applications" in new Setup {

      val applicationResponses = List(
        applicationResponse("app id 1", "client id 1", "app 1"),
        applicationResponse("app id 2", "client id 2", "app 2")
      )
      val url = baseUrl + "/developer/applications"
      when(mockHttpClient
        .GET[Seq[Application]](meq(url), meq(Seq("emailAddress" -> email, "environment" -> environmentName)))(any(), any(), any()))
        .thenReturn(Future.successful(applicationResponses))

      val result = await(connector.fetchByTeamMemberEmail(email))

      result.size shouldBe 2
      result.map(_.name) shouldBe Seq("app 1", "app 2")
    }
  }

  "fetch application by id" should {

    val fetchUrl = s"/application/$applicationId"
    val url = baseUrl + fetchUrl

    "return an application" in new Setup {

      val appName = "app name"

      when(mockHttpClient.GET[Application](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(applicationResponse(applicationId, "client-id", appName)))

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
    }

    "return None if the application cannot be found" in new Setup {

      when(mockHttpClient.GET[Application](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe empty
    }
  }

  "fetch credentials for application" should {
    val tokens = ApplicationTokens(EnvironmentToken("pId", Seq(aSecret("pSecret")), "pToken"))
    val url = baseUrl + s"/application/$applicationId/credentials"

    "return credentials" in new Setup {

      when(mockHttpClient.GET[ApplicationTokens](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(tokens))

      val result = await(connector.fetchCredentials(applicationId))

      Json.toJson(result) shouldBe Json.toJson(tokens)
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient.GET[ApplicationTokens](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.fetchCredentials(applicationId))
      )
    }
  }

  "fetch subscriptions for application" should {

    val apiSubscription1 = createApiSubscription("api", "1.0", subscribed = true)
    val apiSubscription2 = createApiSubscription("api", "2.0", subscribed = false)
    val subscriptions = Seq(apiSubscription1, apiSubscription2)
    val url = baseUrl + s"/application/$applicationId/subscription"

    "return the subscriptions when they are successfully retrieved" in new Setup {

      when(mockHttpClient.GET[Seq[APISubscription]](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(subscriptions))

      val result = await(connector.fetchSubscriptions(applicationId))

      result shouldBe subscriptions
    }

    "return an empty sequence when an error occurs retrieving the subscriptions" in new Setup {

      when(mockHttpClient.GET[Seq[APISubscription]](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(upstream500Response))

      val result = await(connector.fetchSubscriptions(applicationId))

      result shouldBe Seq.empty
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient.GET[Seq[APISubscription]](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.fetchSubscriptions(applicationId))
      )
    }

  }

  "subscribe to api" should {
    val apiIdentifier = APIIdentifier("app1", "2.0")
    val url = baseUrl + s"/application/$applicationId/subscription"

    "subscribe application to an api" in new Setup {

      when(mockHttpClient
        .POST[APIIdentifier, HttpResponse](meq(url), meq(apiIdentifier), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.subscribeToApi(applicationId, apiIdentifier))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient
        .POST[APIIdentifier, HttpResponse](meq(url), meq(apiIdentifier), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.subscribeToApi(applicationId, apiIdentifier))
      )
    }
  }

  "unsubscribe from api" should {
    val context = "app1"
    val version = "2.0"
    val url = baseUrl + s"/application/$applicationId/subscription?context=$context&version=$version"

    "unsubscribe application from an api" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.unsubscribeFromApi(applicationId, context, version))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.unsubscribeFromApi(applicationId, context, version))
      )
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"
    val url = baseUrl + s"/verify-uplift/$verificationCode"

    "return success response in case of a 204 NO CONTENT on backend" in new Setup {

      when(mockHttpClient.POSTEmpty[HttpResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationSuccessful
    }

    "return failure response in case of a 400 on backend" in new Setup {

      when(mockHttpClient.POSTEmpty[HttpResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new BadRequestException("")))

      intercept[ApplicationVerificationFailed](
        await(connector.verify(verificationCode))
      )
    }
  }

  "requestUplift" should {
    val applicationId = "applicationId"
    val applicationName = "applicationName"
    val email = "john.requestor@example.com"
    val upliftRequest = UpliftRequest(applicationName, email)
    val url = baseUrl + s"/application/$applicationId/request-uplift"

    "return success response in case of a 204 NO CONTENT on backend " in new Setup {

      when(mockHttpClient
        .POST[UpliftRequest, HttpResponse](meq(url), meq(upliftRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.requestUplift(applicationId, upliftRequest))

      result shouldEqual ApplicationUpliftSuccessful
    }

    "return ApplicationAlreadyExistsResponse response in case of a 409 CONFLICT on backend " in new Setup {

      when(mockHttpClient
        .POST[UpliftRequest, HttpResponse](meq(url), meq(upliftRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.failed(upstream409Response))

      intercept[ApplicationAlreadyExists] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(mockHttpClient
        .POST[UpliftRequest, HttpResponse](meq(url), meq(upliftRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }
  }

  "updateApproval" should {
    val applicationId = "applicationId"
    val updateRequest = CheckInformation(contactDetails = Some(ContactDetails("name", "email", "telephone")))
    val url = s"$baseUrl/application/$applicationId/check-information"

    "return success response in case of a 204 on backend " in new Setup {
      when(mockHttpClient
        .POST[JsValue, HttpResponse](meq(url), meq(Json.toJson(updateRequest)), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.updateApproval(applicationId, updateRequest))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(mockHttpClient
        .POST[JsValue, HttpResponse](meq(url), meq(Json.toJson(updateRequest)), any())(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.updateApproval(applicationId, updateRequest))
      }
    }
  }

  "addTeamMember" should {
    val applicationId = "applicationId"
    val admin = "john.requestor@example.com"
    val teamMember = "john.teamMember@example.com"
    val role = Role.ADMINISTRATOR
    val adminsToEmail = Set("bobby@example.com", "daisy@example.com")
    val addTeamMemberRequest =
      AddTeamMemberRequest(admin, Collaborator(teamMember, role), isRegistered = true, adminsToEmail)
    val url = s"$baseUrl/application/$applicationId/collaborator"

    "return success" in new Setup {
      val addTeamMemberResponse = AddTeamMemberResponse(true)

      when(mockHttpClient
        .POST[AddTeamMemberRequest, HttpResponse](meq(url), meq(addTeamMemberRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(addTeamMemberResponse)))))

      val result = await(connector.addTeamMember(applicationId, addTeamMemberRequest))

      result shouldEqual addTeamMemberResponse
    }

    "return teamMember already exists response" in new Setup {
      when(mockHttpClient
        .POST[AddTeamMemberRequest, HttpResponse](meq(url), meq(addTeamMemberRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.failed(Upstream4xxResponse("409 exception", CONFLICT, CONFLICT)))

      intercept[TeamMemberAlreadyExists] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }

    "return application not found response" in new Setup {
      when(mockHttpClient
        .POST[AddTeamMemberRequest, HttpResponse](meq(url), meq(addTeamMemberRequest), meq(Seq(CONTENT_TYPE -> JSON)))(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }
  }

  "removeTeamMember" should {
    val applicationId = "applicationId"
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq("otheradmin@example.com", "anotheradmin@example.com")
    val queryParams = s"admin=${urlEncode(admin)}&adminsToEmail=${urlEncode(adminsToEmail.mkString(","))}"
    val url = s"$baseUrl/application/$applicationId/collaborator/${urlEncode(email)}?$queryParams"

    "return success" in new Setup {
      when(mockHttpClient
        .DELETE[HttpResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return application needs administrator response" in new Setup {
      when(mockHttpClient
        .DELETE[HttpResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(Upstream4xxResponse("403 Forbidden", FORBIDDEN, FORBIDDEN)))

      intercept[ApplicationNeedsAdmin](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }

    "return application not found response" in new Setup {
      when(mockHttpClient
        .DELETE[HttpResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }
  }

  "addClientSecret" should {
    val applicationId = "applicationId"
    val applicationTokens = ApplicationTokens(
      EnvironmentToken("prodId", Seq(aClientSecret("prodSecret1"), aClientSecret("prodSecret2")), "prodToken"))
    val clientSecretRequest = ClientSecretRequest("")
    val url = s"$baseUrl/application/$applicationId/client-secret"
    val environment = "production"

    "generate the client secret" in new Setup {
      when(mockHttpClient
        .POST[ClientSecretRequest, ApplicationTokens](meq(url), meq(clientSecretRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(applicationTokens))

      val result = await(connector.addClientSecrets(applicationId, clientSecretRequest))

      result shouldEqual applicationTokens
    }

    "throw an ApplicationNotFound exception when the application does not exist" in new Setup {
      when(mockHttpClient
        .POST[ClientSecretRequest, ApplicationTokens](meq(url), meq(clientSecretRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }

    "throw a ClientSecretLimitExceeded exception when the max number of client secret has been exceeded" in new Setup {
      when(mockHttpClient
        .POST[ClientSecretRequest, ApplicationTokens](meq(url), meq(clientSecretRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.failed(Upstream4xxResponse("403 Forbidden", FORBIDDEN, FORBIDDEN)))

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
    val url = s"$baseUrl/application/$applicationId/revoke-client-secrets"

    "delete a client secret" in new Setup {
      when(mockHttpClient
        .POST[DeleteClientSecretsRequest, HttpResponse](meq(url), meq(deleteClientSecretsRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.deleteClientSecrets(applicationId, deleteClientSecretsRequest))

      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(mockHttpClient
        .POST[DeleteClientSecretsRequest, HttpResponse](meq(url), meq(deleteClientSecretsRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.deleteClientSecrets(applicationId, deleteClientSecretsRequest))
      }
    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def aSecret(value: String) = ClientSecret(value, value, DateTimeUtils.now)

  private def createApiSubscription(context: String, version: String, subscribed: Boolean) = {
    APISubscription(
      "a",
      "b",
      context,
      Seq(VersionSubscription(APIVersion(version, APIStatus.STABLE, None), subscribed)),
      None
    )
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = encode(str, encoding)
}
