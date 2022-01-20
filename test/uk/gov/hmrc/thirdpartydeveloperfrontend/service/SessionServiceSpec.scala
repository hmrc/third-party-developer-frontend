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

import builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import domain.models.connectors.{LoginRequest, TotpAuthenticationRequest, UserAuthenticationResponse}
import domain.models.developers.{LoggedInState, Session, SessionInvalid}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.http.HeaderCarrier
import utils.AsyncHmrcSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import domain.models.developers.UserId
import utils.LocalUserIdTracker

class SessionServiceSpec extends AsyncHmrcSpec with DeveloperBuilder with LocalUserIdTracker {
  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new SessionService(mock[ThirdPartyDeveloperConnector], mock[MfaMandateService], mock[FlowRepository])

    val email = "thirdpartydeveloper@example.com"
    val userId = UserId.random
    val encodedEmail = "thirdpartydeveloper%40example.com"
    val password = "Password1!"
    val totp = "123456"
    val nonce = "ABC-123"
    val developer = buildDeveloper(emailAddress = email)
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session))
  }

  "authenticate" should {
    "return the user authentication response from the connector when the authentication succeeds and mfaMandatedForUser is false" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      when(underTest.mfaMandateService.isMfaMandatedForUser(*[UserId])(*)).thenReturn(successful(false))
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*))
        .thenReturn(successful(userAuthenticationResponse))

      await(underTest.authenticate(email, password)) shouldBe userAuthenticationResponse

      verify(underTest.mfaMandateService).isMfaMandatedForUser(userId)
      verify(underTest.thirdPartyDeveloperConnector).authenticate(LoginRequest(email, password, mfaMandatedForUser = false))
    }

    "return the user authentication response from the connector when the authentication succeeds and mfaMandatedForUser is true" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      when(underTest.mfaMandateService.isMfaMandatedForUser(*[UserId])(*)).thenReturn(successful(true))
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*))
        .thenReturn(successful(userAuthenticationResponse))

      await(underTest.authenticate(email, password)) shouldBe userAuthenticationResponse

      verify(underTest.mfaMandateService).isMfaMandatedForUser(userId)
      verify(underTest.thirdPartyDeveloperConnector).authenticate(LoginRequest(email, password, mfaMandatedForUser = true))
    }

    "propagate the exception when the connector fails" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.findUserId(eqTo(email))(*)).thenReturn(successful(Some(ThirdPartyDeveloperConnector.CoreUserDetails(email, userId))))
      when(underTest.mfaMandateService.isMfaMandatedForUser(*[UserId])(*)).thenReturn(successful(true))
      when(underTest.thirdPartyDeveloperConnector.authenticate(*)(*))
        .thenThrow(new RuntimeException("this one"))

      intercept[RuntimeException](await(underTest.authenticate(email, password))).getMessage() shouldBe "this one"
    }
  }

  "authenticateTotp" should {
    "return the new session from the connector when the authentication succeeds" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.authenticateTotp(TotpAuthenticationRequest(email, totp, nonce))).thenReturn(successful(session))

      await(underTest.authenticateTotp(email, totp, nonce)) shouldBe session
    }

    "propagate the exception when the connector fails" in new Setup {
      when(underTest.thirdPartyDeveloperConnector.authenticateTotp(TotpAuthenticationRequest(email, totp, nonce)))
        .thenThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.authenticateTotp(email, totp, nonce)))
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
