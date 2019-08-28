/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.service

import connectors.ThirdPartyDeveloperConnector
import domain._
import org.mockito.BDDMockito.given
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionServiceSpec extends UnitSpec with Matchers with MockitoSugar with ScalaFutures {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest = new SessionService(mock[ThirdPartyDeveloperConnector])

    val email = "thirdpartydeveloper@example.com"
    val encodedEmail = "thirdpartydeveloper%40example.com"
    val password = "Password1!"
    val totp = "123456"
    val nonce = "ABC-123"
    val developer = Developer(email, "firstName", "lastName")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session), mfaEnablementRequired = false)
  }

  "authenticate" should {
    "return the user authentication response from the connector when the authentication succeeds" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.authenticate(LoginRequest(email, password, mfaMandatedForUser = false)))
        .willReturn(userAuthenticationResponse)

      await(underTest.authenticate(email, password, mfaMandatedForUser = false)) shouldBe userAuthenticationResponse
    }


    "propagate the exception when the connector fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.authenticate(LoginRequest(email, password, mfaMandatedForUser = false)))
        .willThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.authenticate(email, password, mfaMandatedForUser = false)))
    }
  }

  "authenticateTotp" should {
    "return the new session from the connector when the authentication succeeds" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.authenticateTotp(TotpAuthenticationRequest(email, totp, nonce))).willReturn(session)

      await(underTest.authenticateTotp(email, totp, nonce)) shouldBe session
    }

    "propagate the exception when the connector fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.authenticateTotp(TotpAuthenticationRequest(email, totp, nonce)))
        .willThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.authenticateTotp(email, totp, nonce)))
    }
  }

  "fetchUser" should {
    "return the developer when it exists" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .willReturn(session)

      await(underTest.fetch(sessionId)) shouldBe Some(session)
    }

    "return None when its does not exist" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .willReturn(Future.failed(new SessionInvalid))

      private val result = await(underTest.fetch(sessionId))

      result shouldBe None
    }

    "propagate the exception when the connector fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .willThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.fetch(sessionId)))

    }
  }
}
