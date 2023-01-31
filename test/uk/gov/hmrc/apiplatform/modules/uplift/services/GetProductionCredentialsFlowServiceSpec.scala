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

import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ResponsibleIndividual, SellResellOrDistribute}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}

class GetProductionCredentialsFlowServiceSpec
    extends AsyncHmrcSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with SubscriptionsBuilder
    with FlowRepositoryMockModule
    with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperBuilder {

  trait Setup extends MockitoSugar {
    val loggedInDeveloper        = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloperWithRandomId("dev@example.com", "firstName", "lastName"))
    val underTest                = new GetProductionCredentialsFlowService(FlowRepositoryMock.aMock)
    val sessionId                = "sessionId"
    val sellResellOrDistribute   = SellResellOrDistribute("answer")
    val responsibleIndividual    = ResponsibleIndividual(ResponsibleIndividual.Name("oldname"), ResponsibleIndividual.EmailAddress("old@example.com"))
    val apiSubscriptions         = ApiSubscriptions()
    val flow                     = GetProductionCredentialsFlow(sessionId, Some(sellResellOrDistribute), Some(apiSubscriptions))
    val flowType                 = FlowType.GET_PRODUCTION_CREDENTIALS
    val newResponsibleIndividual = ResponsibleIndividual(ResponsibleIndividual.Name("newname"), ResponsibleIndividual.EmailAddress("new@example.com"))
  }

  "fetchFlow" should {
    "return the correct credentials flow if one already exists" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(loggedInDeveloper.session.sessionId)(flow)
      val result = await(underTest.fetchFlow(loggedInDeveloper))

      result shouldBe flow
    }

    "return a new credentials flow if one does not already exist" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[GetProductionCredentialsFlow]
      FlowRepositoryMock.SaveFlow.thenReturnSuccess
      val result = await(underTest.fetchFlow(loggedInDeveloper))

      result.sessionId shouldBe loggedInDeveloper.session.sessionId
    }
  }

  "storeSellResellOrDistribute" should {
    "save flow with new details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess

      val result = await(underTest.storeSellResellOrDistribute(sellResellOrDistribute, loggedInDeveloper))

      result.sellResellOrDistribute shouldBe Some(sellResellOrDistribute)
    }
  }

  "findSellResellOrDistribute" should {
    "return the correct details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)

      val result = await(underTest.findSellResellOrDistribute(loggedInDeveloper))

      result shouldBe Some(sellResellOrDistribute)
    }
  }

  "storeApiSubscriptions" should {
    "save api subscriptions correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess

      val result = await(underTest.storeApiSubscriptions(apiSubscriptions, loggedInDeveloper))

      result.apiSubscriptions shouldBe Some(apiSubscriptions)
    }
  }

  "resetFlow" should {
    "remove details correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(loggedInDeveloper.session.sessionId, flowType)

      val result = await(underTest.resetFlow(loggedInDeveloper))

      result shouldBe flow
    }
  }
}
