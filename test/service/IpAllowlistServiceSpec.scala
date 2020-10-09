/*
 * Copyright 2020 HM Revenue & Customs
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

package service

import java.util.UUID.randomUUID

import connectors.ThirdPartyApplicationConnector
import domain.ApplicationUpdateSuccessful
import domain.models.applications.Application
import domain.models.flows.IpAllowlistFlow
import org.scalatest.Matchers
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.formatIpAllowlistFlow
import service.PushPullNotificationsService.PushPullNotificationsConnector
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.HeaderCarrier
import utils.{AsyncHmrcSpec, TestApplications}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class IpAllowlistServiceSpec extends AsyncHmrcSpec with Matchers {

  trait Setup extends TestApplications {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val sessionId: String = randomUUID.toString

    val mockFlowRepository: FlowRepository = mock[FlowRepository]
    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    when(mockConnectorsWrapper.forEnvironment(*))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mock[SubscriptionFieldsConnector], mock[PushPullNotificationsConnector]))

    val underTest = new IpAllowlistService(mockFlowRepository, mockConnectorsWrapper)
  }

  "getIpAllowlistFlow" should {
    "return existing flow" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(Some(expectedFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "create new flow if it does not exist" in new Setup {
      val ipAllowlist = Set("1.1.1.1/24")
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, ipAllowlist)
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(None))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication().copy(ipWhitelist = ipAllowlist), sessionId))

      result shouldBe expectedFlow
      verify(mockFlowRepository).saveFlow(expectedFlow)
    }
  }

  "discardIpAllowlistFlow" should {
    "delete the flow in the repository" in new Setup {
      when(mockFlowRepository.deleteBySessionId(sessionId)).thenReturn(successful(true))

      val result: Boolean = await(underTest.discardIpAllowlistFlow(sessionId))

      result shouldBe true
      verify(mockFlowRepository).deleteBySessionId(sessionId)
    }
  }

  "addCidrBlock" should {
    val newCidrBlock = "2.2.2.1/32"

    "add the cidr block to the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", newCidrBlock))
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(Some(existingFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "add the cidr block to a new flow if it does not exist yet" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set(newCidrBlock))
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(None))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }
  }

  "removeCidrBlock" should {
    val cidrBlockToRemove = "2.2.2.1/32"

    "remove cidr block from the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", cidrBlockToRemove))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(Some(existingFlow)))
      when(mockFlowRepository.saveFlow(expectedFlow)).thenReturn(successful(expectedFlow))

      val result: IpAllowlistFlow = await(underTest.removeCidrBlock(cidrBlockToRemove, sessionId))

      result shouldBe expectedFlow
    }

    "fail when no flow exists for the given session ID" in new Setup {
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(None))

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.removeCidrBlock(cidrBlockToRemove, sessionId))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
    }
  }

  "activateIpAllowlist" should {
    "save the allowlist in TPA" in new Setup {
      val app: Application = anApplication()
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(Some(existingFlow)))
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, existingFlow.allowlist)).thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockFlowRepository.deleteBySessionId(sessionId)).thenReturn(successful(true))

      val result: ApplicationUpdateSuccessful = await(underTest.activateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockFlowRepository).deleteBySessionId(sessionId)
    }

    "fail when no flow exists for the given session ID" in new Setup {
      when(mockFlowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)).thenReturn(successful(None))

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.activateIpAllowlist(anApplication(), sessionId))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
    }
  }

  "deactivateIpAllowlist" should {
    "update the allowlist in TPA with an empty set" in new Setup {
      val app: Application = anApplication()
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, Set.empty)).thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockFlowRepository.deleteBySessionId(sessionId)).thenReturn(successful(true))

      val result: ApplicationUpdateSuccessful = await(underTest.deactivateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockFlowRepository).deleteBySessionId(sessionId)
    }
  }
}
