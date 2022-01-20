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

package uk.gov.hmrc.modules.uplift.controllers

import builder._
import controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.modules.uplift.domain.models._

import scala.concurrent.ExecutionContext.Implicits.global
import utils.AsyncHmrcSpec
import utils.LocalUserIdTracker
import uk.gov.hmrc.modules.uplift.services.GetProductionCredentialsFlowService
import org.mockito.MockitoSugar
import utils._
import domain.models.developers.LoggedInState
import uk.gov.hmrc.modules.uplift.services.mocks.FlowRepositoryMockModule

class GetProductionCredentialsFlowServiceSpec
                extends AsyncHmrcSpec
                with SampleSession
                with SampleApplication
                with SubscriptionTestHelperSugar
                with SubscriptionsBuilder
                with DeveloperBuilder
                with FlowRepositoryMockModule
                with LocalUserIdTracker {

  trait Setup extends MockitoSugar{
    val loggedInDeveloper = utils.DeveloperSession("dev@example.com", "firstName", "lastName", loggedInState = LoggedInState.LOGGED_IN)
    val underTest = new GetProductionCredentialsFlowService(FlowRepositoryMock.aMock)
    val sessionId = "sessionId"
    val sellResellOrDistribute = SellResellOrDistribute("answer")
    val responsibleIndividual = ResponsibleIndividual("oldname", "old@example.com")
    val apiSubscriptions = ApiSubscriptions()
    val flow = GetProductionCredentialsFlow(sessionId, Some(responsibleIndividual), Some(sellResellOrDistribute), Some(apiSubscriptions))
    val newResponsibleIndividual = ResponsibleIndividual("newname", "new@example.com")
  }

  "fetchFlow" should {
    "return the correct credentials flow if one already exists" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      val result = await(underTest.fetchFlow(loggedInDeveloper))

      result shouldBe flow
    }

    "return a new credentials flow if one does not already exist" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing
      FlowRepositoryMock.SaveFlow.thenReturnsSuccess
      val result = await(underTest.fetchFlow(loggedInDeveloper))

      result.sessionId shouldBe loggedInDeveloper.session.sessionId
    }
  }

  "storeResponsibleIndividual" should {
    "save responsible individual details correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnsSuccess

      val result = await(underTest.storeResponsibleIndividual(newResponsibleIndividual, loggedInDeveloper))

      result.responsibleIndividual shouldBe Some(newResponsibleIndividual)
    }
  }

  "findResponsibleIndividual" should {
    "return the correct individual details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)

      val result = await(underTest.findResponsibleIndividual(loggedInDeveloper))

      result shouldBe Some(responsibleIndividual)
    }
  }

  "storeSellResellOrDistribute" should {
    "save flow with new details" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.SaveFlow.thenReturnsSuccess

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
      FlowRepositoryMock.SaveFlow.thenReturnsSuccess

      val result = await(underTest.storeApiSubscriptions(apiSubscriptions, loggedInDeveloper))

      result.apiSubscriptions shouldBe Some(apiSubscriptions)
    }
  }

  "resetFlow" should {
    "remove details correctly" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(flow)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnsSuccess

      val result = await(underTest.resetFlow(loggedInDeveloper))

      result shouldBe flow
    }
  }
}
