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
import domain.models.developers.{LoggedInState}
import domain.models.subscriptions.ApiSubscriptionFields
import domain.models.subscriptions.ApiSubscriptionFields._
import org.mockito.ArgumentCaptor
import service.AuditAction.{Remove2SVRequested, UserLogoutSurveyCompleted}
import service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.time.DateTimeUtils
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import domain.models.subscriptions.VersionSubscription
import service.PushPullNotificationsService.PushPullNotificationsConnector
import utils.LocalUserIdTracker

class ApplicationServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")
  val grantLength = 547

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
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, grantLength, Environment.PRODUCTION, Some("description"), Set())
  val sandboxApplicationId = ApplicationId("Application ID")
  val sandboxClientId = ClientId("Client ID")
  val sandboxApplication: Application =
    Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, grantLength, Environment.SANDBOX, Some("description"))

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
    val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, None, grantLength, Environment.PRODUCTION, None)

    "truncate the description to 250 characters on update request" in new Setup {
      private val longDescription = "abcde" * 100
      private val editApplicationForm = EditApplicationForm(applicationId, "name", Some(longDescription), grantLength = "12 months")

      UpdateApplicationRequest.from(editApplicationForm, application).description.get.length shouldBe 250
    }

    "update application" in new Setup {
      private val editApplicationForm = EditApplicationForm(applicationId, "name", grantLength = "12 months")
      when(mockProductionApplicationConnector.update(eqTo(applicationId), any[UpdateApplicationRequest])(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      private val updateApplicationRequest = UpdateApplicationRequest.from(editApplicationForm, application)

      private val result = await(applicationService.update(updateApplicationRequest))
      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "request application deletion" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(adminEmail.asAdministratorCollaborator, developerEmail.asDeveloperCollaborator)
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
    val teamMembers = Set(adminEmail.asAdministratorCollaborator, developerEmail.asDeveloperCollaborator)
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
