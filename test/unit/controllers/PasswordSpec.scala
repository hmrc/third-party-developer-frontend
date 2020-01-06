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

package unit.controllers

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import controllers.{Password, routes}
import domain.{ChangePassword, InvalidResetCode, PasswordReset, UnverifiedAccount}
import org.mockito.ArgumentMatchers.{eq => meq, any}
import org.mockito.BDDMockito.given
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import play.twirl.api.HtmlFormat
import service.{AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class PasswordSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup {

    val mockConnector = mock[ThirdPartyDeveloperConnector]
    val mockSessionService = mock[SessionService]
    val mockAuditService = mock[AuditService]
    val mockAppConfig = mock[ApplicationConfig]

    val underTest = new Password(
      mock[AuditService],
      mock[SessionService],
      mockConnector,
      mockErrorHandler,
      messagesApi,
      mock[ApplicationConfig]
    )

    def mockRequestResetFor(email: String) =
      given(mockConnector.requestReset(meq(email))(any[HeaderCarrier]))
        .willReturn(Future.successful(OK))

    def mockConnectorUnverifiedForReset(email: String, password: String) =
      given(mockConnector.reset(meq(PasswordReset(email, password)))(any[HeaderCarrier]))
        .willReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForRequestReset(email: String) =
      given(mockConnector.requestReset(meq(email))(any[HeaderCarrier]))
        .willReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForChangePassword(email: String, oldPassword: String, newPassword: String) =
      given(mockConnector.changePassword(meq(ChangePassword(email, oldPassword, newPassword)))(any[HeaderCarrier]))
        .willReturn(failed(new UnverifiedAccount))

    def mockConnectorUnverifiedForValidateReset(code: String) =
      given(mockConnector.fetchEmailForResetCode(meq(code))(any[HeaderCarrier]))
        .willReturn(failed(new UnverifiedAccount))

    def mockConnectorInvalidResetCodeForValidateReset(code: String) =
      given(mockConnector.fetchEmailForResetCode(meq(code))(any[HeaderCarrier]))
        .willReturn(failed(new InvalidResetCode))

    val emailFieldName = "emailaddress"
    val passwordFieldName = "password"
    val confirmPasswordFieldName = "confirmpassword"
    val currentPasswordFieldName = "currentpassword"
    val emailSessionName = "email"
    val developerEmail = "developer@example.com"
    val developerPassword = "$Pr4srs1234W0irddd1$"
    val developerCode = "developerCode"
    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val request = FakeRequest().withSession(sessionParams: _*)
    val mockHeaderCarrier = mock[HeaderCarrier]
  }

  "password" should {

    "validate reset unverified user" in new Setup {
      mockConnectorUnverifiedForValidateReset(developerCode)
      val result = await(addToken(underTest.validateReset(developerEmail, developerCode))(request))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
      result.header.headers("Set-Cookie") should include("UnverifiedAccount")
    }

    "validate reset invalid reset code" in new Setup {
      mockConnectorInvalidResetCodeForValidateReset(developerCode)
      val result = await(addToken(underTest.validateReset(developerEmail, developerCode))(request))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
      result.header.headers("Set-Cookie") should include("InvalidResetCode")
    }

    "process password changed unverified user" in new Setup {
      mockConnectorUnverifiedForChangePassword(developerEmail, developerPassword, developerPassword)
      val requestWithPassword = request.withFormUrlEncodedBody((currentPasswordFieldName, developerPassword),
        (passwordFieldName, developerPassword), (confirmPasswordFieldName, developerPassword))
        .withSession((emailSessionName, developerEmail))
      val result = await(underTest.processPasswordChange(
        developerEmail, play.api.mvc.Results.Ok(HtmlFormat.empty), _ => HtmlFormat.empty)(requestWithPassword, mockHeaderCarrier, global))
      status(result) shouldBe FORBIDDEN
      result.toString should include(developerEmail.replace("@", "%40"))
    }

    "request reset unverified user" in new Setup {
      mockConnectorUnverifiedForRequestReset(developerEmail)
      val requestWithPasswordAndEmail = request.withFormUrlEncodedBody((emailFieldName, developerEmail))
        .withSession((emailSessionName, developerEmail))
      val result = await(addToken(underTest.requestReset())(requestWithPasswordAndEmail))
      status(result) shouldBe FORBIDDEN
      result.toString should include(developerEmail.replace("@", "%40"))
    }

    "reset unverified user" in new Setup {
      mockConnectorUnverifiedForReset(developerEmail, developerPassword)
      val requestWithPasswordAndEmail = request.withFormUrlEncodedBody((passwordFieldName, developerPassword), (confirmPasswordFieldName, developerPassword))
        .withSession((emailSessionName, developerEmail))
      val result = await(addToken(underTest.resetPassword())(requestWithPasswordAndEmail))
      status(result) shouldBe FORBIDDEN
      result.toString should include(developerEmail.replace("@", "%40"))
    }

    "show the forgot password page" in new Setup {
      val result = await(addToken(underTest.showForgotPassword())(request))
      status(result) shouldBe OK
      bodyOf(result) should include("Reset your password")
    }

    "show the sent reset link page" in new Setup {
      mockRequestResetFor(developerEmail)
      val requestWithEmail = request.withFormUrlEncodedBody((emailFieldName, developerEmail))
      val result = await(addToken(underTest.requestReset())(requestWithEmail))
      status(result) shouldBe OK
      bodyOf(result) should include(s"We have sent an email to $developerEmail")
    }

    "show the reset password page with errors for unverified email" in new Setup {
      val requestWithunverifiedEmail = request.withFlash("error" -> "UnverifiedAccount", "email" -> developerEmail)
      val result = await(addToken(underTest.resetPasswordError())(requestWithunverifiedEmail))
      status(result) shouldBe FORBIDDEN
      bodyOf(result) should include("Reset your password")
      bodyOf(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
    }

    "show the reset password page with no errors" in new Setup {
      val requestWithVerifiedEmail = request.withSession("email" -> developerEmail)
      val result = await(addToken(underTest.resetPasswordChange())(requestWithVerifiedEmail))
      status(result) shouldBe OK
      bodyOf(result) should include("Create a new password")
    }

    "show the reset password error page for invalid reset code" in new Setup {
      val requestWithInvalidResetCode = request.withFlash("error" -> "InvalidResetCode")
      val result = await(addToken(underTest.resetPasswordError())(requestWithInvalidResetCode))
      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("Reset password link no longer valid")
      bodyOf(result) should include("Reset password again")
    }

    "redirect to an error page if no email is found to reset password" in new Setup {
      val result = await(addToken(underTest.resetPasswordChange())(request))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) should be(Some(routes.Password.resetPasswordError().url))
    }

  }
}
