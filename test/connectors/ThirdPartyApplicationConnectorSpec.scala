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

package connectors

import java.net.URLEncoder.encode
import java.util.UUID
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import config.ApplicationConfig
import connectors.ThirdPartyApplicationConnectorDomain._
import domain._
import domain.models.apidefinitions._
import domain.models.applications.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult, Errors}
import domain.models.applications._
import domain.models.connectors.{AddTeamMemberRequest, AddTeamMemberResponse}
import helpers.FutureTimeoutSupportImpl
import org.joda.time.DateTimeZone
import org.mockito.Mockito
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.{Json, JsValue}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import uk.gov.hmrc.time.DateTimeUtils
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class ThirdPartyApplicationConnectorSpec extends AsyncHmrcSpec {

  private val applicationId = ApplicationId("applicationId")
  private val clientId = ClientId("client-id")
  private val baseUrl = "https://example.com"
  private val environmentName = "ENVIRONMENT"

  class Setup(proxyEnabled: Boolean = false) {
    implicit val hc = HeaderCarrier()
    protected val mockHttpClient = mock[HttpClient]
    protected val mockProxiedHttpClient = mock[ProxiedHttpClient]
    protected val mockAppConfig = mock[ApplicationConfig]
    protected val mockEnvironment = mock[Environment]
    protected val mockMetrics = new NoopConnectorMetrics()
    private val futureTimeoutSupport = new FutureTimeoutSupportImpl
    private val actorSystemTest = ActorSystem("test-actor-system")
    val apiKeyTest = "5bb51bca-8f97-4f2b-aee4-81a4a70a42d3"

    val connector = new ThirdPartyApplicationConnector(mockAppConfig, mockMetrics) {
      val ec = global
      val httpClient = mockHttpClient
      val proxiedHttpClient = mockProxiedHttpClient
      val serviceBaseUrl = baseUrl
      val useProxy = proxyEnabled
      val environment = mockEnvironment
      val apiKey = apiKeyTest
      val appConfig = mockAppConfig
      val actorSystem = actorSystemTest
      val futureTimeout = futureTimeoutSupport
      val metrics = mockMetrics
      val isEnabled = true
    }

    when(mockEnvironment.toString).thenReturn(environmentName)
  }

  private val upstream409Response = Upstream4xxResponse("", CONFLICT, CONFLICT)

  private val updateApplicationRequest = new UpdateApplicationRequest(
    ApplicationId("My Id"),
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

  private def applicationResponse(appId: ApplicationId, clientId: ClientId, appName: String = "My Application") = new Application(
    appId,
    clientId,
    appName,
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
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
      val url = baseUrl + "/application"

      when(
        mockHttpClient
          .POST[JsValue, HttpResponse](eqTo(url), eqTo(createApplicationRequestJsValue), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(applicationResponse(applicationId, ClientId("appName")))))))

      val result = await(connector.create(createApplicationRequest))

      result shouldBe ApplicationCreatedResponse(applicationId)
    }
  }

  "update application" should {

    "successfully update an application" in new Setup {

      val url = baseUrl + s"/application/${applicationId.value}"
      when(
        mockHttpClient
          .POST[JsValue, HttpResponse](eqTo(url), eqTo(updateApplicationRequestJsValue), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.update(applicationId, updateApplicationRequest))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "fetch by teamMember email" should {
    val email = "email@email.com"
    val url = baseUrl + "/developer/applications"
    val applicationResponses = List(
      applicationResponse(ApplicationId("app id 1"), ClientId("client id 1"), "app 1"),
      applicationResponse(ApplicationId("app id 2"), ClientId("client id 2"), "app 2")
    )

    val response: Seq[String] = Seq("app 1", "app 2")

    "return list of applications" in new Setup {
      when(
        mockHttpClient
          .GET[Seq[Application]](eqTo(url), eqTo(Seq("emailAddress" -> email, "environment" -> environmentName)))(*, *, *)
      ).thenReturn(Future.successful(applicationResponses))

      val result = await(connector.fetchByTeamMemberEmail(email))

      result.size shouldBe 2
      result.map(_.name) shouldBe response
    }

    "when retry logic is enabled should retry on failure" in new Setup {
      when(mockAppConfig.retryCount).thenReturn(1)
      when(mockHttpClient.GET[Seq[Application]](eqTo(url), eqTo(Seq("emailAddress" -> email, "environment" -> environmentName)))(*, *, *)).thenReturn(
        failed(new BadRequestException("")),
        Future.successful(applicationResponses)
      )

      val result = await(connector.fetchByTeamMemberEmail(email))

      result.size shouldBe 2
      result.map(_.name) shouldBe response
    }
  }

  "fetch application by id" should {

    val fetchUrl = s"/application/${applicationId.value}"
    val url = baseUrl + fetchUrl
    val appName = "app name"

    "return an application" in new Setup {
      when(mockHttpClient.GET[Application](eqTo(url))(*, *, *))
        .thenReturn(Future.successful(applicationResponse(applicationId, clientId, appName)))

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
    }

    "return None if the application cannot be found" in new Setup {

      when(mockHttpClient.GET[Application](eqTo(url))(*, *, *))
        .thenReturn(failed(new NotFoundException("")))

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe empty
    }

    "when retry logic is enabled should retry on failure" in new Setup {
      when(mockAppConfig.retryCount).thenReturn(1)
      when(mockHttpClient.GET[Application](eqTo(url))(*, *, *)).thenReturn(
        failed(new BadRequestException("")),
        Future.successful(applicationResponse(applicationId, clientId, appName))
      )

      val result = await(connector.fetchApplicationById(applicationId))

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
    }

    "when useProxy is enabled returns an application from proxy" in new Setup(proxyEnabled = true) {
      when(mockProxiedHttpClient.GET[Application](eqTo(url))(*, *, *))
        .thenReturn(Future.successful(applicationResponse(applicationId, clientId, appName)))
      when(connector.http).thenReturn(mockProxiedHttpClient)

      val result = await(connector.fetchApplicationById(applicationId))

      verify(mockProxiedHttpClient).withHeaders(apiKeyTest)

      result shouldBe defined
      result.get.id shouldBe applicationId
      result.get.name shouldBe appName
    }
  }

  "fetch credentials for application" should {
    val tokens = ApplicationToken(Seq(aClientSecret()), "pToken")
    val url = baseUrl + s"/application/${applicationId.value}/credentials"

    "return credentials" in new Setup {

      when(mockHttpClient.GET[ApplicationToken](eqTo(url))(*, *, *))
        .thenReturn(Future.successful(tokens))

      val result = await(connector.fetchCredentials(applicationId))

      Json.toJson(result) shouldBe Json.toJson(tokens)
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient.GET[ApplicationToken](eqTo(url))(*, *, *))
        .thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.fetchCredentials(applicationId))
      )
    }

    "when retry logic is enabled should retry on failure" in new Setup {
      when(mockAppConfig.retryCount).thenReturn(1)
      when(mockHttpClient.GET[ApplicationToken](eqTo(url))(*, *, *)).thenReturn(
        failed(new BadRequestException("")),
        Future.successful(tokens)
      )

      val result = await(connector.fetchCredentials(applicationId))

      Json.toJson(result) shouldBe Json.toJson(tokens)
    }
  }

  "subscribe to api" should {
    val apiIdentifier = ApiIdentifier(ApiContext("app1"), ApiVersion("2.0"))
    val url = baseUrl + s"/application/${applicationId.value}/subscription"

    "subscribe application to an api" in new Setup {

      when(
        mockHttpClient
          .POST[ApiIdentifier, HttpResponse](eqTo(url), eqTo(apiIdentifier), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.subscribeToApi(applicationId, apiIdentifier))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(
        mockHttpClient
          .POST[ApiIdentifier, HttpResponse](eqTo(url), eqTo(apiIdentifier), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.subscribeToApi(applicationId, apiIdentifier))
      )
    }
  }

  "unsubscribe from api" should {
    val context = ApiContext("app1")
    val version = ApiVersion("2.0")
    val url = baseUrl + s"/application/${applicationId.value}/subscription?context=${context.value}&version=${version.value}"

    "unsubscribe application from an api" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.unsubscribeFromApi(applicationId, context, version))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound](
        await(connector.unsubscribeFromApi(applicationId, context, version))
      )
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"
    val url = baseUrl + s"/verify-uplift/$verificationCode"

    "return success response in case of a 204 NO CONTENT on backend" in new Setup {

      when(mockHttpClient.POSTEmpty[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationSuccessful
    }

    "return failure response in case of a 400 on backend" in new Setup {

      when(mockHttpClient.POSTEmpty[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(failed(new BadRequestException("")))

      val result = await(connector.verify(verificationCode))

      result shouldEqual ApplicationVerificationFailed
    }
  }

  "requestUplift" should {
    val applicationName = "applicationName"
    val email = "john.requestor@example.com"
    val upliftRequest = UpliftRequest(applicationName, email)
    val url = baseUrl + s"/application/${applicationId.value}/request-uplift"

    "return success response in case of a 204 NO CONTENT on backend " in new Setup {

      when(
        mockHttpClient
          .POST[UpliftRequest, HttpResponse](eqTo(url), eqTo(upliftRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.requestUplift(applicationId, upliftRequest))

      result shouldEqual ApplicationUpliftSuccessful
    }

    "return ApplicationAlreadyExistsResponse response in case of a 409 CONFLICT on backend " in new Setup {

      when(
        mockHttpClient
          .POST[UpliftRequest, HttpResponse](eqTo(url), eqTo(upliftRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(failed(upstream409Response))

      intercept[ApplicationAlreadyExists] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(
        mockHttpClient
          .POST[UpliftRequest, HttpResponse](eqTo(url), eqTo(upliftRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.requestUplift(applicationId, upliftRequest))
      }
    }
  }

  "updateApproval" should {
    val updateRequest = CheckInformation(contactDetails = Some(ContactDetails("name", "email", "telephone")))
    val url = s"$baseUrl/application/${applicationId.value}/check-information"

    "return success response in case of a 204 on backend " in new Setup {
      when(
        mockHttpClient
          .POST[JsValue, HttpResponse](eqTo(url), eqTo(Json.toJson(updateRequest)), *)(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.updateApproval(applicationId, updateRequest))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(
        mockHttpClient
          .POST[JsValue, HttpResponse](eqTo(url), eqTo(Json.toJson(updateRequest)), *)(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.updateApproval(applicationId, updateRequest))
      }
    }
  }

  "addTeamMember" should {
    val applicationId = ApplicationId("applicationId")
    val admin = "john.requestor@example.com"
    val teamMember = "john.teamMember@example.com"
    val role = Role.ADMINISTRATOR
    val adminsToEmail = Set("bobby@example.com", "daisy@example.com")
    val addTeamMemberRequest =
      AddTeamMemberRequest(admin, Collaborator(teamMember, role), isRegistered = true, adminsToEmail)
    val url = s"$baseUrl/application/${applicationId.value}/collaborator"

    "return success" in new Setup {
      val addTeamMemberResponse = AddTeamMemberResponse(true)

      when(
        mockHttpClient
          .POST[AddTeamMemberRequest, HttpResponse](eqTo(url), eqTo(addTeamMemberRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(addTeamMemberResponse)))))

      val result = await(connector.addTeamMember(applicationId, addTeamMemberRequest))

      result shouldEqual addTeamMemberResponse
    }

    "return teamMember already exists response" in new Setup {
      when(
        mockHttpClient
          .POST[AddTeamMemberRequest, HttpResponse](eqTo(url), eqTo(addTeamMemberRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(failed(Upstream4xxResponse("409 exception", CONFLICT, CONFLICT)))

      intercept[TeamMemberAlreadyExists] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }

    "return application not found response" in new Setup {
      when(
        mockHttpClient
          .POST[AddTeamMemberRequest, HttpResponse](eqTo(url), eqTo(addTeamMemberRequest), eqTo(Seq(CONTENT_TYPE -> JSON)))(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.addTeamMember(applicationId, addTeamMemberRequest))
      }
    }
  }

  "removeTeamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq("otheradmin@example.com", "anotheradmin@example.com")
    val queryParams = s"admin=${urlEncode(admin)}&adminsToEmail=${urlEncode(adminsToEmail.mkString(","))}"
    val url = s"$baseUrl/application/${applicationId.value}/collaborator/${urlEncode(email)}?$queryParams"

    "return success" in new Setup {
      when(
        mockHttpClient
          .DELETE[HttpResponse](eqTo(url), *)(*, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail))
      result shouldEqual ApplicationUpdateSuccessful
    }

    "return application needs administrator response" in new Setup {
      when(
        mockHttpClient
          .DELETE[HttpResponse](eqTo(url), *)(*, *, *)
      ).thenReturn(failed(Upstream4xxResponse("403 Forbidden", FORBIDDEN, FORBIDDEN)))

      intercept[ApplicationNeedsAdmin](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }

    "return application not found response" in new Setup {
      when(
        mockHttpClient
          .DELETE[HttpResponse](eqTo(url), *)(*, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound](await(connector.removeTeamMember(applicationId, email, admin, adminsToEmail)))
    }
  }

  "addClientSecret" should {
    def tpaClientSecret(clientSecretId: String, clientSecretValue: Option[String] = None): TPAClientSecret =
      TPAClientSecret(clientSecretId, "secret-name", clientSecretValue, DateTimeUtils.now, None)

    val actorEmailAddress = "john.requestor@example.com"
    val clientSecretRequest = ClientSecretRequest(actorEmailAddress)
    val url = s"$baseUrl/application/${applicationId.value}/client-secret"

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

      when(
        mockHttpClient
          .POST[ClientSecretRequest, AddClientSecretResponse](eqTo(url), eqTo(clientSecretRequest), *)(*, *, *, *)
      ).thenReturn(Future.successful(response))

      val result = await(connector.addClientSecrets(applicationId, clientSecretRequest))

      result._1 shouldEqual newClientSecretId
      result._2 shouldEqual newClientSecretValue
    }

    "throw an ApplicationNotFound exception when the application does not exist" in new Setup {
      when(
        mockHttpClient
          .POST[ClientSecretRequest, ApplicationToken](eqTo(url), eqTo(clientSecretRequest), *)(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }

    "throw a ClientSecretLimitExceeded exception when the max number of client secret has been exceeded" in new Setup {
      when(
        mockHttpClient
          .POST[ClientSecretRequest, ApplicationToken](eqTo(url), eqTo(clientSecretRequest), *)(*, *, *, *)
      ).thenReturn(failed(Upstream4xxResponse("403 Forbidden", FORBIDDEN, FORBIDDEN)))

      intercept[ClientSecretLimitExceeded] {
        await(connector.addClientSecrets(applicationId, clientSecretRequest))
      }
    }
  }

  "deleteClientSecret" should {
    val applicationId = ApplicationId(UUID.randomUUID().toString())
    val clientSecretId = UUID.randomUUID().toString
    val actorEmailAddress = "john.requestor@example.com"
    val expectedDeleteClientSecretRequest = DeleteClientSecretRequest(actorEmailAddress)
    val url = s"$baseUrl/application/${applicationId.value}/client-secret/$clientSecretId"

    "delete a client secret" in new Setup {
      when(
        mockHttpClient
          .POST[DeleteClientSecretRequest, HttpResponse](eqTo(url), eqTo(expectedDeleteClientSecretRequest), *)(*, *, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.deleteClientSecret(applicationId, clientSecretId, actorEmailAddress))

      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(
        mockHttpClient
          .POST[DeleteClientSecretRequest, HttpResponse](eqTo(url), eqTo(expectedDeleteClientSecretRequest), *)(*, *, *, *)
      ).thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.deleteClientSecret(applicationId, clientSecretId, actorEmailAddress))
      }
    }
  }

  "validateName" should {
    val url = s"$baseUrl/application/name/validate"

    "returns a valid response" in new Setup {
      val applicationName = "my valid application name"
      val appId = ApplicationId(randomUUID().toString)

      when(mockHttpClient.POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](*, *, *)(*, *, *, *))
        .thenReturn(Future.successful(ApplicationNameValidationResult(None)))

      val result = await(connector.validateName(applicationName, Some(appId)))

      result shouldBe Valid

      val expectedRequest = ApplicationNameValidationRequest(applicationName, Some(appId))

      verify(mockHttpClient)
        .POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](eqTo(url), eqTo(expectedRequest), *)(*, *, *, *)
    }

    "returns a invalid response" in new Setup {

      val applicationName = "my invalid application name"

      when(mockHttpClient.POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](*, *, *)(*, *, *, *))
        .thenReturn(Future.successful(ApplicationNameValidationResult(Some(Errors(invalidName = true, duplicateName = false)))))

      val result = await(connector.validateName(applicationName, None))

      result shouldBe Invalid(invalidName = true, duplicateName = false)

      val expectedRequest = ApplicationNameValidationRequest(applicationName, None)

      verify(mockHttpClient)
        .POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](eqTo(url), eqTo(expectedRequest), *)(*, *, *, *)
    }

    "when retry logic is enabled should retry on failure" in new Setup {
      val applicationName = "my valid application name"
      val appId = ApplicationId(randomUUID().toString)

      when(mockAppConfig.retryCount).thenReturn(1)

      when(mockHttpClient.POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](*, *, *)(*, *, *, *))
        .thenReturn(
          failed(new BadRequestException("")),
          Future.successful(ApplicationNameValidationResult(None))
        )

      val result = await(connector.validateName(applicationName, Some(appId)))

      result shouldBe Valid

      val expectedRequest = ApplicationNameValidationRequest(applicationName, Some(appId))

      verify(mockHttpClient, Mockito.atLeastOnce)
        .POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](eqTo(url), eqTo(expectedRequest), *)(*, *, *, *)
    }
  }

  "updateIpAllowlist" should {
    val allowlist = Set("1.1.1.1/24")
    val updateRequest = UpdateIpAllowlistRequest(allowlist)
    val url = s"$baseUrl/application/${applicationId.value}/ipWhitelist"

    "return success response in case of a 204 on backend " in new Setup {
      when(mockHttpClient.PUT[UpdateIpAllowlistRequest, HttpResponse](eqTo(url), eqTo(updateRequest), *)(*, *, *, *))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result: ApplicationUpdateSuccessful = await(connector.updateIpAllowlist(applicationId, allowlist))

      result shouldEqual ApplicationUpdateSuccessful
    }

    "return ApplicationNotFound response in case of a 404 on backend " in new Setup {
      when(mockHttpClient.PUT[UpdateIpAllowlistRequest, HttpResponse](eqTo(url), eqTo(updateRequest), *)(*, *, *, *))
        .thenReturn(failed(new NotFoundException("")))

      intercept[ApplicationNotFound] {
        await(connector.updateIpAllowlist(applicationId, allowlist))
      }
    }
  }

  "http" when {
    "configured not to use the proxy" should {
      "use the HttpClient" in new Setup {
        connector.http shouldBe mockHttpClient
      }
    }

    "configured to use the proxy" should {
      "use the ProxiedHttpClient with the correct authorisation" in new Setup(proxyEnabled = true) {
        when(connector.http)
          .thenReturn(mockProxiedHttpClient)

        connector.http shouldBe mockProxiedHttpClient
        verify(mockProxiedHttpClient).withHeaders(apiKeyTest)
      }
    }
  }

  "deleteSubordinateApplication" should {
    val url = s"$baseUrl/application/${applicationId.value}/delete"

    "successfully delete the application" in new Setup {

      when(
        mockHttpClient
          .POSTEmpty[HttpResponse](eqTo(url), *)(*, *, *)
      ).thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      val result = await(connector.deleteApplication(applicationId))

      result shouldEqual (())
    }

    "throw exception response if error on back end" in new Setup {

      when(
        mockHttpClient
          .POSTEmpty[HttpResponse](eqTo(url), *)(*, *, *)
      ).thenReturn(failed(new Exception("error deleting subordinate application")))

      intercept[Exception] {
        await(connector.deleteApplication(applicationId))
      }
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def urlEncode(str: String, encoding: String = "UTF-8") = encode(str, encoding)
}
