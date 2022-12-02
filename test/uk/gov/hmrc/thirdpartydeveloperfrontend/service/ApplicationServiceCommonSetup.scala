/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, FixedClock, LocalUserIdTracker}

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

abstract class ApplicationServiceCommonSetup extends AsyncHmrcSpec
  with ApplicationBuilder
  with LocalUserIdTracker
  with FixedClock {

  trait CommonSetup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector = mock[ThirdPartyApplicationSandboxConnector]
    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockAppConfig = mock[ApplicationConfig]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockSandboxSubscriptionFieldsConnector,
      mockProductionSubscriptionFieldsConnector,
      mockPushPullNotificationsConnector,
      mockPushPullNotificationsConnector,
      mockAppConfig
    )

    val mockApmConnector: ApmConnector = mock[ApmConnector]
    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]
    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockAuditService: AuditService = mock[AuditService]

    val applicationService = new ApplicationService(
      mockApmConnector,
      connectorsWrapper,
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

  val grantLength = Period.ofDays(547)
  
  val productionApplicationId = ApplicationId("Application ID")
  val productionClientId = ClientId(s"client-id-${randomUUID().toString}")
  val productionApplication: Application =
    Application(productionApplicationId, productionClientId, "name", LocalDateTime.now(ZoneOffset.UTC), Some(LocalDateTime.now(ZoneOffset.UTC)), None, grantLength, Environment.PRODUCTION, Some("description"), Set())
    
  val sandboxApplicationId = ApplicationId("Application ID")
  val sandboxClientId = ClientId("Client ID")
  val sandboxApplication: Application =
    Application(sandboxApplicationId, sandboxClientId, "name", LocalDateTime.now(ZoneOffset.UTC), Some(LocalDateTime.now(ZoneOffset.UTC)), None, grantLength, Environment.SANDBOX, Some("description"))

}
