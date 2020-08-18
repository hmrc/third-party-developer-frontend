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

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import domain.{InvalidResetCode, UnverifiedAccount}
import domain.models.connectors.{ChangePassword, PasswordReset}
import mocks.service.SessionServiceMock
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import play.twirl.api.HtmlFormat
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class PasswordSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends SessionServiceMock {

    val mockConnector = mock[ThirdPartyDeveloperConnector]
    val mockAuditService = mock[AuditService]
    val mockAppConfig = mock[ApplicationConfig]

    val forgotPasswordView = app.injector.instanceOf[ForgotPasswordView]
    val checkEmailView = app.injector.instanceOf[CheckEmailView]
    val resetView = app.injector.instanceOf[ResetView]
    val resetInvalidView = app.injector.instanceOf[ResetInvalidView]
    val resetErrorView = app.injector.instanceOf[ResetErrorView]
    val signInView = app.injector.instanceOf[SignInView]

    val underTest = new Password(
      mock[AuditService],
      sessionServiceMock,
      mockConnector,
      mockErrorHandler,
      mcc,
      cookieSigner,
      forgotPasswordView,
      checkEmailView,
      resetView,
      resetInvalidView,
      resetErrorView,
      signInView
    )

    def mockRequestResetFor(email: String) =
      when(mockConnector.requestReset(eqTo(email))(any[HeaderCarrier]))
        .thenReturn(Future.successful(OK))

    def mockConnectorUnverifiedForReset(email: String, password: String) =
      when(mockConnector.reset(eqTo(PasswordReset(email, password)))(any[HeaderCarrier]))
        .thenReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForRequestReset(email: String) =
      when(mockConnector.requestReset(eqTo(email))(any[HeaderCarrier]))
        .thenReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForChangePassword(email: String, oldPassword: String, newPassword: String) =
      when(mockConnector.changePassword(eqTo(ChangePassword(email, oldPassword, newPassword)))(any[HeaderCarrier]))
        .thenReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForValidateReset(code: String) =
      when(mockConnector.fetchEmailForResetCode(eqTo(code))(any[HeaderCarrier]))
        .thenReturn(failed(new UnverifiedAccount))

    def mockConnectorInvalidResetCodeForValidateReset(code: String) =
      when(mockConnector.fetchEmailForResetCode(eqTo(code))(any[HeaderCarrier]))
        .thenReturn(failed(new InvalidResetCode))

    val emailFieldName = "emailaddress"
    val passwordFieldName = "password"
    val confirmPasswordFieldName = "confirmpassword"
    val currentPasswordFieldName = "currentpassword"
    val emailSessionName = "email"
    val developerEmail = "developer@example.com"
    val developerPassword = "$Pr4srs1234W0irddd1$"
    val developerCode = "developerCode"
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val request = FakeRequest().withSession(sessionParams: _*)
    val mockHeaderCarrier = mock[HeaderCarrier]
  }

  "password" should {

    "validate reset unverified user" in new Setup {
      mockConnectorUnverifiedForValidateReset(developerCode)
      val result = addToken(underTest.validateReset(developerEmail, developerCode))(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
      await(result).newFlash.get.get("error").mkString shouldBe "UnverifiedAccount"
    }

    "validate reset invalid reset code" in new Setup {
      mockConnectorInvalidResetCodeForValidateReset(developerCode)
      val result = addToken(underTest.validateReset(developerEmail, developerCode))(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
      await(result).newFlash.get.get("error").mkString shouldBe "InvalidResetCode"
    }

    "process password changed unverified user" in new Setup {
      mockConnectorUnverifiedForChangePassword(developerEmail, developerPassword, developerPassword)
      val requestWithPassword = request
        .withFormUrlEncodedBody((currentPasswordFieldName, developerPassword), (passwordFieldName, developerPassword), (confirmPasswordFieldName, developerPassword))
        .withSession((emailSessionName, developerEmail))
      val result =
        underTest.processPasswordChange(developerEmail, play.api.mvc.Results.Ok(HtmlFormat.empty), _ => HtmlFormat.empty)(requestWithPassword, mockHeaderCarrier, implicitly)
      status(result) shouldBe FORBIDDEN
      await(result).session(requestWithPassword).get("email").mkString shouldBe developerEmail
    }

    "request reset unverified user" in new Setup {
      mockConnectorUnverifiedForRequestReset(developerEmail)
      val requestWithPasswordAndEmail = request
        .withFormUrlEncodedBody((emailFieldName, developerEmail))
        .withSession((emailSessionName, developerEmail))
      val result = addToken(underTest.requestReset())(requestWithPasswordAndEmail)
      status(result) shouldBe FORBIDDEN
      await(result).session(requestWithPasswordAndEmail).get("email").mkString shouldBe developerEmail
    }

    "reset unverified user" in new Setup {
      mockConnectorUnverifiedForReset(developerEmail, developerPassword)
      val requestWithPasswordAndEmail = request
        .withFormUrlEncodedBody((passwordFieldName, developerPassword), (confirmPasswordFieldName, developerPassword))
        .withSession((emailSessionName, developerEmail))
      val result = addToken(underTest.resetPassword())(requestWithPasswordAndEmail)
      status(result) shouldBe FORBIDDEN
      await(result).session(requestWithPasswordAndEmail).get("email").mkString shouldBe developerEmail
    }

    "show the forgot password page" in new Setup {
      val result = addToken(underTest.showForgotPassword())(request)
      status(result) shouldBe OK
      contentAsString(result) should include("Reset your password")
    }

    "show the sent reset link page" in new Setup {
      mockRequestResetFor(developerEmail)
      val requestWithEmail = request.withFormUrlEncodedBody((emailFieldName, developerEmail))
      val result = addToken(underTest.requestReset())(requestWithEmail)
      status(result) shouldBe OK
      contentAsString(result) should include(s"We have sent an email to $developerEmail")
    }

    "show the reset password page with errors for unverified email" in new Setup {
      val requestWithunverifiedEmail = request.withFlash("error" -> "UnverifiedAccount", "email" -> developerEmail)
      val result = addToken(underTest.resetPasswordError())(requestWithunverifiedEmail)
      status(result) shouldBe FORBIDDEN
      contentAsString(result) should include("Reset your password")
      contentAsString(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
    }

    "show the reset password page with no errors" in new Setup {
      val requestWithVerifiedEmail = request.withSession("email" -> developerEmail)
      val result = addToken(underTest.resetPasswordChange())(requestWithVerifiedEmail)
      status(result) shouldBe OK
      contentAsString(result) should include("Create a new password")
    }

    "show the reset password error page for invalid reset code" in new Setup {
      val requestWithInvalidResetCode = request.withFlash("error" -> "InvalidResetCode")
      val result = addToken(underTest.resetPasswordError())(requestWithInvalidResetCode)
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Reset password link no longer valid")
      contentAsString(result) should include("Reset password again")
    }

    "redirect to an error page if no email is found to reset password" in new Setup {
      val result = addToken(underTest.resetPasswordChange())(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
    }

  }
}
