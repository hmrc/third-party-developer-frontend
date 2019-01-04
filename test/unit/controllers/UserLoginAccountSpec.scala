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

import java.util.UUID

import config.{ApplicationConfig, ErrorHandler}
import controllers._
import domain._
import org.mockito.BDDMockito.given
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction._
import service.{ApplicationService, AuditAction, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future
import scala.concurrent.Future._

class UserLoginAccountSpec extends UnitSpec with MockitoSugar with WithFakeApplication with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val user = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(UUID.randomUUID().toString, user)
  val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session))
  val emailFieldName: String = "emailaddress"
  val passwordFieldName: String = "password"
  val userPassword = "Password1!"
  val totp = "123456"
  val nonce = "ABC-123"

  trait Setup {

    val underTest = new UserLoginAccount(mock[AuditService],
      mock[ErrorHandler],
      mock[SessionService],
      mock[ApplicationService],
      mock[ApplicationConfig]
    )


    def mockAuthenticate(email: String, password: String, result: Future[UserAuthenticationResponse]) =
      given(underTest.sessionService.authenticate(Matchers.eq(email), Matchers.eq(password))(any[HeaderCarrier])).willReturn(result)

    def mockAuthenticateTotp(email: String, totp: String, nonce: String, result: Future[Session]) =
      given(underTest.sessionService.authenticateTotp(Matchers.eq(email), Matchers.eq(totp), Matchers.eq(nonce))(any[HeaderCarrier])).willReturn(result)

    def mockLogout() =
      given(underTest.sessionService.destroy(Matchers.eq(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(NO_CONTENT))

    def mockAudit(auditAction: AuditAction, result: Future[AuditResult]) =
      given(underTest.auditService.audit(Matchers.eq(auditAction), Matchers.eq(Map.empty))(any[HeaderCarrier])).willReturn(result)

    given(underTest.appConfig.isExternalTestEnvironment).willReturn(false)
    given(underTest.sessionService.authenticate(anyString(), anyString())(any[HeaderCarrier])).willReturn(failed(new InvalidCredentials))
    given(underTest.sessionService.authenticateTotp(anyString(), anyString(), anyString())(any[HeaderCarrier])).willReturn(failed(new InvalidCredentials))
    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
  }

  "authenticate" should {

    "return the manage Applications page when the credentials are correct" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponse))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      val request = FakeRequest().withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      val result = await(underTest.authenticate()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginSucceeded), Matchers.eq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "return the login page when the password is incorrect" in new Setup {

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, "wrongPassword1!"))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToInvalidPassword), Matchers.eq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }

    "return the login page when the email has not been registered" in new Setup {
      private val unregisteredEmail = "unregistered@email.test"
      mockAuthenticate(unregisteredEmail, userPassword, failed(new InvalidEmail))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, unregisteredEmail), (passwordFieldName, userPassword))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToInvalidEmail), Matchers.eq(Map("developerEmail" -> unregisteredEmail)))(any[HeaderCarrier])
    }

    "return to the login page when the account is unverified" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new UnverifiedAccount))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe FORBIDDEN
      bodyOf(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
      result.toString should include(user.email.replace("@", "%40"))
    }

    "return the login page when the account is locked" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new LockedAccount))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe LOCKED
      bodyOf(result) should include("You entered incorrect login details too many times you&#x27;ll now have to reset your password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToLockedAccount), Matchers.eq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }
  }

  "authenticateTotp" should {

    "return the manage Applications page when the credentials are correct" in new Setup {
      mockAuthenticateTotp(user.email, totp, nonce, successful(session))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      val request = FakeRequest().withSession(sessionParams: _*)
        .withFormUrlEncodedBody(("email", user.email), ("accessCode", totp), ("nonce", nonce))

      val result = await(underTest.authenticateTotp()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginSucceeded), Matchers.eq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "return the login page when the access code is incorrect" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(("email", user.email), ("accessCode", "654321"), ("nonce", nonce))
      val result = await(addToken(underTest.authenticateTotp())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("You have entered an incorrect access code")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToInvalidAccessCode), Matchers.eq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }
  }

  "2SVHelp" should {

    "return the remove 2SV confirmation page when user does not have an access code" in new Setup {

      val request = FakeRequest().withSession(sessionParams: _*)

      val result = await(addToken(underTest.get2SVHelpConfirmationPage())(request))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("You do not have an access code")
      body should include("You can ask us to remove 2-step verification so you can sign into your account")

    }

    "return the remove 2SV complete page when user selects yes" in new Setup {

      val request = FakeRequest().withSession(sessionParams: _*)

      val result = await(addToken(underTest.get2SVHelpCompletionPage())(request))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("Request submitted")
      body should include("You have requested to remove 2-step verification from your account")

    }

      "redirect to the login page when user selects no" in new Setup {

      val request = FakeRequest().withSession(sessionParams: _*).
        withFormUrlEncodedBody(("helpRemoveConfirm", "No"))

      val result = await(addToken(underTest.confirm2SVHelp())(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "return 2-step verification request completed page when yes selected" in new Setup {

      val request = FakeRequest().withSession(sessionParams :+ "emailAddress" -> user.email :_*).
        withFormUrlEncodedBody(("helpRemoveConfirm", "Yes"))

      given(underTest.applicationService.request2SVRemoval(Matchers.eq(user.email))(any[HeaderCarrier]))
        .willReturn(Future.successful(TicketCreated))

      val result = await(addToken(underTest.confirm2SVHelp())(request))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("You have requested to remove 2-step verification from your account")
      body should include("Request submitted")
      verify(underTest.applicationService).request2SVRemoval(Matchers.eq(user.email))(any[HeaderCarrier])

    }
  }

  "accountLocked" should {
    "destroy session when locked" in new Setup {
      mockLogout()
      val request = FakeRequest().withLoggedIn(underTest)(session.sessionId)
      await(underTest.accountLocked()(request))
      verify(underTest.sessionService, atLeastOnce()).destroy(Matchers.eq(session.sessionId))(Matchers.any[HeaderCarrier])
    }
  }
}
