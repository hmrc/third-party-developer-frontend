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

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.{SupportData, SupportDetailsForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.SupportSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproHorizonTicket, DeskproHorizonTicketMessage, DeskproHorizonTicketPerson}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorMockModule, DeskproHorizonConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class SupportServiceSpec extends AsyncHmrcSpec {

  val sessionId                        = SupportSessionId.random
  val entryPoint                       = SupportData.UsingAnApi.id
  val savedFlow: SupportFlow           = SupportFlow(sessionId, entryPoint)
  val defaultFlow: SupportFlow         = SupportFlow(sessionId, "unknown")
  val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  trait Setup extends ApmConnectorMockModule with FlowRepositoryMockModule with DeskproHorizonConnectorMockModule {
    val underTest        = new SupportService(ApmConnectorMock.aMock, DeskproHorizonConnectorMock.aMock, FlowRepositoryMock.aMock, mockAppConfig)
    val brand            = 5
    val apiNameConfig    = "5"
    val entryPointConfig = "7"
    when(mockAppConfig.deskproHorizonApiName).thenReturn(apiNameConfig)
    when(mockAppConfig.deskproHorizonSupportReason).thenReturn(entryPointConfig)
    when(mockAppConfig.deskproHorizonBrand).thenReturn(brand)
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
      val result                       = await(underTest.fetchAllPublicApis(None))
      result shouldBe apiList
      verify(ApmConnectorMock.aMock).fetchApiDefinitionsVisibleToUser(None)
    }

    "updateWithDelta" in new Setup {
      val oldValue = SupportSessionId.random
      val newValue = SupportSessionId.random

      val result = await(underTest.updateWithDelta(f => f.copy(sessionId = newValue))(SupportFlow(oldValue, "?")))

      result shouldBe SupportFlow(newValue, "?")
    }

    "fetchAllPublicApis with a user" in new Setup {
      val apiList: List[ApiDefinition]   = List(ApiDefinitionData.apiDefinition)
      ApmConnectorMock.FetchApiDefinitionsVisibleToUser.willReturn(apiList)
      private val loggedInUserId: UserId = UserId(UUID.randomUUID())
      val result                         = await(underTest.fetchAllPublicApis(Some(loggedInUserId)))
      result shouldBe apiList
      verify(ApmConnectorMock.aMock).fetchApiDefinitionsVisibleToUser(Some(loggedInUserId))
    }

    "submitTicket with no api should send no api" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(savedFlow)
      DeskproHorizonConnectorMock.CreateTicket.thenReturnsSuccess()
      await(
        underTest.submitTicket(
          SupportFlow(
            SupportSessionId.random,
            SupportData.FindingAnApi.id,
            None
          ),
          SupportDetailsForm(
            "This is some\ndescription",
            "test name",
            "email@test.com",
            None,
            None
          )
        )
      )

      verify(DeskproHorizonConnectorMock.aMock).createTicket(eqTo(DeskproHorizonTicket(
        person = DeskproHorizonTicketPerson("test name", "email@test.com"),
        subject = "HMRC Developer Hub: Support Enquiry",
        message = DeskproHorizonTicketMessage("This is some<br>description"),
        brand = brand,
        fields = Map(entryPointConfig -> SupportData.FindingAnApi.text)
      )))(*)
    }

    "submitTicket with api should be name" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn(savedFlow)
      DeskproHorizonConnectorMock.CreateTicket.thenReturnsSuccess()

      await(
        underTest.submitTicket(
          SupportFlow(
            SupportSessionId.random,
            SupportData.UsingAnApi.id,
            Some(SupportData.MakingAnApiCall.id),
            Some("Hello world")
          ),
          SupportDetailsForm(
            "This is some\ndescription",
            "test name",
            "email@test.com",
            None,
            None
          )
        )
      )

      verify(DeskproHorizonConnectorMock.aMock).createTicket(eqTo(DeskproHorizonTicket(
        person = DeskproHorizonTicketPerson("test name", "email@test.com"),
        subject = "HMRC Developer Hub: Support Enquiry",
        message = DeskproHorizonTicketMessage("This is some<br>description"),
        brand = brand,
        fields = Map(apiNameConfig -> "Hello world", entryPointConfig -> SupportData.MakingAnApiCall.text)
      )))(*)

    }

  }

}
