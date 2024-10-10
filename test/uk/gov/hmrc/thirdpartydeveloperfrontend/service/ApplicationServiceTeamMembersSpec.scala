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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiStatus, ApiVersion, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{UserId, _}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.VersionSubscription
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures

class ApplicationServiceTeamMembersSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker with ApplicationWithCollaboratorsFixtures {

  val versionOne = ApiVersionNbr("1.0")
  val versionTwo = ApiVersionNbr("2.0")

  trait Setup extends FixedClock with ApplicationCommandConnectorMockModule {
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
      ApplicationCommandConnectorMock.aMock,
      mockSubscriptionFieldsService,
      mockDeskproConnector,
      mockDeveloperConnector,
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockAuditService,
      clock
    )

    def theProductionConnectorthenReturnTheApplication(applicationId: ApplicationId, application: ApplicationWithCollaborators): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
    }

    def theSandboxConnectorthenReturnTheApplication(applicationId: ApplicationId, application: ApplicationWithCollaborators): Unit = {
      when(mockProductionApplicationConnector.fetchApplicationById(applicationId)).thenReturn(successful(None))
      when(mockSandboxApplicationConnector.fetchApplicationById(applicationId))
        .thenReturn(successful(Some(application)))
    }
  }

  def version(version: ApiVersionNbr, status: ApiStatus, subscribed: Boolean): VersionSubscription =
    VersionSubscription(ApiVersion(version, status, ApiAccess.PUBLIC, List.empty), subscribed)


  val productionApplication: ApplicationWithCollaborators = standardApp

  val sandboxApplication: ApplicationWithCollaborators = standardApp2.withEnvironment(Environment.SANDBOX)

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

  "request delete developer" should {
    val developerName   = "Testy McTester"
    val developerEmail  = "testy@example.com".toLaxEmail
    val developerUserId = UserId.random

    "correctly create a deskpro ticket and audit record" in new Setup {
      when(mockDeskproConnector.createTicket(any[Option[UserId]], any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestDeveloperAccountDeletion(developerUserId, developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[Option[UserId]], any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
  }
}
