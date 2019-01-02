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
import domain.{Developer, Session}
import org.jsoup.Jsoup
import org.mockito.BDDMockito._
import org.mockito.Matchers.{any, eq => mockEq}
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.http.Status.{BAD_REQUEST, SEE_OTHER}
import play.api.mvc.Result
import play.filters.csrf.CSRF.TokenProvider
import qr.{OtpAuthUri, QRCode}
import service.{MFAResponse, MFAService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future

class ProtectAccountSpec extends UnitSpec with MockitoSugar with WithFakeApplication with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer

  trait Setup {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val loggedInUser = Developer("johnsmith@example.com", "John", "Doe")
    val qrImage = "qrImage"
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"

    val underTest = new ProtectAccount(
      mock[ThirdPartyDeveloperConnector],
      mock[OtpAuthUri],
      mock[MFAService],
      mock[SessionService],
      mock[ErrorHandler])(mock[ApplicationConfig]) {
      override val qrCode = mock[QRCode]
    }

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(Session(sessionId, loggedInUser))))

    def protectAccountRequest(code: String) = {
      FakeRequest().
        withLoggedIn(underTest)(sessionId).
        withSession("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken).
        withFormUrlEncodedBody("accessCode" -> code)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    given(underTest.connector.fetchDeveloper(mockEq(loggedInUser.email))(any[HeaderCarrier])).
      willReturn(Some(Developer(loggedInUser.email, "Bob", "Smith", None)))
  }

  trait SetupProtectedAccount extends Setup {
    given(underTest.connector.fetchDeveloper(mockEq(loggedInUser.email))(any[HeaderCarrier])).
      willReturn(Some(Developer(loggedInUser.email, "Bob", "Smith", None, Some(true))))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    given(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInUser.email)).willReturn(otpUri)
    given(underTest.qrCode.generateDataImageBase64(otpUri.toString)).willReturn(qrImage)
    given(underTest.connector.createMfaSecret(mockEq(loggedInUser.email))(any[HeaderCarrier])).willReturn(secret)
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

  "getQrCode" should {
    "return secureAccountSetupPage with secret from third party developer" in new SetupSuccessfulStart2SV {
      val request = FakeRequest().
        withLoggedIn(underTest)(sessionId)

      val result = await(underTest.getQrCode()(request))

      status(result) shouldBe 200
      val dom = Jsoup.parse(bodyOf(result))
      dom.getElementById("secret").html() shouldBe "abcd efgh"
      dom.getElementById("qrCode").attr("src") shouldBe qrImage
    }
  }

  "getProtectAccount" should {
    "return protect account page for user without MFA enabled" in new SetupUnprotectedAccount {
      val request = FakeRequest().
        withLoggedIn(underTest)(sessionId)

      val result = await(addToken(underTest.getProtectAccount())(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Protect your Developer Hub account by adding 2-step verification")
    }

    "return protected account page for user with MFA enabled" in new SetupProtectedAccount {
      val request = FakeRequest().
        withLoggedIn(underTest)(sessionId)

      val result = await(addToken(underTest.getProtectAccount())(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Your Developer account is currently protected with 2-step verification")
    }
  }

  "protectAccount" should {
    "return error when access code in invalid format" in new SetupSuccessfulStart2SV {
      val request = protectAccountRequest("abc")

      val result = await(addToken(underTest.protectAccount())(request))

      status(result) shouldBe BAD_REQUEST
      assertIncludesOneError(result, "You have entered an invalid access code")
    }

    "return error when verification fails" in new SetupFailedVerification {
      val request = protectAccountRequest(correctCode)

      val result = await(addToken(underTest.protectAccount())(request))

      status(result) shouldBe BAD_REQUEST
      assertIncludesOneError(result, "You have entered an incorrect access code")
    }

    "redirect to getProtectAccountCompletedAction" in new SetupSuccessfulVerification {
      val request = protectAccountRequest(correctCode)

      val result = await(addToken(underTest.protectAccount())(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ProtectAccount.getProtectAccountCompletedPage().url)
    }
  }

  "removeMfa" should {
    "return error when totpCode in invalid format" in new SetupSuccessfulRemoval {
      val request = protectAccountRequest("abc")

      val result = await(addToken(underTest.remove2SV())(request))

      status(result) shouldBe BAD_REQUEST
      assertIncludesOneError(result, "You have entered an invalid access code")
    }

    "return error when verification fails" in new SetupFailedRemoval {
      val request = protectAccountRequest(correctCode)

      val result = await(addToken(underTest.remove2SV())(request))

      assertIncludesOneError(result, "You have entered an incorrect access code")
    }

    "redirect to 2SV removal completed action" in new SetupSuccessfulRemoval {
      val request = protectAccountRequest(correctCode)

      val result = await(addToken(underTest.remove2SV())(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2SVRemovalCompletePage().url)
    }
  }

  def assertIncludesOneError(result: Result, message: String) = {

    val body = bodyOf(result)

    body should include(message)
    assert(Jsoup.parse(body).getElementsByClass("form-field--error").size == 1)
  }
}
