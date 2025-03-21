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

import java.time.Period
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{DeviceSessionId, MfaId}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto.AccessCodeAuthenticationRequest
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, SessionInvalid, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.AppsByTeamMemberServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, CollaboratorTracker}

class SessionServiceSpec extends AsyncHmrcSpec with UserBuilder with LocalUserIdTracker with CollaboratorTracker with AppsByTeamMemberServiceMock with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new SessionService(mock[ThirdPartyDeveloperConnector], appsByTeamMemberServiceMock, mock[FlowRepository])

    val email                      = "thirdpartydeveloper@example.com".toLaxEmail
    val userId                     = UserId.random
    val encodedEmail               = "thirdpartydeveloper%40example.com"
    val password                   = "Password1!"
    val accessCode                 = "123456"
    val nonce                      = "ABC-123"
    val mfaId                      = MfaId.random
    val developer                  = buildTrackedUser(emailAddress = email)
    val sessionId                  = UserSessionId.random
    val deviceSessionId            = DeviceSessionId.random
    val session                    = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled = false, session = Some(session))

    val applicationId       = ApplicationId.random
    val clientId            = ClientId("myClientId")
    val grantLength: Period = Period.ofDays(547)

    val applicationsWhereUserIsDeveloperInProduction =
      Seq(
        standardApp.withSubscriptions(Set.empty)
      )

    val applicationsWhereUserIsAdminInProduction =
      Seq(
        standardApp.withSubscriptions(Set.empty)
      )

  }

  "authenticate" should {
    "return the user authentication response from the connector when the authentication succeeds and user is not admin on production application" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      fetchProductionSummariesByAdmin(userId, applicationsWhereUserIsDeveloperInProduction)
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*)).thenReturn(successful(userAuthenticationResponse))
      await(underTest.authenticate(email, password, Some(deviceSessionId))) shouldBe ((userAuthenticationResponse, userId))

      verify(appsByTeamMemberServiceMock).fetchProductionSummariesByAdmin(eqTo(userId))(*)
      verify(underTest.thirdPartyDeveloperConnector).authenticate(*)(*)
    }

    "return the user authentication response from the connector when the authentication succeeds and user is an admin on production application" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      fetchProductionSummariesByAdmin(userId, applicationsWhereUserIsAdminInProduction)
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*))
        .thenReturn(successful(userAuthenticationResponse))

      await(underTest.authenticate(email, password, Some(deviceSessionId))) shouldBe ((userAuthenticationResponse, userId))

      verify(appsByTeamMemberServiceMock).fetchProductionSummariesByAdmin(eqTo(userId))(*)
      verify(underTest.thirdPartyDeveloperConnector).authenticate(*)(*)
    }

    "propagate the exception when the connector fails" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      fetchProductionSummariesByAdmin(userId, applicationsWhereUserIsDeveloperInProduction)
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*))
        .thenThrow(new RuntimeException("this one"))

      intercept[RuntimeException](await(underTest.authenticate(email, password, Some(deviceSessionId)))).getMessage shouldBe "this one"
    }
  }

  "authenticateMfaAccessCode" should {
    "return the new session from the connector when the authentication succeeds" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.authenticateMfaAccessCode(AccessCodeAuthenticationRequest(email, accessCode, nonce, mfaId))).thenReturn(successful(session))

      await(underTest.authenticateAccessCode(email, accessCode, nonce, mfaId)) shouldBe session
    }

    "propagate the exception when the connector fails" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.authenticateMfaAccessCode(AccessCodeAuthenticationRequest(email, accessCode, nonce, mfaId)))
        .thenThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.authenticateAccessCode(email, accessCode, nonce, mfaId)))
    }
  }

  "fetchUser" should {
    "return the developer when it exists" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .thenReturn(successful(session))

      await(underTest.fetch(sessionId)) shouldBe Some(session)
    }

    "return None when its does not exist" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .thenReturn(failed(new SessionInvalid))

      private val result = await(underTest.fetch(sessionId))

      result shouldBe None
    }

    "propagate the exception when the connector fails" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .thenThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.fetch(sessionId)))

    }
  }

  "updateUserFlowSessions" should {
    "update flow using the flow repository" in new Setup {
      when(underTest.flowRepository.updateLastUpdated(sessionId)).thenReturn(successful(()))

      await(underTest.updateUserFlowSessions(sessionId))

      verify(underTest.flowRepository).updateLastUpdated(sessionId)
    }

    "propagate the exception when the repository fails" in new Setup {
      when(underTest.flowRepository.updateLastUpdated(sessionId)).thenThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.updateUserFlowSessions(sessionId)))
    }
  }
}
