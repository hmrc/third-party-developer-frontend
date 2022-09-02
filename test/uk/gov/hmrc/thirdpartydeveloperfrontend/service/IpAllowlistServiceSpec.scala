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

import java.util.UUID.randomUUID

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, IpAllowlist}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType.IP_ALLOW_LIST
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.IpAllowlistFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule

class IpAllowlistServiceSpec
    extends AsyncHmrcSpec 
    with Matchers 
    with TestApplications
    with CollaboratorTracker
    with DeveloperBuilder 
    with LocalUserIdTracker {

  trait Setup extends FlowRepositoryMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val sessionId: String = randomUUID.toString

    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockConnectorsWrapper: ConnectorsWrapper = mock[ConnectorsWrapper]
    when(mockConnectorsWrapper.forEnvironment(*))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mock[SubscriptionFieldsConnector], mock[PushPullNotificationsConnector]))

    val underTest = new IpAllowlistService(FlowRepositoryMock.aMock, mockConnectorsWrapper)
  }

  "getIpAllowlistFlow" should {
    "return existing flow" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(expectedFlow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "create new flow if it does not exist" in new Setup {
      val ipAllowlist = Set("1.1.1.1/24")
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, ipAllowlist)
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(anApplication(ipAllowlist = IpAllowlist(allowlist = ipAllowlist)), sessionId))

      result shouldBe expectedFlow
      FlowRepositoryMock.SaveFlow.verifyCalledWith[IpAllowlistFlow](expectedFlow)
    }
  }

  "discardIpAllowlistFlow" should {
    "delete the flow in the repository" in new Setup {
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, IP_ALLOW_LIST)

      val result: Boolean = await(underTest.discardIpAllowlistFlow(sessionId))

      result shouldBe true
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.verifyCalledWith(sessionId, IP_ALLOW_LIST)
    }
  }

  "addCidrBlock" should {
    val newCidrBlock = "2.2.2.1/32"

    "add the cidr block to the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", newCidrBlock))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }

    "add the cidr block to a new flow if it does not exist yet" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set(newCidrBlock))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, anApplication(), sessionId))

      result shouldBe expectedFlow
    }
  }

  "removeCidrBlock" should {
    val cidrBlockToRemove = "2.2.2.1/32"

    "remove cidr block from the existing flow" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", cidrBlockToRemove))
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.removeCidrBlock(cidrBlockToRemove, sessionId))

      result shouldBe expectedFlow
    }

    "fail when no flow exists for the given session ID" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)

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
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, app.ipAllowlist.required, existingFlow.allowlist))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, IP_ALLOW_LIST)

      val result: ApplicationUpdateSuccessful = await(underTest.activateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockThirdPartyApplicationConnector).updateIpAllowlist(app.id, app.ipAllowlist.required, existingFlow.allowlist)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.verifyCalledWith(sessionId, IP_ALLOW_LIST)
    }

    "fail when activating an empty allowlist" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set())
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.activateIpAllowlist(anApplication(), sessionId))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be activated because it is empty"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }

    "fail when no flow exists for the given session ID" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.activateIpAllowlist(anApplication(), sessionId))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }

  "deactivateIpAllowlist" should {
    "update the allowlist in TPA with an empty set" in new Setup {
      val app: Application = anApplication()
      when(mockThirdPartyApplicationConnector.updateIpAllowlist(app.id, app.ipAllowlist.required, Set.empty))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, IP_ALLOW_LIST)

      val result: ApplicationUpdateSuccessful = await(underTest.deactivateIpAllowlist(app, sessionId))

      result shouldBe ApplicationUpdateSuccessful
      verify(mockThirdPartyApplicationConnector).updateIpAllowlist(app.id, app.ipAllowlist.required, Set.empty)
    }

    "fail when the IP allowlist is required" in new Setup {
      val app: Application = anApplication(ipAllowlist = IpAllowlist(required = true))

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.deactivateIpAllowlist(app, sessionId))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be deactivated because it is required"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }
}
