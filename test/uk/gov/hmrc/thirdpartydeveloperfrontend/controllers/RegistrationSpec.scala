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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import org.mockito.ArgumentCaptor
import views.html._

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.{BadRequestException, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.RegistrationSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock

class RegistrationSpec extends BaseControllerSpec {

  var userPassword = "Password1!"

  trait Setup extends SessionServiceMock {
    val registrationView            = app.injector.instanceOf[RegistrationView]
    val signInView                  = app.injector.instanceOf[SignInView]
    val accountVerifiedView         = app.injector.instanceOf[AccountVerifiedView]
    val expiredVerificationLinkView = app.injector.instanceOf[ExpiredVerificationLinkView]
    val confirmationView            = app.injector.instanceOf[ConfirmationView]
    val resendConfirmationView      = app.injector.instanceOf[ResendConfirmationView]

    val underTest = new Registration(
      sessionServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mockErrorHandler,
      mcc,
      cookieSigner,
      registrationView,
      signInView,
      accountVerifiedView,
      expiredVerificationLinkView,
      confirmationView,
      resendConfirmationView
    )

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
  }

  trait RequestWithSession {
    self: Setup =>
    val email            = "john.smith@example.com".toLaxEmail
    val newSessionParams = Seq(("email", email.text), sessionParams.head)
    val request          = FakeRequest().withSession(newSessionParams: _*)
  }

  "registration" should {
    "register with normalized firstname, lastname, email and organisation" in new Setup {
      val request = FakeRequest().withSession(sessionParams: _*).withFormUrlEncodedBody(
        ("firstname", "   first  "), // with whitespaces before and after
        ("lastname", "  last  "),    // with whitespaces before and after
        ("emailaddress", "email@example.com"),
        ("password", "VALID@1q2w3e"),
        ("confirmpassword", "VALID@1q2w3e"),
        ("organisation", "org")
      )

      val requestCaptor: ArgumentCaptor[developers.Registration] = ArgumentCaptor.forClass(classOf[developers.Registration])
      when(underTest.connector.register(requestCaptor.capture())(*)).thenReturn(successful(RegistrationSuccessful))

      val result = underTest.register()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/confirmation")

      requestCaptor.getValue.firstName shouldBe "first"
      requestCaptor.getValue.lastName shouldBe "last"
      requestCaptor.getValue.email.text shouldBe "email@example.com"
      requestCaptor.getValue.password shouldBe "VALID@1q2w3e"
      requestCaptor.getValue.organisation shouldBe Some("org")
    }
    "register with no organisation" in new Setup {
      val request = FakeRequest().withSession(sessionParams: _*).withFormUrlEncodedBody(
        ("firstname", "   first  "), // with whitespaces before and after
        ("lastname", "  last  "),    // with whitespaces before and after
        ("emailaddress", "email@example.com"),
        ("password", "VALID@1q2w3e"),
        ("confirmpassword", "VALID@1q2w3e")
      )

      val requestCaptor: ArgumentCaptor[developers.Registration] = ArgumentCaptor.forClass(classOf[developers.Registration])
      when(underTest.connector.register(requestCaptor.capture())(*)).thenReturn(successful(RegistrationSuccessful))

      await(underTest.register()(request))

      requestCaptor.getValue.organisation shouldBe None
    }
  }

  "registration verification" should {
    val code = "verificationCode"

    "redirect the user to login if their verification link matches an account" in new Setup {
      when(underTest.connector.verify(eqTo(code))(*)).thenReturn(successful(OK))
      val result = underTest.verify(code)(FakeRequest())

      status(result) shouldBe OK
      contentAsString(result) should include("Email address verified")
    }

    "invite user to register again when the verification link has expired" in new Setup {
      when(underTest.connector.verify(eqTo(code))(*)).thenReturn(failed(new BadRequestException("")))

      val result = underTest.verify(code)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Your account verification link has expired")
    }

    "redirect the user to confirmation page when resending verification" in new Setup with RequestWithSession {
      when(underTest.connector.resendVerificationEmail(eqTo(email))(*)).thenReturn(successful(NO_CONTENT))

      val result = underTest.resendVerification()(request)

      status(result) shouldBe SEE_OTHER
    }

    "show error page when resending verification fails" in new Setup with RequestWithSession {
      when(underTest.connector.resendVerificationEmail(eqTo(email))(*)).thenReturn(failed(UpstreamErrorResponse("Bang", NOT_FOUND)))

      val result = underTest.resendVerification()(request)

      status(result) shouldBe NOT_FOUND
    }
  }
}
