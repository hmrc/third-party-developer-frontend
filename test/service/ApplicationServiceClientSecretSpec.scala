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

import java.time.Period
import java.util.UUID
import java.util.UUID.randomUUID

import builder._
import config.ApplicationConfig
import connectors._
import domain._
import domain.models.apidefinitions._
import domain.models.applications._
import domain.models.subscriptions.ApiSubscriptionFields
import service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}
import uk.gov.hmrc.http.{HeaderCarrier}
import uk.gov.hmrc.time.DateTimeUtils
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import domain.models.subscriptions.VersionSubscription
import service.PushPullNotificationsService.PushPullNotificationsConnector
import utils.LocalUserIdTracker

class ApplicationServiceClientSecretSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")
  val grantLength = Period.ofDays(547)

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
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, None, grantLength,
      Environment.PRODUCTION, Some("description"), Set())

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
}
