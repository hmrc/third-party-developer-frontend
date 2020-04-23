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

import java.util.UUID

import config.ErrorHandler
import domain._
import org.mockito.ArgumentMatchers.{any, eq => meq, _}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import play.api.libs.crypto.CookieSigner
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction._
import service._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future
import scala.concurrent.Future._

class UserLoginAccountSpec extends BaseControllerSpec with WithCSRFAddToken {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(UUID.randomUUID().toString, developer, LoggedInState.LOGGED_IN)
  val user = DeveloperSession(session)
  val sessionPartLoggedInEnablingMfa = Session(UUID.randomUUID().toString, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)
  val emailFieldName: String = "emailaddress"
  val passwordFieldName: String = "password"
  val userPassword = "Password1!"
  val totp = "123456"
  val nonce = "ABC-123"

  val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session))

  val userAuthenticationResponseWithMfaEnablementRequired = UserAuthenticationResponse(
    accessCodeRequired = false,
    nonce = None,
    session = Some(sessionPartLoggedInEnablingMfa))

  val userAuthenticationWith2SVResponse = UserAuthenticationResponse(
    accessCodeRequired = true,
    nonce = Some(nonce),
    session = None)

  trait Setup {
    private val daysRemaining = 10

    val mfaMandateService: MfaMandateService = mock[MfaMandateService]

    val underTest = new UserLoginAccount(mock[AuditService],
      mock[ErrorHandler],
      mock[SessionService],
      mock[ApplicationService],
      messagesApi,
      mfaMandateService,
      cookieSigner
    )
    when(mfaMandateService.daysTillAdminMfaMandate).thenReturn(Some(daysRemaining))
    when(mfaMandateService.showAdminMfaMandatedMessage(any())(any[HeaderCarrier])).thenReturn(true)

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    def mockAuthenticate(email: String, password: String, result: Future[UserAuthenticationResponse],
                         resultShowAdminMfaMandateMessage: Future[Boolean]): Unit = {

      given(underTest.sessionService.authenticate(meq(email), meq(password))(any[HeaderCarrier]))
        .willReturn(result)

      given(underTest.mfaMandateService.showAdminMfaMandatedMessage(meq(email))(any[HeaderCarrier]))
        .willReturn(resultShowAdminMfaMandateMessage)
    }

    def mockAuthenticateTotp(email: String, totp: String, nonce: String, result: Future[Session]): Unit =
      given(underTest.sessionService.authenticateTotp(meq(email), meq(totp), meq(nonce))(any[HeaderCarrier]))
        .willReturn(result)

    def mockLogout(): Unit =
      given(underTest.sessionService.destroy(meq(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(NO_CONTENT))

    given(underTest.sessionService.authenticate(anyString(), anyString())(any[HeaderCarrier])).willReturn(failed(new InvalidCredentials))
    given(underTest.sessionService.authenticateTotp(anyString(), anyString(), anyString())(any[HeaderCarrier])).willReturn(failed(new InvalidCredentials))

    def mockAudit(auditAction: AuditAction, result: Future[AuditResult]): Unit =
      given(underTest.auditService.audit(meq(auditAction), meq(Map.empty))(any[HeaderCarrier])).willReturn(result)
  }

  "authenticate" should {

    "display the 2-step verification code page when logging in with 2SV configured" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationWith2SVResponse), false)
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = await(addToken(underTest.authenticate())(request))

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.enterTotp().url)
    }

    "display the enter access code page" in new Setup {

      mockAuthenticate(user.email, userPassword, successful(userAuthenticationWith2SVResponse), false)
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)

      private val result = await(addToken(underTest.enterTotp())(request))

      status(result) shouldBe OK

      bodyOf(result) should include("Enter your access code")
    }

    "Redirect to the display the Add 2-step Verification suggestion page when successfully logging in without having 2SV configured" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponse), true)
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = await(underTest.authenticate()(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2svRecommendationPage.url)

      verify(underTest.auditService, times(1)).audit(
        meq(LoginSucceeded), meq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "display the Add 2-step Verification suggestion page when successfully logging in with not 2SV enabled and not 2SV configured" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponse), false)
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = await(underTest.authenticate()(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2svRecommendationPage.url)

      verify(underTest.auditService, times(1)).audit(
        meq(LoginSucceeded), meq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "display the 2-step protect account page when successfully logging in without having 2SV configured and is 2SV mandated" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponseWithMfaEnablementRequired), true)
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = await(underTest.authenticate()(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some("/developer/profile/protect-account")
    }

    "return the login page when the password is incorrect" in new Setup {

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, "wrongPassword1!"))
      private val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        meq(LoginFailedDueToInvalidPassword), meq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }

    "return the login page when the email has not been registered" in new Setup {
      private val unregisteredEmail = "unregistered@email.test"
      mockAuthenticate(unregisteredEmail, userPassword, failed(new InvalidEmail), false)

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, unregisteredEmail), (passwordFieldName, userPassword))
      private val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        meq(LoginFailedDueToInvalidEmail), meq(Map("developerEmail" -> unregisteredEmail)))(any[HeaderCarrier])
    }

    "return to the login page when the account is unverified" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new UnverifiedAccount), false)

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))
      private val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe FORBIDDEN
      bodyOf(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
      result.toString should include(user.email.replace("@", "%40"))
    }

    "display the Account locked page when the account is locked" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new LockedAccount), false)

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe LOCKED
      bodyOf(result) should include("You've entered details that do not match our records. Reset your password to sign in.")
      verify(underTest.auditService, times(1)).audit(
        meq(LoginFailedDueToLockedAccount), meq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }
  }

  "authenticateTotp" should {

    "return the manage Applications page when the credentials are correct" in new Setup {
      mockAuthenticateTotp(user.email, totp, nonce, successful(session))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams :+ "emailAddress" -> user.email :+ "nonce" -> nonce: _*)
        .withFormUrlEncodedBody(("accessCode", totp))

      private val result = await(underTest.authenticateTotp()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications")
      verify(underTest.auditService, times(1)).audit(
        meq(LoginSucceeded), meq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "return the login page when the access code is incorrect" in new Setup {
      private val request = FakeRequest()
        .withSession(sessionParams :+ "emailAddress" -> user.email :+ "nonce" -> nonce: _*)
        .withFormUrlEncodedBody(("accessCode", "654321"))

      private val result = await(addToken(underTest.authenticateTotp())(request))

      status(result) shouldBe UNAUTHORIZED
      bodyOf(result) should include("You have entered an incorrect access code")
      verify(underTest.auditService, times(1)).audit(
        meq(LoginFailedDueToInvalidAccessCode), meq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }
  }

  "2SVHelp" should {

    "return the remove 2SV confirmation page when user does not have an access code" in new Setup {

      private val request = FakeRequest().withSession(sessionParams: _*)

      private val result = await(addToken(underTest.get2SVHelpConfirmationPage())(request))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Get help accessing your account")
      body should include("We will remove 2-step verification so you can sign in to your account.")
    }

    "return the remove 2SV complete page when user selects yes" in new Setup {

      private val request = FakeRequest().withSession(sessionParams: _*)

      private val result = await(addToken(underTest.get2SVHelpCompletionPage())(request))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Request submitted")
      body should include("We have received your request to remove 2-step verification from your account")
    }

    "return 2-step removal request completed page on submission" in new Setup {

      private val request = FakeRequest().withSession(sessionParams :+ "emailAddress" -> user.email: _*)

      given(underTest.applicationService.request2SVRemoval(meq(user.email))(any[HeaderCarrier]))
        .willReturn(Future.successful(TicketCreated))

      private val result = await(addToken(underTest.confirm2SVHelp())(request))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("We have received your request to remove 2-step verification from your account")
      body should include("Request submitted")
      verify(underTest.applicationService).request2SVRemoval(meq(user.email))(any[HeaderCarrier])

    }
  }

  "accountLocked" should {
    "destroy session when locked" in new Setup {
      mockLogout()
      private val request = FakeRequest().withLoggedIn(underTest, implicitly)(session.sessionId)
      await(underTest.accountLocked()(request))
      verify(underTest.sessionService, atLeastOnce()).destroy(meq(session.sessionId))(any[HeaderCarrier])
    }
  }

  "login" should {
    "show the sign-in page" when {
      "Not logged in" in new Setup {
        private val request = FakeRequest()

        private val result = await(addToken(underTest.login())(request))

        status(result) shouldBe OK
        bodyOf(result) should include("Sign in")
      }

      "Part logged in" in new Setup {
        given(underTest.sessionService.fetch(meq(sessionPartLoggedInEnablingMfa.sessionId))(any[HeaderCarrier]))
          .willReturn(Future.successful(Some(sessionPartLoggedInEnablingMfa)))

        private val partLoggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(sessionPartLoggedInEnablingMfa.sessionId)
          .withSession(sessionParams: _*)

        private val result = await(addToken(underTest.login())(partLoggedInRequest))

        status(result) shouldBe OK
        bodyOf(result) should include("Sign in")
      }
    }

    "Redirect to the XXX page" when {
      "already logged in" in new Setup {
        given(underTest.sessionService.fetch(meq(session.sessionId))(any[HeaderCarrier]))
          .willReturn(Future.successful(Some(session)))

        private val loggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(session.sessionId)
          .withSession(sessionParams: _*)

        private val result = await(addToken(underTest.login())(loggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/applications")
      }
    }
  }
}
