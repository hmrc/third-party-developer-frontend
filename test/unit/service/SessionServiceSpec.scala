/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SessionServiceSpec extends UnitSpec with Matchers with MockitoSugar with ScalaFutures {

  trait Setup {
    implicit val hc = HeaderCarrier()

    val underTest = new SessionService(mock[ThirdPartyDeveloperConnector])

    val email = "thirdpartydeveloper@example.com"
    val encodedEmail = "thirdpartydeveloper%40example.com"
    val password = "Password1!"
    val developer = Developer(email, "firstName", "lastName")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer)
  }

  "createSession" should {
    "return the new session when the authentication succeeds" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.createSession(LoginRequest(email, password))).willReturn(session)

      await(underTest.authenticate(email, password)) shouldBe session
    }

    "throw invalid credentials None when the authentication fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.createSession(LoginRequest(email, password))).willReturn(Future.failed(new InvalidCredentials))

      intercept[InvalidCredentials](await(underTest.authenticate(email, password)))
    }

    "propagate the exception when the connector fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.createSession(LoginRequest(email, password)))
        .willThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.authenticate(email, password)))
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

      val result = await(underTest.fetch(sessionId))

      result shouldBe None
    }

    "propagate the exception when the connector fails" in new Setup {
      given(underTest.thirdPartyDeveloperConnector.fetchSession(sessionId))
        .willThrow(new RuntimeException)

      intercept[RuntimeException](await(underTest.fetch(sessionId)))

    }
  }
}
