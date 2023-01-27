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

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIStatus._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{User, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.VersionSubscription
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, FixedClock, LocalUserIdTracker}

class ApplicationServiceTeamMembersSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")

  trait Setup extends FixedClock {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mock[SubscriptionFieldsConnector],
      mock[SubscriptionFieldsConnector],
      mock[PushPullNotificationsConnector],
      mock[PushPullNotificationsConnector],
      mock[ApplicationConfig]
    )

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector                   = mock[DeskproConnector]

    val applicationService = new ApplicationService(
      mock[ApmConnector],
      connectorsWrapper,
      mockSubscriptionFieldsService,
      mock[SubscriptionsService],
      mockDeskproConnector,
      mockDeveloperConnector,
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockAuditService,
      clock
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
  }

  def version(version: ApiVersion, status: APIStatus, subscribed: Boolean): VersionSubscription =
    VersionSubscription(ApiVersionDefinition(version, status), subscribed)

  val productionApplicationId = ApplicationId("Application ID")
  val productionClientId      = ClientId(s"client-id-${randomUUID().toString}")

  val productionApplication: Application =
    Application(
      productionApplicationId,
      productionClientId,
      "name",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength = Period.ofDays(547),
      Environment.PRODUCTION,
      Some("description"),
      Set()
    )
  val sandboxApplicationId               = ApplicationId("Application ID")
  val sandboxClientId                    = ClientId("Client ID")

  val sandboxApplication: Application =
    Application(
      sandboxApplicationId,
      sandboxClientId,
      "name",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength = Period.ofDays(547),
      Environment.SANDBOX,
      Some("description")
    )

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

  "remove teamMember" should {
    val email         = "john.bloggs@example.com"
    val admin         = "admin@example.com"
    val adminsToEmail = Set.empty[String]

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

      private val verifiedAdmin      = "verified@example.com".asAdministratorCollaborator
      private val unverifiedAdmin    = "unverified@example.com".asAdministratorCollaborator
      private val removerAdmin       = "admin.email@example.com".asAdministratorCollaborator
      private val verifiedDeveloper  = "developer@example.com".asDeveloperCollaborator
      private val teamMemberToRemove = "to.remove@example.com".asAdministratorCollaborator

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
        eqTo(Set(verifiedAdmin.emailAddress))
      )(*)
    }
  }

  "request delete developer" should {
    val developerName   = "Testy McTester"
    val developerEmail  = "testy@example.com"
    val developerUserId = UserId.random

    "correctly create a deskpro ticket and audit record" in new Setup {
      when(mockDeskproConnector.createTicket(any[UserId], any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestDeveloperAccountDeletion(developerUserId, developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[UserId], any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
  }
}
