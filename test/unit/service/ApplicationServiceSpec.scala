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

package unit.service

import java.util.UUID

import config.ApplicationConfig
import connectors._
import controllers.{AddApplicationForm, EditApplicationForm}
import domain.APIStatus._
import domain.ApiSubscriptionFields.SubscriptionFieldsWrapper
import domain._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{times, verify, verifyZeroInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import service.AuditAction.{Remove2SVRequested, UserLogoutSurveyCompleted}
import service._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class ApplicationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  trait Setup {
    implicit val hc = HeaderCarrier()

    val mockAppConfig = mock[ApplicationConfig]
    val mockProductionApplicationConnector = mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector = mock[ThirdPartyApplicationSandboxConnector]

    val mockProductionSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsProductionConnector]
    val mockSandboxSubscriptionFieldsConnector = mock[ApiSubscriptionFieldsSandboxConnector]

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

    val mockAuditService: AuditService = mock[AuditService]
    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockSandboxSubscriptionFieldsConnector,
      mockProductionSubscriptionFieldsConnector,
      mockAppConfig)
    val mockSubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector = mock[DeskproConnector]

   
    val service = new ApplicationService(connectorsWrapper, mockSubscriptionFieldsService,  mockDeskproConnector, mockAppConfig, mockDeveloperConnector, mockSandboxApplicationConnector, mockProductionApplicationConnector, mockAuditService)

    def theProductionConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      given(mockProductionApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(Some(application)))
      given(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
    }

    def theSandboxConnectorWillReturnTheApplication(applicationId: String, application: Application) = {
      given(mockProductionApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
      given(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(Some(application)))
    }

    def theSubscriptionFieldsServiceWillReturn(fields: Seq[ApiSubscriptionFields.SubscriptionField]) = {
      given(mockSubscriptionFieldsService.fetchFields(any[Application], anyString(), anyString())(any[HeaderCarrier])).willReturn(Future.successful(fields))
    }
  }

  def version(version: String, status: APIStatus, subscribed: Boolean) = VersionSubscription(APIVersion(version, status), subscribed)

  def api(name: String, context: String, requiresTrust: Option[Boolean], versions: VersionSubscription*) = APISubscription(name, name, context, versions, requiresTrust)

  val productionApplicationId = "Application ID"
  val productionClientId = s"client-id-${UUID.randomUUID().toString}"
  val productionApplication = Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("description"), Set())
  val sandboxApplicationId = "Application ID"
  val sandboxClientId = "Client ID"
  val sandboxApplication = Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, DateTimeUtils.now, Environment.SANDBOX, Some("description"))

  def subStatus(appId: String, clientId: String, name: String, context: String, version: String, status: APIStatus = STABLE, subscribed: Boolean = false, requiresTrust: Boolean = false) =
    APISubscriptionStatus(name, name, context, APIVersion(version, status), subscribed, requiresTrust, Some(SubscriptionFieldsWrapper(appId, clientId, context, version, Seq.empty)))

  "Fetch by teamMember email" should {
    "when strategic sandbox is enabled" should {

      val emailAddress = "user@example.com"
      val app1 = Application("id1", "cl-id1", "zapplication", DateTime.now, DateTime.now, Environment.PRODUCTION)
      val app2 = Application("id2", "cl-id2","application", DateTime.now, DateTime.now, Environment.SANDBOX)
      val app3 = Application("id3", "cl-id3", "4pplication", DateTime.now, DateTime.now, Environment.PRODUCTION)

      val productionApps = Seq(app1, app3)
      val sandboxApps = Seq(app2)

      "sort the returned applications by name" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)

        when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(successful(productionApps))
        when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(successful(sandboxApps))

        val result = await(service.fetchByTeamMemberEmail(emailAddress))
        result shouldBe Seq(app3, app2, app1)
      }

      "tolerate the sandbox connector failing with a 5xx error" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)

        when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(successful(productionApps))
        when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(Future.failed(new Upstream5xxResponse("Expected exception", 504, 504)))

        val result = await(service.fetchByTeamMemberEmail(emailAddress))
        result shouldBe Seq(app3, app1)
      }

      "not tolerate the sandbox connector failing with a 5xx error" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)

        when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(Future.failed(new Upstream5xxResponse("Expected exception", 504, 504)))
        when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
          .thenReturn(successful(sandboxApps))

        intercept[Upstream5xxResponse] {
          await(service.fetchByTeamMemberEmail(emailAddress))
        }
      }
    }
  }

  "Fetch by ID" should {
    "when strategic sandbox is enabled" should {
      "return the application fetched from the production connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(service.fetchByApplicationId(productionApplicationId))
        result shouldBe productionApplication
      }

      "return the application fetched from the sandbox connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
        theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
        val result = await(service.fetchByApplicationId(sandboxApplicationId))
        result shouldBe sandboxApplication
      }
    }

    "when strategic sandbox is not enabled" should {
      "return the application fetched from the production connector when it exists there" in new Setup {
        given(mockAppConfig.strategicSandboxEnabled).willReturn(false)
        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
        val result = await(service.fetchByApplicationId(productionApplicationId))
        result shouldBe productionApplication
      }
    }
  }

  "Fetch api subscriptions" should {

    "identify subscribed apis from available definitions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("1.0", STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true), version("1.0-RC", STABLE, subscribed = false)))

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)
      theSubscriptionFieldsServiceWillReturn(Seq.empty)
      val result = await(service.apisWithSubscriptions(productionApplication))

      result.size shouldBe 4
      result should contain inOrder(
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0-RC", STABLE),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
      )
    }

    "include deprecated apis with subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", DEPRECATED, subscribed = true), version("1.0", STABLE, subscribed = false),
          version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true)))

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)
      theSubscriptionFieldsServiceWillReturn(Seq.empty)
      val result = await(service.apisWithSubscriptions(productionApplication))

      result.size shouldBe 4
      result should contain inOrder(
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "0.1", DEPRECATED, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
        )
    }

    "filter out deprecated apis with no subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", DEPRECATED, subscribed = false), version("1.0", STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true)))

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)
      theSubscriptionFieldsServiceWillReturn(Seq.empty)

      val result = await(service.apisWithSubscriptions(productionApplication))

      result.size shouldBe 3
      result should contain inOrder(
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
        )
    }

    "filter out retired apis with no subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", RETIRED, subscribed = false), version("1.0", STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true)))

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)
      theSubscriptionFieldsServiceWillReturn(Seq.empty)

      val result = await(service.apisWithSubscriptions(productionApplication))

      result.size shouldBe 3
      result should contain inOrder(
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
        )
    }

    "return empty sequence when the connector returns empty sequence" in new Setup {
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(Seq.empty)

      val result = await(service.apisWithSubscriptions(productionApplication))

      result shouldBe empty
    }
  }

  "Subscribe to API" should {
    "subscribe application to an API version" in new Setup {
      val context: String = "api1"
      val version: String = "1.0"

      private val subscription: APIIdentifier = APIIdentifier(context, version)

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.subscribeToApi(productionApplicationId, subscription))
        .willReturn(Future.successful(ApplicationUpdateSuccessful))

      val result = await(service.subscribeToApi(productionApplicationId, context, version)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {
      val context: String = "api1"
      val version: String = "1.0"

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.unsubscribeFromApi(productionApplicationId, context, version))
        .willReturn(Future.successful(ApplicationUpdateSuccessful))
      given(mockProductionSubscriptionFieldsConnector.deleteFieldValues(productionApplicationId, context, version))
        .willReturn(Future.successful(true))

      val result = await(service.unsubscribeFromApi(productionApplicationId, context, version)) shouldBe ApplicationUpdateSuccessful


    }
  }

  "Update" should {
    val applicationId = "applicationId"
    val clientId = "clientId"
    val applicationName = "applicationName"
    val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, None)

    "truncate the description to 250 characters on update request" in new Setup {
      val longDescription = "abcde" * 100
      val editApplicationForm = EditApplicationForm("applicationId", "name", Some(longDescription))

      UpdateApplicationRequest.from(editApplicationForm, application).description.get.length shouldBe 250
    }

    "update application" in new Setup {
      val user = utils.DeveloperSession("email@example.com", "first", "last", loggedInState = LoggedInState.LOGGED_IN)
      val editApplicationForm = EditApplicationForm(applicationId, "name")
      given(mockProductionApplicationConnector.update(mockEq(applicationId),
        any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(Future.successful(ApplicationUpdateSuccessful))

      val updateApplicationRequest = UpdateApplicationRequest.from(editApplicationForm, application)

      val result = await(service.update(updateApplicationRequest))
      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "Create" should {
    val applicationId = "applicationId"
    val developer = utils.DeveloperSession("email@example.com", "first", "last", loggedInState = LoggedInState.LOGGED_IN)
    val developer2 = utils.DeveloperSession("username+alias@example.com", "first", "last", loggedInState = LoggedInState.LOGGED_IN)

    "create the user as an administrator on create request" in new Setup {
      val addApplicationForm = AddApplicationForm("name", Some(Environment.PRODUCTION.toString), Some("description"))

      CreateApplicationRequest.from(developer, addApplicationForm).collaborators shouldBe Seq(Collaborator("email@example.com", Role.ADMINISTRATOR))
    }

    "create the user as a urlencoded string to allow for unusual email addresses" in new Setup {
      val addApplicationForm = AddApplicationForm("name", Some(Environment.PRODUCTION.toString), Some("description"))

      CreateApplicationRequest.from(developer2, addApplicationForm).collaborators shouldBe Seq(Collaborator("username+alias@example.com", Role.ADMINISTRATOR))
    }

    "truncate the description to 250 characters on create request" in new Setup {
      val longDescription = "abcde" * 100
      val addApplicationForm = AddApplicationForm("name", Some(Environment.PRODUCTION.toString), Some(longDescription))

      CreateApplicationRequest.from(developer, addApplicationForm).description.get.length shouldBe 250
    }

    "leave the description untouched if it's 250 characters exactly" in new Setup {
      val longDescription = "a" * 250
      val addApplicationForm = AddApplicationForm("name", Some(Environment.PRODUCTION.toString), Some(longDescription))

      CreateApplicationRequest.from(developer, addApplicationForm).description.get shouldBe longDescription
    }

    "create application through the production connector when the request specifies a production app" in new Setup {
      val request = CreateApplicationRequest.from(developer, AddApplicationForm("name", Some(Environment.PRODUCTION.toString)))
      given(mockProductionApplicationConnector.create(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(Future.successful(ApplicationCreatedResponse(applicationId)))


      val result = await(service.createForUser(request))

      result shouldBe ApplicationCreatedResponse(applicationId)
      verifyZeroInteractions(mockSandboxApplicationConnector)
    }

    "create application through the sandbox connector when the request specifies a sandbox app" in new Setup {
      val request = CreateApplicationRequest.from(developer, AddApplicationForm("name", Some(Environment.SANDBOX.toString)))
      given(mockSandboxApplicationConnector.create(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(Future.successful(ApplicationCreatedResponse(applicationId)))

      val result = await(service.createForUser(request))

      result shouldBe ApplicationCreatedResponse(applicationId)
      verifyZeroInteractions(mockProductionApplicationConnector)
    }
  }

  "addClientSecret" should {
    val applicationId = "applicationId"
    val applicationTokens = ApplicationTokens(
      EnvironmentToken("prodId", Seq(aClientSecret("prodSecret1"), aClientSecret("prodSecret2")), "prodToken"))

    "add a client secret for app in production environment" in new Setup {

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

      given(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(""))).willReturn(applicationTokens)

      val updatedToken = await(service.addClientSecret(productionApplicationId))

      updatedToken shouldBe applicationTokens
    }

    "propagate exceptions from connector" in new Setup {

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(""))).thenReturn(Future.failed(new ClientSecretLimitExceeded))

      intercept[ClientSecretLimitExceeded] {
        await(service.addClientSecret(productionApplicationId))
      }
    }
  }

  "deleteClientSecrets" should {
    val applicationId = "applicationId"
    val secretsToDelete = Seq("secret")

    "delete a client secret" in new Setup {

      theProductionConnectorWillReturnTheApplication(applicationId, productionApplication.copy(id = applicationId))

      given(mockProductionApplicationConnector.deleteClientSecrets(any(), any[DeleteClientSecretsRequest])(any[HeaderCarrier]))
        .willReturn(ApplicationUpdateSuccessful)

      await(service.deleteClientSecrets(applicationId, secretsToDelete)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "requestUplift" should {
    val applicationId = "applicationId"
    val applicationName = "applicationName"

    val user = utils.DeveloperSession("Firstname", "Lastname", "email@example.com", loggedInState = LoggedInState.LOGGED_IN)

    "request uplift" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(TicketCreated)
      given(mockProductionApplicationConnector.requestUplift(applicationId,
        UpliftRequest(applicationName, user.email))).willReturn(ApplicationUpliftSuccessful)
      await(service.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "don't propagate error if failed to create deskpro ticket" in new Setup {
      val testError = new scala.RuntimeException("deskpro error")
      given(mockProductionApplicationConnector.requestUplift(applicationId,
        UpliftRequest(applicationName, user.email))).willReturn(ApplicationUpliftSuccessful)
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(Future.failed(testError))

      await(service.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "propagate ApplicationAlreadyExistsResponse from connector" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(Future.successful(TicketCreated))
      given(mockProductionApplicationConnector.requestUplift(applicationId,
        UpliftRequest(applicationName, user.email))).willReturn(Future.failed(new ApplicationAlreadyExists))
      intercept[ApplicationAlreadyExists] {
        await(service.requestUplift(applicationId, applicationName, user))
      }
      verifyZeroInteractions(mockDeskproConnector)
    }

    "propagate ApplicationNotFound from connector" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(Future.successful(TicketCreated))
      given(mockProductionApplicationConnector.requestUplift(applicationId,
        UpliftRequest(applicationName, user.email))).willReturn(Future.failed(new ApplicationNotFound))
      intercept[ApplicationNotFound] {
        await(service.requestUplift(applicationId, applicationName, user))
      }
      verifyZeroInteractions(mockDeskproConnector)
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"

    "verify an uplift successful" in new Setup {
      given(mockProductionApplicationConnector.verify(verificationCode)).willReturn(ApplicationVerificationSuccessful)
      await(service.verify(verificationCode)) shouldBe ApplicationVerificationSuccessful
    }

    "verify an uplift with failure" in new Setup {
      given(mockProductionApplicationConnector.verify(verificationCode))
        .willReturn(Future.failed(new ApplicationVerificationFailed(verificationCode)))
      intercept[ApplicationVerificationFailed](await(service.verify(verificationCode)))
    }
  }

  "add teamMember" should {
    val email = "email@testuser.com"
    val teamMember = Collaborator(email, Role.ADMINISTRATOR)
    val adminEmail = "admin.email@example.com"
    val adminsToEmail = Set.empty[String]
    val request = AddTeamMemberRequest(adminEmail, teamMember, isRegistered = false, adminsToEmail)

    "add teamMember successfully in production app" in new Setup {
      val response = AddTeamMemberResponse(registeredUser = true)

      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request)).willReturn(response)

      await(service.addTeamMember(productionApplication, adminEmail, teamMember)) shouldBe response
    }

    "propagate TeamMemberAlreadyExists from connector in production app" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId,  request)).willReturn(Future.failed(new TeamMemberAlreadyExists))
      intercept[TeamMemberAlreadyExists] {
        await(service.addTeamMember(productionApplication, adminEmail, teamMember))
      }
    }

    "propagate ApplicationNotFound from connector in production app" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request)).willReturn(Future.failed(new ApplicationAlreadyExists))
      intercept[ApplicationAlreadyExists] {
        await(service.addTeamMember(productionApplication, adminEmail, teamMember))
      }
    }
    "add teamMember successfully in sandbox app" in new Setup {
      val response = AddTeamMemberResponse(registeredUser = true)

      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId, request)).willReturn(response)

      await(service.addTeamMember(sandboxApplication, adminEmail, teamMember)) shouldBe response
    }

    "propagate TeamMemberAlreadyExists from connector in sandbox app" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId,  request)).willReturn(Future.failed(new TeamMemberAlreadyExists))
      intercept[TeamMemberAlreadyExists] {
        await(service.addTeamMember(sandboxApplication, adminEmail, teamMember))
      }
    }

    "propagate ApplicationNotFound from connector in sandbox app" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId, request)).willReturn(Future.failed(new ApplicationAlreadyExists))
      intercept[ApplicationAlreadyExists] {
        await(service.addTeamMember(sandboxApplication, adminEmail, teamMember))
      }
    }
    
    "include correct set of admins to email" in new Setup {

      val verifiedAdmin = Collaborator("verified@example.com", Role.ADMINISTRATOR)
      val unverifiedAdmin = Collaborator("unverified@example.com", Role.ADMINISTRATOR)
      val adderAdmin = Collaborator(adminEmail, Role.ADMINISTRATOR)
      val verifiedDeveloper = Collaborator("developer@example.com", Role.DEVELOPER)
      val nonAdderAdmins = Seq(User("verified@example.com", Some(true)), User("unverified@example.com", Some(false)))
      
      val application = productionApplication.copy(collaborators =
        Set(verifiedAdmin, unverifiedAdmin, adderAdmin, verifiedDeveloper))

      val response = AddTeamMemberResponse(registeredUser = true)

      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(mockEq(Set("verified@example.com", "unverified@example.com")))(any()))
        .willReturn(Future.successful(nonAdderAdmins))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, application)
      given(mockProductionApplicationConnector.addTeamMember(any(), any())(any())).willReturn(response)

      await(service.addTeamMember(application, adderAdmin.emailAddress, teamMember)) shouldBe response
      verify(mockProductionApplicationConnector)
        .addTeamMember(mockEq(productionApplicationId), mockEq(request.copy(adminsToEmail = Set("verified@example.com"))))(any())
    }
  }

  "remove teamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq.empty[String]

    "remove teamMember successfully from production" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail)).willReturn(ApplicationUpdateSuccessful)
      await(service.removeTeamMember(productionApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from production" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail)).willReturn(Future.failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(service.removeTeamMember(productionApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from production" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail)).willReturn(Future.failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(service.removeTeamMember(productionApplication, email, admin)))
    }

    "remove teamMember successfully from sandbox" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail)).willReturn(ApplicationUpdateSuccessful)
      await(service.removeTeamMember(sandboxApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from sandbox" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail)).willReturn(Future.failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(service.removeTeamMember(sandboxApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from sandbox" in new Setup {
      given(mockAppConfig.strategicSandboxEnabled).willReturn(true)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail)).willReturn(Future.failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(service.removeTeamMember(sandboxApplication, email, admin)))
    }

    "include correct set of admins to email" in new Setup {

      val verifiedAdmin = Collaborator("verified@example.com", Role.ADMINISTRATOR)
      val unverifiedAdmin = Collaborator("unverified@example.com", Role.ADMINISTRATOR)
      val removerAdmin = Collaborator("admin.email@example.com", Role.ADMINISTRATOR)
      val verifiedDeveloper = Collaborator("developer@example.com", Role.DEVELOPER)
      val teamMemberToRemove = Collaborator("to.remove@example.com", Role.ADMINISTRATOR)

      val nonRemoverAdmins = Seq(User("verified@example.com", Some(true)), User("unverified@example.com", Some(false)))

      val application = productionApplication.copy(collaborators =
        Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

      val response = ApplicationUpdateSuccessful

      given(mockDeveloperConnector.fetchByEmails(mockEq(Set("verified@example.com", "unverified@example.com")))(any()))
        .willReturn(Future.successful(nonRemoverAdmins))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, application)
      given(mockProductionApplicationConnector.removeTeamMember(any(), any(), any(), any())(any())).willReturn(response)

      await(service.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress)) shouldBe response
      verify(mockProductionApplicationConnector)
        .removeTeamMember(
          mockEq(productionApplicationId),
          mockEq(teamMemberToRemove.emailAddress),
          mockEq(removerAdmin.emailAddress),
          mockEq(Seq(verifiedAdmin.emailAddress)))(any())
    }
  }

  "request application deletion" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(Collaborator(adminEmail, Role.ADMINISTRATOR), Collaborator(developerEmail, Role.DEVELOPER))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val productionApp = productionApplication.copy(collaborators = teamMembers)
    val subject = "Request to delete an application"
    val captor: ArgumentCaptor[DeskproTicket] = ArgumentCaptor.forClass(classOf[DeskproTicket])

    "create a deskpro ticket and audit record for an Admin in a Sandbox app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(mockEq(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(mockEq(hc)))
        .willReturn(Future.successful(Success))

      await(service.requestPrincipalApplicationDeletion(adminRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(mockEq(hc))
    }
    "create a deskpro ticket and audit record for a Developer in a Sandbox app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(mockEq(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(mockEq(hc)))
        .willReturn(Future.successful(Success))

      await(service.requestPrincipalApplicationDeletion(developerRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe developerEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(mockEq(hc))
    }
    "create a deskpro ticket and audit record for an Admin in a Production app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(mockEq(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(mockEq(hc)))
        .willReturn(Future.successful(Success))

      await(service.requestPrincipalApplicationDeletion(adminRequester, productionApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(mockEq(hc))
    }
    "not create a deskpro ticket or audit record for a Developer in a Production app" in new Setup {

      intercept[ForbiddenException] {
        await(service.requestPrincipalApplicationDeletion(developerRequester, productionApp))
      }
      verifyZeroInteractions(mockDeskproConnector)
      verifyZeroInteractions(mockAuditService)
    }
  }

  "delete subordinate application" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(Collaborator(adminEmail, Role.ADMINISTRATOR), Collaborator(developerEmail, Role.DEVELOPER))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val invalidROPCApp = sandboxApplication.copy(collaborators = teamMembers, access = ROPC())
    val productionApp = productionApplication.copy(collaborators = teamMembers)
    val adminDeleteApplicationRequest = DeleteApplicationRequest(adminRequester)

    var expectedMessage = "Only standard subordinate applications can be deleted by admins"

    "delete standard subordinate application when requested by an admin" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(successful()))

      await(service.deleteSubordinateApplication(adminRequester, sandboxApp))

      verify(mockSandboxApplicationConnector).deleteApplication(mockEq(sandboxApplicationId), mockEq(adminDeleteApplicationRequest))(mockEq(hc))
    }

    "throw an exception when a subordinate application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any(), any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      val exception = intercept[ForbiddenException](await(service.deleteSubordinateApplication(developerRequester, sandboxApp)))
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a production application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any(), any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      val exception = intercept[ForbiddenException](await(service.deleteSubordinateApplication(developerRequester, productionApp)))
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a ROPC application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any(), any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      val exception = intercept[ForbiddenException](await(service.deleteSubordinateApplication(developerRequester, invalidROPCApp)))
      exception.getMessage shouldBe expectedMessage
    }

  }

  "request delete developer" should {
    val developerName = "Testy McTester"
    val developerEmail = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(mockEq(hc))).willReturn(Future.successful(Success))

      await(service.requestDeveloperAccountDeletion(developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(mockEq(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(mockEq(hc))
    }
  }

  "isSubscribedToApi" should {
    val subscriptions = Seq(
      APISubscription("First API", "", "first context", Seq(VersionSubscription(APIVersion("1.0", APIStatus.STABLE), subscribed = true), VersionSubscription(APIVersion("2.0", APIStatus.BETA), subscribed = false)), None),
      APISubscription("Second API", "", "second context", Seq(VersionSubscription(APIVersion("1.0", APIStatus.ALPHA), subscribed = true)), None))

    "return false when the application has no subscriptions to the requested api version" in new Setup {
      val apiName = "Third API"
      val apiContext = "third context"
      val apiVersion = "3.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(mockEq(productionApplication.id))(mockEq(hc))).willReturn(Future.successful(subscriptions))
      val result = await(service.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe false
    }

    "return false when the application has unsubscribed to the requested api version" in new Setup {
      val apiName = "First API"
      val apiContext = "first context"
      val apiVersion = "2.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(mockEq(productionApplication.id))(mockEq(hc))).willReturn(Future.successful(subscriptions))
      val result = await(service.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe false
    }

    "return true when the application is subscribed to the requested api version" in new Setup {
      val apiName = "First API"
      val apiContext = "first context"
      val apiVersion = "1.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(mockEq(productionApplication.id))(mockEq(hc))).willReturn(Future.successful(subscriptions))
      val result = await(service.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe true
    }
  }

  "request 2SV removal" should {

    val email = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(mockEq(hc))).willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(mockEq(Remove2SVRequested), any[Map[String, String]])(mockEq(hc))).willReturn(Future.successful(Success))

      await(service.request2SVRemoval(email))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(mockEq(hc))
      verify(mockAuditService, times(1)).audit(mockEq(Remove2SVRequested), any[Map[String, String]])(mockEq(hc))
    }
  }

  "userLogoutSurveyCompleted" should {

    val email = "testy@example.com"
    val name = "John Smith"
    val rating = "5"
    val improvementSuggestions = "Test"

    "audit user logout survey" in new Setup {
      given(mockAuditService.audit(mockEq(UserLogoutSurveyCompleted), any[Map[String, String]])(mockEq(hc))).willReturn(Future.successful(Success))

      await(service.userLogoutSurveyCompleted(email, name, rating, improvementSuggestions))

      verify(mockAuditService, times(1)).audit(mockEq(UserLogoutSurveyCompleted), any[Map[String, String]])(mockEq(hc))
    }
  }

  "validate application name" should {
    "call the application connector validate method in sandbox" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = UUID.randomUUID().toString

      given(mockSandboxApplicationConnector.validateName(any(), any())(any[HeaderCarrier]))
        .willReturn(Valid)

      val result = await (service.isApplicationNameValid(applicationName, Environment.SANDBOX, Some(applicationId)))

      result shouldBe Valid

      verify(mockSandboxApplicationConnector).validateName(mockEq(applicationName), mockEq(Some(applicationId)))(mockEq(hc))
    }

    "call the application connector validate method in production" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = UUID.randomUUID().toString

      given(mockProductionApplicationConnector.validateName(any(), any())(any[HeaderCarrier]))
        .willReturn(Valid)

      val result = await (service.isApplicationNameValid(applicationName, Environment.PRODUCTION, Some(applicationId)))

      result shouldBe Valid

      verify(mockProductionApplicationConnector).validateName(mockEq(applicationName), mockEq(Some(applicationId)))(mockEq(hc))
    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now)
}
