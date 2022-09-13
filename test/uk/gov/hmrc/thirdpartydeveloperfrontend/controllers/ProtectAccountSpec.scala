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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.net.URI
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, MfaDetailBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UpdateLoggedInStateRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import org.jsoup.Jsoup
import org.scalatest.Assertion
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, SEE_OTHER}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.RegisterAuthAppResponse
import uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.ProtectAccount
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.service.{MFAResponse, MFAService, MfaMandateService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html.protectaccount._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker

class ProtectAccountSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker with MfaDetailBuilder {

  trait Setup extends SessionServiceMock {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val mfaId = verifiedAuthenticatorAppMfaDetail.id
    val loggedInDeveloper = buildDeveloper()
    val qrImage = "qrImage"
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"

    def loggedInState: LoggedInState

    val protectAccountSetupView = app.injector.instanceOf[ProtectAccountSetupView]
    val protectAccountView = app.injector.instanceOf[ProtectAccountView]
    val protectAccountAccessCodeView = app.injector.instanceOf[ProtectAccountAccessCodeView]
    val protectAccountCompletedView = app.injector.instanceOf[ProtectAccountCompletedView]
    val protectedAccountWithMfaView = app.injector.instanceOf[ProtectedAccountWithMfaView]
    val protectedAccountMfaRemovalByIdAccessCodeView = app.injector.instanceOf[ProtectedAccountMfaRemovalByIdAccessCodeView]
    val protectedAccountRemovalCompleteView = app.injector.instanceOf[ProtectedAccountRemovalCompleteView]

    val underTest: ProtectAccount = new ProtectAccount(
      mock[ThirdPartyDeveloperConnector],
      mock[ThirdPartyDeveloperMfaConnector],
      mock[OtpAuthUri],
      mock[MFAService],
      sessionServiceMock,
      mcc,
      mock[ErrorHandler],
      mock[MfaMandateService],
      cookieSigner,
      protectAccountSetupView,
      protectAccountView,
      protectAccountAccessCodeView,
      protectAccountCompletedView,
      protectedAccountWithMfaView,
      protectedAccountMfaRemovalByIdAccessCodeView,
      protectedAccountRemovalCompleteView
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
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedAuthenticatorAppMfaDetail))))
      )
  }

  trait SetupSuccessfulStart2SV extends Setup {
    val registerAuthAppResponse = RegisterAuthAppResponse(secret, mfaId)

    when(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInDeveloper.email)).thenReturn(otpUri)
    when(underTest.qrCode.generateDataImageBase64(otpUri.toString)).thenReturn(qrImage)
    when(underTest.thirdPartyDeveloperMfaConnector.createMfaSecret(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(registerAuthAppResponse))
  }

  trait PartLogged extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
  }

  trait LoggedIn extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
  }

  trait SetupFailedVerification extends Setup {
    when(underTest.mfaService.enableMfa(any[UserId], any[MfaId], any[String])(*)).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulVerification extends Setup {
    when(underTest.mfaService.enableMfa(eqTo(loggedInDeveloper.userId), eqTo(mfaId), eqTo(correctCode))(*)).thenReturn(Future.successful(MFAResponse(true)))
  }

  trait SetupFailedRemoval extends Setup {
    when(underTest.mfaService.removeMfaById(any[UserId], any[MfaId], any[String])(*)).thenReturn(Future.successful(MFAResponse(false)))
  }

  trait SetupSuccessfulRemoval extends Setup {
    when(underTest.mfaService.removeMfaById(eqTo(loggedInDeveloper.userId), eqTo(mfaId), eqTo(correctCode))(*)).thenReturn(Future.successful(MFAResponse(true)))
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
        contentAsString(result) should include("Account protection")
        contentAsString(result) should include("This is how you get your access codes.")
      }
    }

    "protectAccount() is called it" should {
      "return error when access code in invalid format" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = addToken(underTest.protectAccount(mfaId))(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.protectAccount(mfaId))(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to getProtectAccountCompletedAction" in new SetupSuccessfulVerification with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.protectAccount(mfaId))(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.ProtectAccount.getProtectAccountCompletedPage().url)

        verify(underTest.thirdPartyDeveloperConnector)
          .updateSessionLoggedInState(eqTo(sessionId), eqTo(UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)))(*)
      }
    }

    "get2SVRemovalByIdAccessCodePage() is called it" should {
      "return 2SV removal access code page" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = addToken(underTest.get2SVRemovalByIdAccessCodePage(mfaId))(request)

        status(result) shouldBe OK
        contentAsString(result) should include("This is the 6 digit code from your authentication app")
      }
    }

    "remove2SVById() is called it" should {
      "return error when totpCode in invalid format" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest("abc")

        private val result = addToken(underTest.remove2SVById(mfaId))(request)

        status(result) shouldBe BAD_REQUEST
        assertIncludesOneError(result, "You have entered an invalid access code")
      }

      "return error when verification fails" in new SetupFailedRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.remove2SVById(mfaId))(request)

        assertIncludesOneError(result, "You have entered an incorrect access code")
      }

      "redirect to 2SV removal completed action" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.remove2SVById(mfaId))(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.ProtectAccount.get2SVRemovalByIdCompletePage().url)
      }
    }

    "get2SVRemovalByIdCompletePage() is called it" should {
      "return protect account removal complete view" in new SetupSuccessfulRemoval with LoggedIn {
        private val request = protectAccountRequest(correctCode)

        private val result = addToken(underTest.get2SVRemovalByIdCompletePage())(request)

        status(result) shouldBe OK
        contentAsString(result) should include("You've removed this security preference")
      }
    }
  }

  private def assertIncludesOneError(result: Future[Result], message: String): Assertion = {
    val body = contentAsString(result)

    body should include(message)
    assert(Jsoup.parse(body).getElementsByClass("govuk-form-group--error").size == 1)
  }
}
