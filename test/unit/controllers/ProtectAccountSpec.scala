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

package unit.controllers

import java.net.URI

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.{ProtectAccount, routes}
import domain.{Developer, LoggedInState, Session, UpdateLoggedInStateRequest}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito._
import org.mockito.Mockito.verify
import org.scalatest.Assertion
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, SEE_OTHER}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import qr.{OtpAuthUri, QRCode}
import service.{MFAResponse, MFAService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProtectAccountSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val loggedInUser = Developer("johnsmith@example.com", "John", "Doe")
    val qrImage = "qrImage"
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"

    def loggedInState: LoggedInState

    val underTest: ProtectAccount = new ProtectAccount(
      mock[ThirdPartyDeveloperConnector],
      mock[OtpAuthUri],
      mock[MFAService],
      mock[SessionService],
      messagesApi,
      mock[ErrorHandler])(mock[ApplicationConfig], global) {
      override val qrCode: QRCode = mock[QRCode]
    }

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Future.successful(Some(Session(sessionId, loggedInUser, loggedInState))))

    def protectAccountRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest().
        withLoggedIn(underTest)(sessionId).
        withSession("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken).
        withFormUrlEncodedBody("accessCode" -> code)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    given(underTest.thirdPartyDeveloperConnector.fetchDeveloper(mockEq(loggedInUser.email))(any[HeaderCarrier])).
      willReturn(Some(Developer(loggedInUser.email, "Bob", "Smith", None)))
  }

  trait SetupProtectedAccount extends Setup {
    given(underTest.thirdPartyDeveloperConnector.fetchDeveloper(mockEq(loggedInUser.email))(any[HeaderCarrier])).
      willReturn(Some(Developer(loggedInUser.email, "Bob", "Smith", None, Some(true))))
  }

  trait SetupSuccessfulStart2SV extends Setup {

    given(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInUser.email)).willReturn(otpUri)
    given(underTest.qrCode.generateDataImageBase64(otpUri.toString)).willReturn(qrImage)
    given(underTest.thirdPartyDeveloperConnector.createMfaSecret(mockEq(loggedInUser.email))(any[HeaderCarrier])).willReturn(secret)
  }

  trait PartLogged extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
  }

  trait LoggedIn extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
  }

  trait SetupFailedVerification extends Setup {
    given(underTest.mfaService.enableMfa(any[String], any[String])(any[HeaderCarrier])).
      willReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulVerification extends Setup {
    given(underTest.mfaService.enableMfa(mockEq(loggedInUser.email), mockEq(correctCode))(any[HeaderCarrier])).
      willReturn(Future.successful(MFAResponse(true)))
  }

  trait SetupFailedRemoval extends Setup {
    given(underTest.mfaService.removeMfa(any[String], any[String])(any[HeaderCarrier])).
      willReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulRemoval extends Setup {
    given(underTest.mfaService.removeMfa(mockEq(loggedInUser.email), mockEq(correctCode))(any[HeaderCarrier])).
      willReturn(Future.successful(MFAResponse(true)))
  }

  "Given a user is not logged in" when {
    "getQrCode() is called it" should {
      "redirect to the login page" in new SetupSuccessfulStart2SV with LoggedIn {

        val invalidSessionId = "notASessionId"

        given(underTest.sessionService.fetch(mockEq(invalidSessionId))(any[HeaderCarrier]))
          .willReturn(Future.successful(None))

        private val request = FakeRequest().
          withLoggedIn(underTest)(invalidSessionId)

        private val result = await(underTest.getQrCode()(request))

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }
  }

  "Given a user is part logged in and enabling Mfa" when {
    "getQrCode() is called it" should {
      "return secureAccountSetupPage with secret from third party developer" in new SetupSuccessfulStart2SV with PartLogged {
        private val request = FakeRequest().
          withLoggedIn(underTest)(sessionId)

        private val result = await(underTest.getQrCode()(request))

        status(result) shouldBe 200
        private val dom = Jsoup.parse(bodyOf(result))
        dom.getElementById("secret").html() shouldBe "abcd efgh"
        dom.getElementById("qrCode").attr("src") shouldBe qrImage
      }
    }
  }

  "Given a user is logged in" when {
    "getQrCode() is called it" should {
      "return secureAccountSetupPage with secret from third party developer" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = FakeRequest().
          withLoggedIn(underTest)(sessionId)

        private val result = await(underTest.getQrCode()(request))

        status(result) shouldBe 200
        private val dom = Jsoup.parse(bodyOf(result))
        dom.getElementById("secret").html() shouldBe "abcd efgh"
        dom.getElementById("qrCode").attr("src") shouldBe qrImage
      }
    }

    "getProtectAccount() is called it" should {
      "return protect account page for user without MFA enabled" in new SetupUnprotectedAccount with LoggedIn {
        private val request = FakeRequest().
          withLoggedIn(underTest)(sessionId)

        private val result = await(addToken(underTest.getProtectAccount())(request))

        status(result) shouldBe OK
        bodyOf(result) should include("Protect your Developer Hub account by adding 2-step verification")
      }

      "return protected account page for user with MFA enabled" in new SetupProtectedAccount with LoggedIn {
        private val request = FakeRequest().
          withLoggedIn(underTest)(sessionId)

        private val result = await(addToken(underTest.getProtectAccount())(request))

        status(result) shouldBe OK
        bodyOf(result) should include(
          "Your Developer Hub account is currently protected with 2-step verification. This is linked to your smartphone or tablet.")

        bodyOf(result) should include("You must remove 2-step verification before you can add it to a new smartphone or tablet.")
      }
    }

    "protectAccount() is called it" should {
      "return error when access code in invalid format" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = await(addToken(underTest.protectAccount())(request))

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = await(addToken(underTest.protectAccount())(request))

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to getProtectAccountCompletedAction" in new SetupSuccessfulVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = await(addToken(underTest.protectAccount())(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ProtectAccount.getProtectAccountCompletedPage().url)

        verify(underTest.thirdPartyDeveloperConnector)
          .updateSessionLoggedInState(mockEq(sessionId), mockEq(UpdateLoggedInStateRequest(Some(LoggedInState.LOGGED_IN))))(any[HeaderCarrier])
      }
    }

    "removeMfa() is called it" should {
      "return error when totpCode in invalid format" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = await(addToken(underTest.remove2SV())(request))

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = await(addToken(underTest.remove2SV())(request))

        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to 2SV removal completed action" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = await(addToken(underTest.remove2SV())(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2SVRemovalCompletePage().url)
      }
    }
  }

  private def assertIncludesOneError(result: Result, message: String): Assertion = {

    val body = bodyOf(result)

    body should include(message)
    assert(Jsoup.parse(body).getElementsByClass("form-field--error").size == 1)
  }
}
