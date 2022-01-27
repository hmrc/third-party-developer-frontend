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

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationProductionConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationSandboxConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.UpliftRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpliftSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationAlreadyExists
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationVerificationSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationVerificationFailed
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.DeveloperSessionBuilder
class ApplicationServiceUpliftSpec extends AsyncHmrcSpec {
  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector = mock[ThirdPartyApplicationSandboxConnector]

    val mockSubscriptionsService: SubscriptionsService = mock[SubscriptionsService]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]

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
      mockSubscriptionFieldsService,
      mockSubscriptionsService,
      mockDeskproConnector,
      mockDeveloperConnector,
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockAuditService
    )
  }

  implicit class ApiIdentifierSyntax(val context: String) {
    def asIdentifier(version: String): ApiIdentifier = ApiIdentifier(ApiContext(context), ApiVersion(version))
    def asIdentifier(): ApiIdentifier = asIdentifier("1.0")
  }

  "filterSubscriptionsForUplift" should {
    val app1 = ApplicationId("app1")
    val app2 = ApplicationId("app2")
    val appWithNothingButTestApis = ApplicationId("app3")
    val apiOk1a = "ok1".asIdentifier
    val apiOk1b = "ok1".asIdentifier("2.0")
    val apiOk2a = "ok2".asIdentifier
    val apiOk2b = "ok2".asIdentifier("2.0")
    val apiUnavailableInProd = "bad21".asIdentifier
    
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

  "requestUplift" should {
    val applicationId = ApplicationId("applicationId")
    val applicationName = "applicationName"

    val user =
      DeveloperSessionBuilder("Firstname", "Lastname", "email@example.com", loggedInState = LoggedInState.LOGGED_IN)

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

    "verify an uplift successful" in new Setup {ApplicationVerificationSuccessful
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