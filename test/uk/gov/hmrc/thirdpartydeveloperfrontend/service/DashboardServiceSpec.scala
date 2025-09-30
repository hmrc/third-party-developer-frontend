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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{
  ApplicationWithSubscriptions,
  ApplicationWithSubscriptionsData,
  ApplicationWithSubscriptionsFixtures,
  CollaboratorData
}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class DashboardServiceSpec extends AsyncHmrcSpec
    with SubscriptionsBuilder
    with ApplicationWithSubscriptionsFixtures
    with CommonSessionFixtures
    with FixedClock {

  trait Setup extends FixedClock {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val mockPushPullNotificationsConnector: PushPullNotificationsConnector = mock[PushPullNotificationsConnector]

    val mockOrganisationConnector: OrganisationConnector = mock[OrganisationConnector]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockPushPullNotificationsConnector,
      mockPushPullNotificationsConnector,
      mockAppConfig
    )

    val dashboardService = new DashboardService(
      connectorsWrapper,
      mockOrganisationConnector,
      clock
    )
  }

  val productionApplication: ApplicationWithSubscriptions = ApplicationWithSubscriptionsData.one

  val sandboxApplicationId = ApplicationId.random

  val sandboxApplication: ApplicationWithSubscriptions = productionApplication.withId(sandboxApplicationId).inSandbox()

  val userId = CollaboratorData.Administrator.one.userId

  val orgId        = OrganisationId.random
  val organisation = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(userId)))

  "get the list of applications for the given user" should {
    "successfully return the list of applications" in new Setup {

      when(mockProductionApplicationConnector.fetchByTeamMember(eqTo(userId))(*)).thenReturn(successful(Seq(productionApplication)))
      when(mockSandboxApplicationConnector.fetchByTeamMember(eqTo(userId))(*)).thenReturn(successful(Seq(sandboxApplication)))

      val result = await(dashboardService.fetchApplicationList(userId))

      result.size shouldBe 2
    }
  }

  "fetchByUserId" should {
    "return organisations for given user id" in new Setup {
      when(mockOrganisationConnector.fetchOrganisationsByUserId(*[UserId])(*)).thenReturn(successful(List(organisation)))

      val result = await(dashboardService.fetchOrganisationsByUserId(userId))

      result shouldBe List(organisation)
    }
  }
}
