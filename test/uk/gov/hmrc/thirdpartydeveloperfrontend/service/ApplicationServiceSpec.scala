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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor

import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{PrivacyPolicyLocations, TermsAndConditionsLocations}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Environment, UserId, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorCommandModuleMockModule, ApmConnectorMockModule, ThirdPartyOrchestratorConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ApplicationServiceSpec extends AsyncHmrcSpec
    with SubscriptionsBuilder
    with ApplicationWithCollaboratorsFixtures
    with CommonSessionFixtures
    with FixedClock {

  trait Setup extends FixedClock with ApmConnectorMockModule with ApmConnectorCommandModuleMockModule with ThirdPartyOrchestratorConnectorMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockPushPullNotificationsConnector,
      mockPushPullNotificationsConnector,
      mockAppConfig
    )

    val mockSubscriptionFieldsService: SubscriptionFieldsService     = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector                       = mock[DeskproConnector]
    val mockApiPlatformDeskproConnector: ApiPlatformDeskproConnector = mock[ApiPlatformDeskproConnector]
    val mockApmConnector: ApmConnector                               = mock[ApmConnector]

    val applicationService = new ApplicationService(
      mockApmConnector,
      connectorsWrapper,
      ApmConnectorCommandModuleMock.aMock,
      mockSubscriptionFieldsService,
      mockDeskproConnector,
      mockApiPlatformDeskproConnector,
      mockDeveloperConnector,
      ThirdPartyOrchestratorConnectorMock.aMock,
      mockAuditService,
      clock
    )
  }

  val productionApplication: ApplicationWithCollaborators = standardApp

  val sandboxApplicationId = ApplicationId.random

  val sandboxApplication: ApplicationWithCollaborators = standardApp.withId(sandboxApplicationId).inSandbox()

  def subStatusWithoutFieldValues(
      appId: ApplicationId,
      clientId: ClientId,
      name: String,
      context: ApiContext,
      version: ApiVersionNbr,
      status: ApiStatus = ApiStatus.STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false
    ): APISubscriptionStatus =
    APISubscriptionStatus(
      name = name,
      serviceName = ServiceName(name),
      context = context,
      apiVersion = ApiVersion(version, status, ApiAccess.PUBLIC, List.empty),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = emptySubscriptionFieldsWrapper(appId, clientId, context, version)
    )

  def subStatus(
      appId: ApplicationId,
      clientId: ClientId,
      name: String,
      context: String,
      version: ApiVersionNbr,
      status: ApiStatus = ApiStatus.STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      subscriptionFieldWithValues: List[SubscriptionFieldValue] = List.empty
    ): APISubscriptionStatus = {
    APISubscriptionStatus(
      name = name,
      serviceName = ServiceName(name),
      context = ApiContext(context),
      apiVersion = ApiVersion(version, status, ApiAccess.PUBLIC, List.empty),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = SubscriptionFieldsWrapper(appId, clientId, ApiContext(context), version, subscriptionFieldWithValues)
    )
  }

  "Update Privacy Policy Location" should {
    "call the TPA connector correctly" in new Setup {
      val userId      = UserId.random
      val newLocation = PrivacyPolicyLocations.Url("http://example.com")
      val cmd         = ApplicationCommands.ChangeProductionApplicationPrivacyPolicyLocation(userId, instant, newLocation)
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(productionApplication)

      val result = await(applicationService.updatePrivacyPolicyLocation(productionApplication, userId, newLocation))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "Update Terms and Conditions Location" should {
    "call the TPA connector correctly" in new Setup {
      val userId      = UserId.random
      val newLocation = TermsAndConditionsLocations.Url("http://example.com")
      val cmd         = ApplicationCommands.ChangeProductionApplicationTermsAndConditionsLocation(userId, instant, newLocation)
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(productionApplication)

      val result = await(applicationService.updateTermsConditionsLocation(productionApplication, userId, newLocation))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "verifyResponsibleIndividual" should {
    "call the TPA connector correctly" in new Setup {
      val userId        = UserId.random
      val riName        = "ri name"
      val riEmail       = "ri@example.com".toLaxEmail
      val requesterName = "ms admin"
      val cmd           = ApplicationCommands.VerifyResponsibleIndividual(userId, instant, requesterName, riName, riEmail)
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(productionApplication)
      val result        = await(applicationService.verifyResponsibleIndividual(productionApplication, userId, requesterName, riName, riEmail))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "request application deletion" should {

    val adminRequester     = adminSession
    val developerRequester = devSession

    "create a deskpro ticket and audit record for an Admin in a Sandbox app" in new Setup {

      when(mockDeskproConnector.createTicket(*[Option[UserId]], *)(*))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestApplicationDeletion(adminRequester, sandboxApplication)) shouldBe TicketCreated

      verify(mockAuditService).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
      verify(mockDeskproConnector).createTicket(*[Option[UserId]], *)(*)
    }

    "not create a deskpro ticket or audit record for a Developer in a Sandbox app" in new Setup {

      intercept[ForbiddenException] {
        await(applicationService.requestApplicationDeletion(developerRequester, sandboxApplication))
      }
      verifyZeroInteractions(mockDeskproConnector)
      verifyZeroInteractions(mockAuditService)
    }

    "create a deskpro ticket and audit record for an Admin in a Production app" in new Setup {

      when(mockDeskproConnector.createTicket(*[Option[UserId]], *)(*))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestApplicationDeletion(adminRequester, productionApplication)) shouldBe TicketCreated
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
      verify(mockDeskproConnector).createTicket(*[Option[UserId]], *)(*)
    }

    "not create a deskpro ticket or audit record for a Developer in a Production app" in new Setup {

      intercept[ForbiddenException] {
        await(applicationService.requestApplicationDeletion(developerRequester, productionApplication))
      }
      verifyZeroInteractions(mockDeskproConnector)
      verifyZeroInteractions(mockAuditService)
    }
  }

  "delete subordinate application" should {
    val invalidROPCApp  = sandboxApplication.withAccess(Access.Ropc())
    val reasons         = "Subordinate application deleted by DevHub user"
    val expectedMessage = "Only standard subordinate applications can be deleted by admins"

    "delete standard subordinate application when requested by an admin" in new Setup {
      val cmd = ApplicationCommands.DeleteApplicationByCollaborator(adminSession.developer.userId, reasons, instant)
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(productionApplication)

      await(applicationService.deleteSubordinateApplication(adminSession, sandboxApplication))
    }

    "throw an exception when a subordinate application is requested to be deleted by a developer" in new Setup {

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(devSession, sandboxApplication))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a production application is requested to be deleted by a developer" in new Setup {

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(devSession, productionApplication))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a ROPC application is requested to be deleted by a developer" in new Setup {

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(devSession, invalidROPCApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

  }

  "request 2SV removal" should {

    val email  = "testy@example.com".toLaxEmail
    val name   = "Bob"
    val userId = UserId.random

    "correctly create a deskpro ticket and audit record" in new Setup {
      val ticketCaptor = ArgCaptor[DeskproTicket]
      when(mockDeskproConnector.createTicket(any[Option[UserId]], any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(eqTo(AuditAction.Remove2SVRequested), any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.request2SVRemoval(Some(userId), name, email))

      verify(mockDeskproConnector, times(1)).createTicket(eqTo(Some(userId)), ticketCaptor)(eqTo(hc))
      ticketCaptor.value.email shouldBe email
      ticketCaptor.value.name shouldBe name

      verify(mockAuditService, times(1)).audit(eqTo(AuditAction.Remove2SVRequested), any[Map[String, String]])(eqTo(hc))
    }
  }

  "userLogoutSurveyCompleted" should {

    val email                  = "testy@example.com".toLaxEmail
    val name                   = "John Smith"
    val rating                 = "5"
    val improvementSuggestions = "Test"

    "audit user logout survey" in new Setup {
      when(mockAuditService.audit(eqTo(AuditAction.UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.userLogoutSurveyCompleted(email, name, rating, improvementSuggestions))

      verify(mockAuditService, times(1)).audit(eqTo(AuditAction.UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc))
    }
  }

  "validate application name" should {
    "call the application connector validate method in sandbox" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId   = ApplicationId.random

      ThirdPartyOrchestratorConnectorMock.ValidateName.succeedsWith(applicationName, Some(applicationId), Environment.SANDBOX)(ApplicationNameValidationResult.Valid)

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.SANDBOX, Some(applicationId)))

      result shouldBe ApplicationNameValidationResult.Valid

    }

    "call the application connector validate method in production" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId   = ApplicationId.random

      ThirdPartyOrchestratorConnectorMock.ValidateName.succeedsWith(applicationName, Some(applicationId), Environment.PRODUCTION)(ApplicationNameValidationResult.Valid)

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.PRODUCTION, Some(applicationId)))

      result shouldBe ApplicationNameValidationResult.Valid

    }
  }

  "requestProductionApplicationNameChange" should {

    "correctly create a deskpro ticket" in new Setup {
      private val applicationName = ApplicationName("applicationName")

      when(mockApiPlatformDeskproConnector.createTicket(*, *)).thenReturn(successful("ref"))

      private val result =
        await(applicationService.requestProductonApplicationNameChange(
          adminSession.developer.userId,
          productionApplication,
          applicationName,
          adminSession.developer.displayedName,
          adminSession.developer.email
        ))

      result shouldBe "ref"
      verify(mockApiPlatformDeskproConnector).createTicket(*, *)
    }
  }

  "updateResponsibleIndividual" should {
    "call the TPA connector correctly" in new Setup {
      val userId  = UserId.random
      val riName  = "Mr Responsible"
      val riEmail = "ri@example.com".toLaxEmail
      val cmd     = ApplicationCommands.ChangeResponsibleIndividualToSelf(userId, instant, riName, riEmail)
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(productionApplication)

      val result = await(applicationService.updateResponsibleIndividual(productionApplication, userId, riName, riEmail))

      result shouldBe ApplicationUpdateSuccessful
    }
  }

}
