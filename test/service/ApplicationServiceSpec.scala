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

package service

import java.util.UUID
import java.util.UUID.randomUUID

import builder._
import config.ApplicationConfig
import connectors._
import controllers.EditApplicationForm
import domain._
import domain.models.apidefinitions._
import domain.models.apidefinitions.APIStatus._
import domain.models.applications._
import domain.models.connectors.{DeskproTicket, TicketCreated}
import domain.models.developers.{LoggedInState, User}
import domain.models.subscriptions.ApiSubscriptionFields
import domain.models.subscriptions.ApiSubscriptionFields._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import service.AuditAction.{Remove2SVRequested, UserLogoutSurveyCompleted}
import service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.time.DateTimeUtils
import utils.AsyncHmrcSpec
import domain.models.developers.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import domain.models.subscriptions.VersionSubscription
import service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.LocalUserIdTracker

class ApplicationServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]
    val mockSubscriptionsService: SubscriptionsService = mock[SubscriptionsService]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockSandboxSubscriptionFieldsConnector,
      mockProductionSubscriptionFieldsConnector,
      mockPushPullNotificationsConnector,
      mockPushPullNotificationsConnector,
      mockAppConfig
    )

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]
    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val applicationService = new ApplicationService(
      mockApmConnector,
      connectorsWrapper,
      mockSubscriptionFieldsService,
      mockSubscriptionsService,
      mockDeskproConnector,
      mockDeveloperConnector,
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockAuditService
    )

    def theProductionConnectorthenReturnTheApplication(applicationId: ApplicationId, application: Application): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
    }

    def theSandboxConnectorthenReturnTheApplication(applicationId: ApplicationId, application: Application): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
    }

    def theSubscriptionFieldsServiceValuesthenReturn(
        fields: Seq[ApiSubscriptionFields.SubscriptionFieldValue]
    ): Unit = {
      when(mockSubscriptionFieldsService.fetchFieldsValues(*[Application], *, *[ApiIdentifier])(*))
        .thenReturn(successful(fields))
    }

    def theSubscriptionFieldsServiceGetAllDefinitionsthenReturn(allFields: DefinitionsByApiVersion): Unit = {
      when(mockSubscriptionFieldsService.getAllFieldDefinitions(*)(*))
        .thenReturn(successful(allFields))
    }
  }

  def version(version: ApiVersion, status: APIStatus, subscribed: Boolean): VersionSubscription =
    VersionSubscription(ApiVersionDefinition(version, status), subscribed)

  val productionApplicationId = ApplicationId("Application ID")
  val productionClientId = ClientId(s"client-id-${randomUUID().toString}")
  val productionApplication: Application =
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("description"), Set())
  val sandboxApplicationId = ApplicationId("Application ID")
  val sandboxClientId = ClientId("Client ID")
  val sandboxApplication: Application =
    Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.SANDBOX, Some("description"))

  def subStatusWithoutFieldValues(
      appId: ApplicationId,
      clientId: ClientId,
      name: String,
      context: ApiContext,
      version: ApiVersion,
      status: APIStatus = STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false
  ): APISubscriptionStatus =
    APISubscriptionStatus(
      name = name,
      serviceName = name,
      context = context,
      apiVersion = ApiVersionDefinition(version, status),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = emptySubscriptionFieldsWrapper(appId, clientId, context, version)
    )

  def subStatus(
      appId: ApplicationId,
      clientId: ClientId,
      name: String,
      context: String,
      version: ApiVersion,
      status: APIStatus = STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      subscriptionFieldWithValues: List[SubscriptionFieldValue] = List.empty
  ): APISubscriptionStatus = {
    APISubscriptionStatus(
      name = name,
      serviceName = name,
      context = ApiContext(context),
      apiVersion = ApiVersionDefinition(version, status),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = SubscriptionFieldsWrapper(appId, clientId, ApiContext(context), version, subscriptionFieldWithValues)
    )
  }

  "Fetch by teamMember userId" should {
    val userId = UserId.random
    val app1 = Application(ApplicationId("id1"), ClientId("cl-id1"), "zapplication", DateTime.now, DateTime.now, None, Environment.PRODUCTION)
    val app2 = Application(ApplicationId("id2"), ClientId("cl-id2"), "application", DateTime.now, DateTime.now, None, Environment.SANDBOX)
    val app3 = Application(ApplicationId("id3"), ClientId("cl-id3"), "4pplication", DateTime.now, DateTime.now, None, Environment.PRODUCTION)

    val productionApps = Seq(app1, app3)
    val sandboxApps = Seq(app2)

    "sort the returned applications by name" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(successful(productionApps))

      when(mockSandboxApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(successful(sandboxApps))

      private val result = await(applicationService.fetchByTeamMemberUserId(userId))
      result shouldBe Seq(app3, app2, app1)
    }

    "tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(successful(productionApps))
      when(mockSandboxApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(failed(UpstreamErrorResponse("Expected exception", 504, 504)))

      private val result = await(applicationService.fetchByTeamMemberUserId(userId))
      result shouldBe Seq(app3, app1)
    }

    "not tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(failed(UpstreamErrorResponse("Expected exception", 504, 504)))
      when(mockSandboxApplicationConnector.fetchByTeamMemberUserId(userId))
        .thenReturn(successful(sandboxApps))

      intercept[UpstreamErrorResponse] {
        await(applicationService.fetchByTeamMemberUserId(userId))
      }
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {
      private val context = ApiContext("api1")
      private val version = versionOne
      private val apiIdentifier = ApiIdentifier(context,version)

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.unsubscribeFromApi(productionApplicationId, apiIdentifier))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockProductionSubscriptionFieldsConnector.deleteFieldValues(productionClientId, context, version))
        .thenReturn(successful(FieldsDeleteSuccessResult))

      await(applicationService.unsubscribeFromApi(productionApplication, apiIdentifier)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "Update" should {
    val applicationId = ApplicationId("applicationId")
    val clientId = ClientId("clientId")
    val applicationName = "applicationName"
    val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, None)

    "truncate the description to 250 characters on update request" in new Setup {
      private val longDescription = "abcde" * 100
      private val editApplicationForm = EditApplicationForm(applicationId, "name", Some(longDescription))

      UpdateApplicationRequest.from(editApplicationForm, application).description.get.length shouldBe 250
    }

    "update application" in new Setup {
      private val editApplicationForm = EditApplicationForm(applicationId, "name")
      when(mockProductionApplicationConnector.update(eqTo(applicationId), any[UpdateApplicationRequest])(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      private val updateApplicationRequest = UpdateApplicationRequest.from(editApplicationForm, application)

      private val result = await(applicationService.update(updateApplicationRequest))
      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "addClientSecret" should {
    val newClientSecretId = UUID.randomUUID().toString
    val newClientSecret = UUID.randomUUID().toString
    val actorEmailAddress = "john.requestor@example.com"

    "add a client secret for app in production environment" in new Setup {

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actorEmailAddress)))
        .thenReturn(successful((newClientSecretId, newClientSecret)))

      private val updatedToken = await(applicationService.addClientSecret(productionApplication, actorEmailAddress))

      updatedToken._1 shouldBe newClientSecretId
      updatedToken._2 shouldBe newClientSecret
    }

    "propagate exceptions from connector" in new Setup {

      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actorEmailAddress)))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      intercept[ClientSecretLimitExceeded] {
        await(applicationService.addClientSecret(productionApplication, actorEmailAddress))
      }
    }
  }

  "deleteClientSecret" should {
    val applicationId = ApplicationId(UUID.randomUUID().toString())
    val actorEmailAddress = "john.requestor@example.com"
    val secretToDelete = UUID.randomUUID().toString

    "delete a client secret" in new Setup {

      val application = productionApplication.copy(id = applicationId)

      theProductionConnectorthenReturnTheApplication(applicationId, application)

      when(mockProductionApplicationConnector.deleteClientSecret(eqTo(applicationId), eqTo(secretToDelete), eqTo(actorEmailAddress))(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      await(applicationService.deleteClientSecret(application, secretToDelete, actorEmailAddress)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "requestUplift" should {
    val applicationId = ApplicationId("applicationId")
    val applicationName = "applicationName"

    val user =
      utils.DeveloperSession("Firstname", "Lastname", "email@example.com", loggedInState = LoggedInState.LOGGED_IN)

    "request uplift" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc))).thenReturn(successful(TicketCreated))
      when(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .thenReturn(successful(ApplicationUpliftSuccessful))
      await(applicationService.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "don't propagate error if failed to create deskpro ticket" in new Setup {
      val testError = new scala.RuntimeException("deskpro error")
      when(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .thenReturn(successful(ApplicationUpliftSuccessful))
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc))).thenReturn(failed(testError))

      await(applicationService.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "propagate ApplicationAlreadyExistsResponse from connector" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .thenReturn(failed(new ApplicationAlreadyExists))

      intercept[ApplicationAlreadyExists] {
        await(applicationService.requestUplift(applicationId, applicationName, user))
      }

      verifyZeroInteractions(mockDeskproConnector)
    }

    "propagate ApplicationNotFound from connector" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .thenReturn(failed(new ApplicationNotFound))

      intercept[ApplicationNotFound] {
        await(applicationService.requestUplift(applicationId, applicationName, user))
      }

      verifyZeroInteractions(mockDeskproConnector)
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"

    "verify an uplift successful" in new Setup {
      when(mockProductionApplicationConnector.verify(verificationCode)).thenReturn(successful(ApplicationVerificationSuccessful))
      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationSuccessful
    }

    "verify an uplift with failure" in new Setup {
      when(mockProductionApplicationConnector.verify(verificationCode))
        .thenReturn(successful(ApplicationVerificationFailed))

      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationFailed
    }
  }

  "remove teamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq.empty[String]

    "remove teamMember successfully from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      await(applicationService.removeTeamMember(productionApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "remove teamMember successfully from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      await(applicationService.removeTeamMember(sandboxApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "include correct set of admins to email" in new Setup {

      private val verifiedAdmin = Collaborator("verified@example.com", CollaboratorRole.ADMINISTRATOR, UserId.random)
      private val unverifiedAdmin = Collaborator("unverified@example.com", CollaboratorRole.ADMINISTRATOR,UserId.random)
      private val removerAdmin = Collaborator("admin.email@example.com", CollaboratorRole.ADMINISTRATOR, UserId.random)
      private val verifiedDeveloper = Collaborator("developer@example.com", CollaboratorRole.DEVELOPER, UserId.random)
      private val teamMemberToRemove = Collaborator("to.remove@example.com", CollaboratorRole.ADMINISTRATOR, UserId.random)

      val nonRemoverAdmins = Seq(
        User("verified@example.com", Some(true)),
        User("unverified@example.com", Some(false))
      )

      private val application = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

      private val response = ApplicationUpdateSuccessful

      when(mockDeveloperConnector.fetchByEmails(eqTo(Set("verified@example.com", "unverified@example.com")))(*))
        .thenReturn(successful(nonRemoverAdmins))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, application)
      when(mockProductionApplicationConnector.removeTeamMember(*[ApplicationId], *, *, *)(*)).thenReturn(successful(response))

      await(applicationService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress)) shouldBe response
      verify(mockProductionApplicationConnector).removeTeamMember(
        eqTo(productionApplicationId),
        eqTo(teamMemberToRemove.emailAddress),
        eqTo(removerAdmin.emailAddress),
        eqTo(Seq(verifiedAdmin.emailAddress))
      )(*)
    }
  }

  "request application deletion" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(Collaborator(adminEmail, CollaboratorRole.ADMINISTRATOR, UserId.random), Collaborator(developerEmail, CollaboratorRole.DEVELOPER, UserId.random))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val productionApp = productionApplication.copy(collaborators = teamMembers)
    val subject = "Request to delete an application"
    val captor: ArgumentCaptor[DeskproTicket] = ArgumentCaptor.forClass(classOf[DeskproTicket])

    "create a deskpro ticket and audit record for an Admin in a Sandbox app" in new Setup {

      when(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(adminRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "create a deskpro ticket and audit record for a Developer in a Sandbox app" in new Setup {

      when(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(developerRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe developerEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "create a deskpro ticket and audit record for an Admin in a Production app" in new Setup {

      when(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(adminRequester, productionApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "not create a deskpro ticket or audit record for a Developer in a Production app" in new Setup {

      intercept[ForbiddenException] {
        await(applicationService.requestPrincipalApplicationDeletion(developerRequester, productionApp))
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
    val teamMembers = Set(Collaborator(adminEmail, CollaboratorRole.ADMINISTRATOR, UserId.random), Collaborator(developerEmail, CollaboratorRole.DEVELOPER, UserId.random))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val invalidROPCApp = sandboxApplication.copy(collaborators = teamMembers, access = ROPC())
    val productionApp = productionApplication.copy(collaborators = teamMembers)

    val expectedMessage = "Only standard subordinate applications can be deleted by admins"

    "delete standard subordinate application when requested by an admin" in new Setup {

      when(mockSandboxApplicationConnector.deleteApplication(*[ApplicationId])(*))
        .thenReturn(successful(successful(())))

      await(applicationService.deleteSubordinateApplication(adminRequester, sandboxApp))

      verify(mockSandboxApplicationConnector).deleteApplication(eqTo(sandboxApplicationId))(eqTo(hc))
    }

    "throw an exception when a subordinate application is requested to be deleted by a developer" in new Setup {

      when(mockSandboxApplicationConnector.deleteApplication(*[ApplicationId])(*))
        .thenReturn(failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, sandboxApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a production application is requested to be deleted by a developer" in new Setup {

      when(mockSandboxApplicationConnector.deleteApplication(*[ApplicationId])(*))
        .thenReturn(failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, productionApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a ROPC application is requested to be deleted by a developer" in new Setup {

      when(mockSandboxApplicationConnector.deleteApplication(*[ApplicationId])(*))
        .thenReturn(failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, invalidROPCApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

  }

  "request delete developer" should {
    val developerName = "Testy McTester"
    val developerEmail = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestDeveloperAccountDeletion(developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
  }


  "request 2SV removal" should {

    val email = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(eqTo(Remove2SVRequested), any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.request2SVRemoval(email))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(eqTo(Remove2SVRequested), any[Map[String, String]])(eqTo(hc))
    }
  }

  "userLogoutSurveyCompleted" should {

    val email = "testy@example.com"
    val name = "John Smith"
    val rating = "5"
    val improvementSuggestions = "Test"

    "audit user logout survey" in new Setup {
      when(mockAuditService.audit(eqTo(UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.userLogoutSurveyCompleted(email, name, rating, improvementSuggestions))

      verify(mockAuditService, times(1)).audit(eqTo(UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc))
    }
  }

  "validate application name" should {
    "call the application connector validate method in sandbox" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = ApplicationId(randomUUID().toString)

      when(mockSandboxApplicationConnector.validateName(*, *[Option[ApplicationId]])(*))
        .thenReturn(successful(Valid))

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.SANDBOX, Some(applicationId)))

      result shouldBe Valid

      verify(mockSandboxApplicationConnector).validateName(eqTo(applicationName), eqTo(Some(applicationId)))(eqTo(hc))
    }

    "call the application connector validate method in production" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = ApplicationId(randomUUID().toString)

      when(mockProductionApplicationConnector.validateName(*, *[Option[ApplicationId]])(*))
        .thenReturn(successful(Valid))

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.PRODUCTION, Some(applicationId)))

      result shouldBe Valid

      verify(mockProductionApplicationConnector).validateName(eqTo(applicationName), eqTo(Some(applicationId)))(
        eqTo(hc)
      )
    }
  }

}
