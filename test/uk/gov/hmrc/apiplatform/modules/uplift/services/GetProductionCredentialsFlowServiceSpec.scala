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

package uk.gov.hmrc.apiplatform.modules.uplift.services

import scala.concurrent.ExecutionContext.Implicits.global

import org.mockito.MockitoSugar

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSessionId
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.{SampleUserSession, UserTestData}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class GetProductionCredentialsFlowServiceSpec
    extends AsyncHmrcSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper
    with SubscriptionsBuilder
    with FlowRepositoryMockModule
    with LocalUserIdTracker with DeveloperSessionBuilder with UserTestData {

  trait Setup extends MockitoSugar {
    val userSession              = standardDeveloper.loggedIn
    val underTest                = new GetProductionCredentialsFlowService(FlowRepositoryMock.aMock)
    val sessionId                = UserSessionId.random
    val sellResellOrDistribute   = SellResellOrDistribute("answer")
    val responsibleIndividual    = ResponsibleIndividual(FullName("oldname"), "old@example.com".toLaxEmail)
    val apiSubscriptions         = ApiSubscriptions()
    val flow                     = GetProductionCredentialsFlow(sessionId, Some(sellResellOrDistribute), Some(apiSubscriptions))
    val flowType                 = FlowType.GET_PRODUCTION_CREDENTIALS
    val newResponsibleIndividual = ResponsibleIndividual(FullName("newname"), "new@example.com".toLaxEmail)
  }

  "fetchFlow" should {
    "return the correct credentials flow if one already exists" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[GetProductionCredentialsFlow](userSession.sessionId)(flow)
      val result = await(underTest.fetchFlow(userSession))

      result shouldBe flow
    }

    "return a new credentials flow if one does not already exist" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[GetProductionCredentialsFlow]
      FlowRepositoryMock.SaveFlow.thenReturnSuccess
      val result = await(underTest.fetchFlow(userSession))

      result.sessionId shouldBe userSession.sessionId
    }
  }

  "storeSellResellOrDistribute" should {
    "save flow with new details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess

      val result = await(underTest.storeSellResellOrDistribute(sellResellOrDistribute, userSession))

      result.sellResellOrDistribute shouldBe Some(sellResellOrDistribute)
    }
  }

  "findSellResellOrDistribute" should {
    "return the correct details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)

      val result = await(underTest.findSellResellOrDistribute(userSession))

      result shouldBe Some(sellResellOrDistribute)
    }
  }

  "storeApiSubscriptions" should {
    "save api subscriptions correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess

      val result = await(underTest.storeApiSubscriptions(apiSubscriptions, userSession))

      result.apiSubscriptions shouldBe Some(apiSubscriptions)
    }
  }

  "resetFlow" should {
    "remove details correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(userSession.sessionId, flowType)

      val result = await(underTest.resetFlow(userSession))

      result shouldBe flow
    }
  }
}
