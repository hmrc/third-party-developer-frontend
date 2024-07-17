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

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{
  ApmConnector,
  DeskproConnector,
  ThirdPartyApplicationProductionConnector,
  ThirdPartyApplicationSandboxConnector,
  ThirdPartyDeveloperConnector
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationVerificationFailed, ApplicationVerificationSuccessful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ApplicationServiceUpliftSpec extends AsyncHmrcSpec {

  trait Setup extends LocalUserIdTracker with DeveloperSessionBuilder with DeveloperTestData with FixedClock with ApplicationCommandConnectorMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector       = mock[ThirdPartyApplicationSandboxConnector]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector    = mock[SubscriptionFieldsConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector     = mock[PushPullNotificationsConnector]

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector                   = mock[DeskproConnector]

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

    val applicationService = new ApplicationService(
      mockApmConnector,
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
  }

  implicit class ApiIdentifierSyntax(val context: String) {
    def asIdentifier(version: String): ApiIdentifier = ApiIdentifier(ApiContext(context), ApiVersionNbr(version))
    def asIdentifier(): ApiIdentifier                = asIdentifier("1.0")
  }

  "filterSubscriptionsForUplift" should {
    val app1                      = ApplicationId.random
    val app2                      = ApplicationId.random
    val appWithNothingButTestApis = ApplicationId.random
    val apiOk1a                   = "ok1".asIdentifier()
    val apiOk1b                   = "ok1".asIdentifier("2.0")
    val apiOk2a                   = "ok2".asIdentifier()
    val apiOk2b                   = "ok2".asIdentifier("2.0")
    val apiUnavailableInProd      = "bad21".asIdentifier()

    "Do not match apps with apis that cannot be uplifted" in new Setup {
      val appsToApis = Map(
        app1                      -> Set(apiOk1a, apiOk2a),
        app2                      -> Set(apiUnavailableInProd, apiOk1a, apiOk2a),
        appWithNothingButTestApis -> Set.empty[ApiIdentifier]
      )

      val result = ApplicationService.filterSubscriptionsForUplift(Set(apiOk1a, apiOk1b, apiOk2a, apiOk2b))(appsToApis)

      result shouldBe Set(app1)
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"

    "verify an uplift successful" in new Setup {
      ApplicationVerificationSuccessful
      when(mockProductionApplicationConnector.verify(verificationCode)).thenReturn(successful(ApplicationVerificationSuccessful))
      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationSuccessful
    }

    "verify an uplift with failure" in new Setup {
      when(mockProductionApplicationConnector.verify(verificationCode))
        .thenReturn(successful(ApplicationVerificationFailed))

      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationFailed
    }
  }
}
