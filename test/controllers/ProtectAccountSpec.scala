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

package controllers

import java.net.URI

import config.ErrorHandler
import connectors.ThirdPartyDeveloperConnector
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
import service.{MFAResponse, MFAService, MfaMandateService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{Add2SVView, UserDidNotAdd2SVView}
import views.html.protectaccount._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import domain.models.developers.Developer
import domain.models.developers.LoggedInState
import domain.models.developers.Session
import domain.models.connectors.UpdateLoggedInStateRequest

import scala.concurrent.Future.successful

class ProtectAccountSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends SessionServiceMock {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val loggedInUser = Developer("johnsmith@example.com", "John", "Doe")
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
    val userDidNotAdd2SVView = app.injector.instanceOf[UserDidNotAdd2SVView]
    val add2SVView = app.injector.instanceOf[Add2SVView]

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
      protectAccountRemovalCompleteView,
      userDidNotAdd2SVView,
      add2SVView
    ) {
      override val qrCode: QRCode = mock[QRCode]
    }

    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInUser, loggedInState))

    def protectAccountRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody("accessCode" -> code)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInUser.email))(any[HeaderCarrier]))
      .thenReturn(successful(Some(Developer(loggedInUser.email, "Bob", "Smith", None))))
  }

  trait SetupProtectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInUser.email))(any[HeaderCarrier]))
      .thenReturn(successful(Some(Developer(loggedInUser.email, "Bob", "Smith", None, Some(true)))))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    when(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInUser.email)).thenReturn(otpUri)
    when(underTest.qrCode.generateDataImageBase64(otpUri.toString)).thenReturn(qrImage)
    when(underTest.thirdPartyDeveloperConnector.createMfaSecret(eqTo(loggedInUser.email))(any[HeaderCarrier]))
      .thenReturn(successful(secret))
  }

  trait PartLogged extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
  }

  trait LoggedIn extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
  }

  trait SetupFailedVerification extends Setup {
    when(underTest.mfaService.enableMfa(any[String], any[String])(any[HeaderCarrier])).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulVerification extends Setup {
    when(underTest.mfaService.enableMfa(eqTo(loggedInUser.email), eqTo(correctCode))(any[HeaderCarrier])).thenReturn(Future.successful(MFAResponse(true)))
  }

  trait SetupFailedRemoval extends Setup {
    when(underTest.mfaService.removeMfa(any[String], any[String])(any[HeaderCarrier])).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulRemoval extends Setup {
    when(underTest.mfaService.removeMfa(eqTo(loggedInUser.email), eqTo(correctCode))(any[HeaderCarrier])).thenReturn(Future.successful(MFAResponse(true)))
  }

  "Given a user is not logged in" when {
    "getQrCode() is called it" should {
      "redirect to the login page" in new SetupSuccessfulStart2SV with LoggedIn {

        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(any[HeaderCarrier]))
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
        redirectLocation(result) shouldBe Some(routes.ProtectAccount.getProtectAccountCompletedPage().url)

        verify(underTest.thirdPartyDeveloperConnector)
          .updateSessionLoggedInState(eqTo(sessionId), eqTo(UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)))(any[HeaderCarrier])
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
        redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2SVRemovalCompletePage().url)
      }
    }

    "Given a user with MFA enabled" when {
      "they have logged in when MFA is mandated in the future" should {
        "be shown the MFA recommendation with 10 days warning" in new LoggedIn {

          when(underTest.mfaMandateService.showAdminMfaMandatedMessage(*)(any[HeaderCarrier]))
            .thenReturn(Future.successful(true))

          private val daysInTheFuture = 10
          when(underTest.mfaMandateService.daysTillAdminMfaMandate)
            .thenReturn(Some(daysInTheFuture))

          private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

          private val result = underTest.get2svRecommendationPage()(request)

          status(result) shouldBe OK

          contentAsString(result) should include("Add 2-step verification")
          contentAsString(result) should include("If you are the Administrator of an application you have 10 days until 2-step verification is mandatory")

          verify(underTest.mfaMandateService).showAdminMfaMandatedMessage(eqTo(loggedInUser.email))(any[HeaderCarrier])
        }
      }
    }

    "they have logged in when MFA is mandated yet" should {
      "they have logged in when MFA is mandated is not configured" in new LoggedIn {
        when(underTest.mfaMandateService.showAdminMfaMandatedMessage(*)(any[HeaderCarrier]))
          .thenReturn(Future.successful(true))

        private val mfaMandateNotConfigured = None
        when(underTest.mfaMandateService.daysTillAdminMfaMandate)
          .thenReturn(mfaMandateNotConfigured)

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.get2svRecommendationPage()(request)

        status(result) shouldBe OK

        contentAsString(result) should include("Add 2-step verification")
        contentAsString(result) should include("Use 2-step verification to protect your Developer Hub account and application details from being compromised.")

        verify(underTest.mfaMandateService).showAdminMfaMandatedMessage(eqTo(loggedInUser.email))(any[HeaderCarrier])
      }
    }
  }

  private def assertIncludesOneError(result: Future[Result], message: String): Assertion = {

    val body = contentAsString(result)

    body should include(message)
    assert(Jsoup.parse(body).getElementsByClass("form-field--error").size == 1)
  }
}
