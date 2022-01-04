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

package controllers

import java.net.URI

import builder.DeveloperBuilder
import config.ErrorHandler
import connectors.ThirdPartyDeveloperConnector
import domain.models.connectors.UpdateLoggedInStateRequest
import domain.models.developers.{LoggedInState, Session}
import mocks.service.SessionServiceMock
import org.jsoup.Jsoup
import org.scalatest.Assertion
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, SEE_OTHER}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import qr.{OtpAuthUri, QRCode}
import service.{MfaMandateService, MFAResponse, MFAService}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.protectaccount._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import controllers.profile.ProtectAccount
import domain.models.developers.UserId
import utils.LocalUserIdTracker

class ProtectAccountSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val loggedInDeveloper = buildDeveloper()
    val qrImage = "qrImage"
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"

    def loggedInState: LoggedInState

    val protectAccountSetupView = app.injector.instanceOf[ProtectAccountSetupView]
    val protectedAccountView = app.injector.instanceOf[ProtectedAccountView]
    val protectAccountView = app.injector.instanceOf[ProtectAccountView]
    val protectAccountAccessCodeView = app.injector.instanceOf[ProtectAccountAccessCodeView]
    val protectAccountCompletedView = app.injector.instanceOf[ProtectAccountCompletedView]
    val protectAccountRemovalConfirmationView = app.injector.instanceOf[ProtectAccountRemovalConfirmationView]
    val protectAccountRemovalAccessCodeView = app.injector.instanceOf[ProtectAccountRemovalAccessCodeView]
    val protectAccountRemovalCompleteView = app.injector.instanceOf[ProtectAccountRemovalCompleteView]

    val underTest: ProtectAccount = new ProtectAccount(
      mock[ThirdPartyDeveloperConnector],
      mock[OtpAuthUri],
      mock[MFAService],
      sessionServiceMock,
      mcc,
      mock[ErrorHandler],
      mock[MfaMandateService],
      cookieSigner,
      protectAccountSetupView,
      protectedAccountView,
      protectAccountView,
      protectAccountAccessCodeView,
      protectAccountCompletedView,
      protectAccountRemovalConfirmationView,
      protectAccountRemovalAccessCodeView,
      protectAccountRemovalCompleteView
    ) {
      override val qrCode: QRCode = mock[QRCode]
    }

    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, loggedInState))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    def protectAccountRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody("accessCode" -> code)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None))))
  }

  trait SetupProtectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaEnabled = Some(true)))))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    when(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInDeveloper.email)).thenReturn(otpUri)
    when(underTest.qrCode.generateDataImageBase64(otpUri.toString)).thenReturn(qrImage)
    when(underTest.thirdPartyDeveloperConnector.createMfaSecret(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(secret))
  }

  trait PartLogged extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
  }

  trait LoggedIn extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
  }

  trait SetupFailedVerification extends Setup {
    when(underTest.mfaService.enableMfa(any[UserId], any[String])(*)).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulVerification extends Setup {
    when(underTest.mfaService.enableMfa(eqTo(loggedInDeveloper.userId), eqTo(correctCode))(*)).thenReturn(Future.successful(MFAResponse(true)))
  }

  trait SetupFailedRemoval extends Setup {
    when(underTest.mfaService.removeMfa(any[UserId], any[String], any[String])(*)).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulRemoval extends Setup {
    when(underTest.mfaService.removeMfa(eqTo(loggedInDeveloper.userId), eqTo(loggedInDeveloper.email), eqTo(correctCode))(*)).thenReturn(Future.successful(MFAResponse(true)))
  }

  "Given a user is not logged in" when {
    "getQrCode() is called it" should {
      "redirect to the login page" in new SetupSuccessfulStart2SV with LoggedIn {

        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result = underTest.getQrCode()(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }
  }

  "Given a user is part logged in and enabling Mfa" when {
    "getQrCode() is called it" should {
      "return secureAccountSetupPage with secret from third party developer" in new SetupSuccessfulStart2SV with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.getQrCode()(request)

        status(result) shouldBe 200
        private val dom = Jsoup.parse(contentAsString(result))
        dom.getElementById("secret").html() shouldBe "abcd efgh"
        dom.getElementById("qrCode").attr("src") shouldBe qrImage
      }
    }
  }

  "Given a user is logged in" when {
    "getQrCode() is called it" should {
      "return secureAccountSetupPage with secret from third party developer" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.getQrCode()(request)

        status(result) shouldBe 200
        private val dom = Jsoup.parse(contentAsString(result))
        dom.getElementById("secret").html() shouldBe "abcd efgh"
        dom.getElementById("qrCode").attr("src") shouldBe qrImage
      }
    }

    "getProtectAccount() is called it" should {
      "return protect account page for user without MFA enabled" in new SetupUnprotectedAccount with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = addToken(underTest.getProtectAccount())(request)

        status(result) shouldBe OK
        contentAsString(result) should include("Set up 2-step verification to protect your Developer Hub account and application details from being compromised.")
      }

      "return protected account page for user with MFA enabled" in new SetupProtectedAccount with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = addToken(underTest.getProtectAccount())(request)

        status(result) shouldBe OK
        contentAsString(result) should include("Your Developer Hub account is currently protected with 2-step verification. This is linked to your smartphone or tablet.")

        contentAsString(result) should include("You must remove 2-step verification before you can add it to a new smartphone or tablet.")
      }
    }

    "protectAccount() is called it" should {
      "return error when access code in invalid format" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = addToken(underTest.protectAccount())(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.protectAccount())(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to getProtectAccountCompletedAction" in new SetupSuccessfulVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.protectAccount())(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.profile.routes.ProtectAccount.getProtectAccountCompletedPage().url)

        verify(underTest.thirdPartyDeveloperConnector)
          .updateSessionLoggedInState(eqTo(sessionId), eqTo(UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)))(*)
      }
    }

    "removeMfa() is called it" should {
      "return error when totpCode in invalid format" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = addToken(underTest.remove2SV())(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.remove2SV())(request)

        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to 2SV removal completed action" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.remove2SV())(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.profile.routes.ProtectAccount.get2SVRemovalCompletePage().url)
      }
    }
  }

  private def assertIncludesOneError(result: Future[Result], message: String): Assertion = {

    val body = contentAsString(result)

    body should include(message)
    assert(Jsoup.parse(body).getElementsByClass("govuk-form-group--error").size == 1)
  }
}
