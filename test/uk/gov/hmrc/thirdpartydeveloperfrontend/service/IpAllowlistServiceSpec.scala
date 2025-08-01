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

import org.scalatest.matchers.should.Matchers

import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures, CidrBlock, IpAllowlist}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSessionId
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType.IP_ALLOW_LIST
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.IpAllowlistFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorCommandModuleMockModule, ApmConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class IpAllowlistServiceSpec
    extends AsyncHmrcSpec
    with Matchers
    with ApplicationWithCollaboratorsFixtures
    with CollaboratorTracker
    with UserTestData
    with UserBuilder
    with LocalUserIdTracker {

  implicit class IpAllowlistSyntax(app: ApplicationWithCollaborators) {
    def withIpAllowlist(list: IpAllowlist) = app.modify(_.copy(ipAllowlist = list))
  }

  val appWithNoIpAllowList = standardApp.withIpAllowlist(defaultIpAllowList)

  trait Setup extends FlowRepositoryMockModule
      with FixedClock
      with ApmConnectorMockModule
      with ApmConnectorCommandModuleMockModule {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val sessionId                  = UserSessionId.random

    val mockThirdPartyApplicationConnector: ThirdPartyApplicationConnector = mock[ThirdPartyApplicationConnector]
    val mockConnectorsWrapper: ConnectorsWrapper                           = mock[ConnectorsWrapper]
    when(mockConnectorsWrapper.forEnvironment(*))
      .thenReturn(Connectors(mockThirdPartyApplicationConnector, mock[PushPullNotificationsConnector]))

    val underTest = new IpAllowlistService(
      FlowRepositoryMock.aMock,
      mockConnectorsWrapper,
      ApmConnectorCommandModuleMock.aMock,
      clock
    )
  }

  "getIpAllowlistFlow" should {
    "return existing flow" in new Setup {
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24"))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(expectedFlow)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(appWithNoIpAllowList, sessionId))

      result shouldBe expectedFlow
    }

    "create new flow if it does not exist" in new Setup {
      val ipAllowlist                   = Set("1.1.1.1/24")
      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, ipAllowlist)
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.getIpAllowlistFlow(appWithNoIpAllowList.withIpAllowlist(IpAllowlist(allowlist = ipAllowlist)), sessionId))

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

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, appWithNoIpAllowList, sessionId))

      result shouldBe expectedFlow
    }

    "add the cidr block to a new flow if it does not exist yet" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)
      FlowRepositoryMock.SaveFlow.thenReturnSuccess[IpAllowlistFlow]

      val result: IpAllowlistFlow = await(underTest.addCidrBlock(newCidrBlock, appWithNoIpAllowList, sessionId))

      val expectedFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set(newCidrBlock))
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
    "successfully dispatch a command to change ip allowlist" in new Setup {
      val app = appWithNoIpAllowList.withIpAllowlist(
        IpAllowlist(
          required = true,
          allowlist = Set("1.1.1.1/24")
        )
      )

      val existingFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", "2.2.2.2/24"))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)

      val cmd = ApplicationCommands.ChangeIpAllowlist(
        Actors.AppCollaborator(standardDeveloper.email),
        instant,
        true,
        List(CidrBlock("1.1.1.1/24")),
        List(CidrBlock("1.1.1.1/24"), CidrBlock("2.2.2.2/24"))
      )

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(app)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, IP_ALLOW_LIST)

      val result = await(underTest.activateIpAllowlist(app, sessionId, standardDeveloper.email))

      result shouldBe ApplicationUpdateSuccessful

      inside(ApmConnectorCommandModuleMock.Dispatch.verifyCommand()) {
        case ApplicationCommands.ChangeIpAllowlist(actor, _, required, oldIpAllowlist, newIpAllowlist) =>
          actor shouldBe Actors.AppCollaborator(standardDeveloper.email)
          required shouldBe true
          oldIpAllowlist shouldBe List(CidrBlock("1.1.1.1/24"))
          newIpAllowlist shouldBe List(CidrBlock("1.1.1.1/24"), CidrBlock("2.2.2.2/24"))
      }

      FlowRepositoryMock.DeleteBySessionIdAndFlowType.verifyCalledWith(sessionId, IP_ALLOW_LIST)
    }

    "fail when activating an empty allowlist" in new Setup {
      val existingFlow: IpAllowlistFlow = IpAllowlistFlow(sessionId, Set())
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.activateIpAllowlist(appWithNoIpAllowList, sessionId, standardDeveloper.email))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be activated because it is empty"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }

    "fail when no flow exists for the given session ID" in new Setup {
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[IpAllowlistFlow](sessionId)

      val expectedException: IllegalStateException = intercept[IllegalStateException] {
        await(underTest.activateIpAllowlist(appWithNoIpAllowList, sessionId, standardDeveloper.email))
      }

      expectedException.getMessage shouldBe s"No IP allowlist flow exists for session ID $sessionId"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }

  "deactivateIpAllowlist" should {
    "update the allowlist in TPA with an empty set" in new Setup {
      val app = appWithNoIpAllowList.withIpAllowlist(
        IpAllowlist(
          required = false,
          allowlist = Set("1.1.1.1/24")
        )
      )

      val existingFlow = IpAllowlistFlow(sessionId, Set("1.1.1.1/24", "2.2.2.2/24"))
      FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[IpAllowlistFlow](sessionId)(existingFlow)

      val cmd = ApplicationCommands.ChangeIpAllowlist(
        Actors.AppCollaborator(standardDeveloper.email),
        instant,
        false,
        List(CidrBlock("1.1.1.1/24")),
        List.empty
      )
      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccessFor(cmd)(app)
      FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, IP_ALLOW_LIST)

      val result = await(underTest.deactivateIpAllowlist(app, sessionId, standardDeveloper.email))

      result shouldBe ApplicationUpdateSuccessful

      inside(ApmConnectorCommandModuleMock.Dispatch.verifyCommand()) {
        case ApplicationCommands.ChangeIpAllowlist(actor, _, required, oldIpAllowlist, newIpAllowlist) =>
          actor shouldBe Actors.AppCollaborator(standardDeveloper.email)
          required shouldBe false
          oldIpAllowlist shouldBe List(CidrBlock("1.1.1.1/24"))
          newIpAllowlist shouldBe List.empty
      }
    }

    "fail when the IP allowlist is required" in new Setup {
      val app: ApplicationWithCollaborators = appWithNoIpAllowList.withIpAllowlist(IpAllowlist(required = true))

      val expectedException: ForbiddenException = intercept[ForbiddenException] {
        await(underTest.deactivateIpAllowlist(app, sessionId, standardDeveloper.email))
      }

      expectedException.getMessage shouldBe s"IP allowlist for session ID $sessionId cannot be deactivated because it is required"
      verifyZeroInteractions(mockThirdPartyApplicationConnector)
    }
  }
}
