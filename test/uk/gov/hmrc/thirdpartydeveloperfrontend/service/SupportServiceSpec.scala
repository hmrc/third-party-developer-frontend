/*
 * Copyright 2024 HM Revenue & Customs
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

import uk.gov.hmrc.http.{HeaderCarrier, HttpException}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ExtendedApiDefinitionData.extendedApiDefinition
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceNameData.serviceName
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{SupportApi, SupportFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class SupportServiceSpec extends AsyncHmrcSpec {

  val sessionId                = "SESSIONID"
  val entryPoint               = "api"
  val savedFlow: SupportFlow   = SupportFlow(sessionId, entryPoint)
  val defaultFlow: SupportFlow = SupportFlow(sessionId, "unknown")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup extends ApmConnectorMockModule with FlowRepositoryMockModule {
    val underTest = new SupportService(ApmConnectorMock.aMock, FlowRepositoryMock.aMock)
    FlowRepositoryMock.SaveFlow.thenReturnSuccess
  }

  "Support Service" should {
    "default to a fixed SupportFlow if not found" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[SupportFlow]

      underTest.getSupportFlow(sessionId)
      FlowRepositoryMock.SaveFlow.verifyCalledWith(defaultFlow)
    }

    "get stored SupportFlow if found" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(savedFlow)
      val result = await(underTest.getSupportFlow(sessionId))
      result shouldBe savedFlow
      FlowRepositoryMock.SaveFlow.verifyCalledWith(savedFlow)
    }

    "create a flow" in new Setup {
      underTest.createFlow(sessionId, entryPoint)
      FlowRepositoryMock.SaveFlow.verifyCalledWith(savedFlow)
    }

    "fetchAllPublicApis" in new Setup {
      val apiList: List[ApiDefinition] = List(ApiDefinitionData.apiDefinition)
      ApmConnectorMock.FetchApiDefinitionsVisibleToUser.willReturn(apiList)
      val result                       = await(underTest.fetchAllPublicApis())
      result shouldBe apiList
    }

    "updateApiChoice with a found Api" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(savedFlow)
      ApmConnectorMock.FetchExtendedApiDefinition.willReturn(extendedApiDefinition)

      val result       = await(underTest.updateApiChoice(sessionId, serviceName))
      val expectedFlow = savedFlow.copy(api = Some(SupportApi(serviceName, extendedApiDefinition.name)))
      result.value shouldBe expectedFlow
      FlowRepositoryMock.SaveFlow.verifyCalledWith(expectedFlow)
    }

    "updateApiChoice with a missing Api" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(savedFlow)
      val exception = new HttpException("", 400)
      ApmConnectorMock.FetchExtendedApiDefinition.willFailWith(exception)

      val result = await(underTest.updateApiChoice(sessionId, serviceName))
      result.left.value shouldBe exception
    }

  }

}
