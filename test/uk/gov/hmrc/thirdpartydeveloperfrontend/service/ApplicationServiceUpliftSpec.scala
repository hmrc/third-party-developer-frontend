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

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.*
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationVerificationFailed, ApplicationVerificationSuccessful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorCommandModuleMockModule, ApmConnectorMockModule, ThirdPartyOrchestratorConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ApplicationServiceUpliftSpec extends AsyncHmrcSpec {

  trait Setup extends LocalUserIdTracker with DeveloperSessionBuilder with UserTestData with FixedClock with ApmConnectorMockModule with ApmConnectorCommandModuleMockModule
      with ThirdPartyOrchestratorConnectorMockModule {
    given hc: HeaderCarrier = HeaderCarrier()

    val mockApmConnector: ApmConnector = subclassMock[ApmConnector]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]

    val mockApiPlatformDeskproConnector: ApiPlatformDeskproConnector = mock[ApiPlatformDeskproConnector]
    val mockOrganisationConnector: OrganisationConnector             = mock[OrganisationConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val applicationService = new ApplicationService(
      mockApmConnector,
      ApmConnectorCommandModuleMock.aMock,
      mockApiPlatformDeskproConnector,
      ThirdPartyOrchestratorConnectorMock.aMock,
      mockOrganisationConnector,
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
      ThirdPartyOrchestratorConnectorMock.Verify.returns(ApplicationVerificationSuccessful)
      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationSuccessful
    }

    "verify an uplift with failure" in new Setup {
      ThirdPartyOrchestratorConnectorMock.Verify.returns(ApplicationVerificationFailed)
      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationFailed
    }
  }
}
