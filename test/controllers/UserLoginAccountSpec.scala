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

import java.util.UUID

import builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import domain._
import domain.models.connectors.{TicketCreated, UserAuthenticationResponse}
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import mocks.service.SessionServiceMock
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction._
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{AccountLockedView, LogInAccessCodeView, SignInView}
import views.html.protectaccount.{ProtectAccountNoAccessCodeCompleteView, ProtectAccountNoAccessCodeView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import views.html.UserDidNotAdd2SVView
import views.html.Add2SVView
import domain.models.developers.UserId
import utils.LocalUserIdTracker
import mocks.connector.ThirdPartyDeveloperConnectorMockModule

class UserLoginAccountSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {
  trait Setup extends SessionServiceMock with ThirdPartyDeveloperConnectorMockModule {
    val developer = buildDeveloper()
    val session = Session(UUID.randomUUID().toString, developer, LoggedInState.LOGGED_IN)
    val user = DeveloperSession(session)
    val emailFieldName: String = "emailaddress"
    val passwordFieldName: String = "password"
    val userPassword = "Password1!"
    val totp = "123456"
    val nonce = "ABC-123"

    private val daysRemaining = 10

    val sessionId = "sessionId"
    val loggedInDeveloper = buildDeveloper()

    val mfaMandateService: MfaMandateService = mock[MfaMandateService]

    val signInView = app.injector.instanceOf[SignInView]
    val accountLockedView = app.injector.instanceOf[AccountLockedView]
    val logInAccessCodeView = app.injector.instanceOf[LogInAccessCodeView]
    val protectAccountNoAccessCodeView = app.injector.instanceOf[ProtectAccountNoAccessCodeView]
    val protectAccountNoAccessCodeCompleteView = app.injector.instanceOf[ProtectAccountNoAccessCodeCompleteView]
    val userDidNotAdd2SVView = app.injector.instanceOf[UserDidNotAdd2SVView]
    val add2SVView = app.injector.instanceOf[Add2SVView]

    val underTest = new UserLoginAccount(
      mock[AuditService],
      mock[ErrorHandler],
      mock[ApplicationService],
      mock[SubscriptionFieldsService],
      TPDMock.aMock,
      sessionServiceMock,
      mcc,
      mfaMandateService,
      cookieSigner,
      signInView,
      accountLockedView,
      logInAccessCodeView,
      protectAccountNoAccessCodeView,
      protectAccountNoAccessCodeCompleteView,
      userDidNotAdd2SVView,
      add2SVView
    )

    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    when(mfaMandateService.daysTillAdminMfaMandate).thenReturn(Some(daysRemaining))
    when(mfaMandateService.showAdminMfaMandatedMessage(*[UserId])(*)).thenReturn(successful(true))

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    def mockAuthenticate(email: String, password: String, result: Future[UserAuthenticationResponse],
                         resultShowAdminMfaMandateMessage: Future[Boolean]): Unit = {

      when(underTest.sessionService.authenticate(eqTo(email), eqTo(password))(*))
        .thenReturn(result)

      when(underTest.mfaMandateService.showAdminMfaMandatedMessage(*[UserId])(*))
        .thenReturn(resultShowAdminMfaMandateMessage)
    }

    def mockAuthenticateTotp(email: String, totp: String, nonce: String, result: Future[Session]): Unit =
      when(underTest.sessionService.authenticateTotp(eqTo(email), eqTo(totp), eqTo(nonce))(*))
        .thenReturn(result)

    def mockLogout(): Unit =
      when(underTest.sessionService.destroy(eqTo(session.sessionId))(*))
        .thenReturn(Future.successful(NO_CONTENT))

    when(underTest.sessionService.authenticate(*, *)(*)).thenReturn(failed(new InvalidCredentials))
    when(underTest.sessionService.authenticateTotp(*, *, *)(*)).thenReturn(failed(new InvalidCredentials))

    def mockAudit(auditAction: AuditAction, result: Future[AuditResult]): Unit =
      when(underTest.auditService.audit(eqTo(auditAction), eqTo(Map.empty))(*)).thenReturn(result)
  }

  trait SetupWithUserAuthenticationResponse extends Setup {
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session))
  }

  trait SetupWithuserAuthenticationResponseWithMfaEnablementRequired extends Setup {
    val sessionPartLoggedInEnablingMfa = Session(UUID.randomUUID().toString, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

    val userAuthenticationResponseWithMfaEnablementRequired = UserAuthenticationResponse(
        accessCodeRequired = false,
        nonce = None,
        session = Some(sessionPartLoggedInEnablingMfa))
  }

  trait SetupWithuserAuthenticationWith2SVResponse extends Setup {
    val userAuthenticationWith2SVResponse = UserAuthenticationResponse(
      accessCodeRequired = true,
      nonce = Some(nonce),
      session = None)
  }

  trait PartLogged extends Setup {
    def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, loggedInState))
  }

  trait LoggedIn extends Setup {
    def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, loggedInState))
  }
  
  "authenticate" should {

    "display the 2-step verification code page when logging in with 2SV configured" in new SetupWithuserAuthenticationWith2SVResponse {
      mockAuthenticate(user.developer.email, userPassword, successful(userAuthenticationWith2SVResponse), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.enterTotp().url)
    }

    "display the enter access code page" in new SetupWithuserAuthenticationWith2SVResponse {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationWith2SVResponse), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)

      private val result = addToken(underTest.enterTotp())(request)

      status(result) shouldBe OK

      contentAsString(result) should include("Enter your access code")
    }

    "Redirect to the display the Add 2-step Verification suggestion page when successfully logging in without having 2SV configured" in new SetupWithUserAuthenticationResponse {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponse), successful(true))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.get2svRecommendationPage.url)

      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded), eqTo(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(*)
    }

    "display the Add 2-step Verification suggestion page when successfully logging in with not 2SV enabled and not 2SV configured" in new SetupWithUserAuthenticationResponse {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponse), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.get2svRecommendationPage.url)

      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded), eqTo(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(*)
    }

    "display the 2-step protect account page when successfully logging in without having 2SV configured and is 2SV mandated" in new SetupWithuserAuthenticationResponseWithMfaEnablementRequired {
      mockAuthenticate(user.email, userPassword, successful(userAuthenticationResponseWithMfaEnablementRequired), successful(true))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some("/developer/profile/protect-account")
    }

    "return the login page when the password is incorrect" in new Setup {

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, "wrongPassword1!"))
      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidPassword), eqTo(Map("developerEmail" -> user.email)))(*)
    }

    "return the login page when the email has not been registered" in new Setup {
      private val unregisteredEmail = "unregistered@email.test"
      mockAuthenticate(unregisteredEmail, userPassword, failed(new InvalidEmail), successful(false))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, unregisteredEmail), (passwordFieldName, userPassword))
      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidEmail), eqTo(Map("developerEmail" -> unregisteredEmail)))(*)
    }

    "return to the login page when the account is unverified" in new Setup {
      mockAuthenticate(user.developer.email, userPassword, failed(new UnverifiedAccount), successful(false))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))
      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe FORBIDDEN
      contentAsString(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
      await(result).session(request).get("email").mkString shouldBe user.email
    }

    "display the Account locked page when the account is locked" in new Setup {
      mockAuthenticate(user.developer.email, userPassword, failed(new LockedAccount), successful(false))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe LOCKED
      contentAsString(result) should include("You've entered details that do not match our records. Reset your password to sign in.")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToLockedAccount), eqTo(Map("developerEmail" -> user.email)))(*)
    }
  }

  "authenticateTotp" should {

    "return the manage Applications page when the credentials are correct" in new Setup {
      mockAuthenticateTotp(user.email, totp, nonce, successful(session))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams :+ "emailAddress" -> user.email :+ "nonce" -> nonce: _*)
        .withFormUrlEncodedBody(("accessCode", totp))

      private val result = underTest.authenticateTotp()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded), eqTo(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(*)
    }

    "return the login page when the access code is incorrect" in new Setup {
      private val request = FakeRequest()
        .withSession(sessionParams :+ "emailAddress" -> user.email :+ "nonce" -> nonce: _*)
        .withFormUrlEncodedBody(("accessCode", "654321"))

      private val result =  addToken(underTest.authenticateTotp())(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("You have entered an incorrect access code")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidAccessCode), eqTo(Map("developerEmail" -> user.email)))(*)
    }
  }

  "2SVHelp" should {

    "return the remove 2SV confirmation page when user does not have an access code" in new Setup {

      private val request = FakeRequest().withSession(sessionParams: _*)

      private val result = addToken(underTest.get2SVHelpConfirmationPage())(request)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Get help accessing your account")
      body should include("We will remove 2-step verification so you can sign in to your account.")
    }

    "return the remove 2SV complete page when user selects yes" in new Setup {

      private val request = FakeRequest().withSession(sessionParams: _*)

      private val result = addToken(underTest.get2SVHelpCompletionPage())(request)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Request submitted")
      body should include("We have received your request to remove 2-step verification from your account")
    }

    "return 2-step removal request completed page on submission" in new Setup {

      private val request = FakeRequest().withSession(sessionParams :+ "emailAddress" -> user.email: _*)

      val userId = UserId.random
      TPDMock.FindUserId.thenReturn(user.email)(userId)
      TPDMock.FetchDeveloper.thenReturn(userId)(developer)
      when(underTest.applicationService.request2SVRemoval(*, eqTo(user.email))(*))
        .thenReturn(Future.successful(TicketCreated))

      private val result = addToken(underTest.confirm2SVHelp())(request)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("We have received your request to remove 2-step verification from your account")
      body should include("Request submitted")
      verify(underTest.applicationService).request2SVRemoval(*, eqTo(user.email))(*)

    }
  }

  "accountLocked" should {
    "destroy session when locked" in new Setup {
      mockLogout()
      private val request = FakeRequest().withLoggedIn(underTest, implicitly)(session.sessionId)
      await(underTest.accountLocked()(request))
      verify(underTest.sessionService, atLeastOnce).destroy(eqTo(session.sessionId))(*)
    }
  }

  "login" should {
    "show the sign-in page" when {
      "Not logged in" in new Setup {
        private val request = FakeRequest()

        private val result = addToken(underTest.login())(request)

        status(result) shouldBe OK
        contentAsString(result) should include("Sign in")
      }

      "Part logged in" in new SetupWithuserAuthenticationResponseWithMfaEnablementRequired {
        when(underTest.sessionService.fetch(eqTo(sessionPartLoggedInEnablingMfa.sessionId))(*))
          .thenReturn(Future.successful(Some(sessionPartLoggedInEnablingMfa)))

        private val partLoggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(sessionPartLoggedInEnablingMfa.sessionId)
          .withSession(sessionParams: _*)

        private val result = addToken(underTest.login())(partLoggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Sign in")
      }
    }

    "Redirect to the XXX page" when {
      "already logged in" in new Setup {
        when(underTest.sessionService.fetch(eqTo(session.sessionId))(*))
          .thenReturn(Future.successful(Some(session)))

        private val loggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(session.sessionId)
          .withSession(sessionParams: _*)

        private val result = addToken(underTest.login())(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/applications")
      }
    }

    "Given a user with MFA enabled" when {
      "they have logged in when MFA is mandated in the future" should {
        "be shown the MFA recommendation with 10 days warning" in new LoggedIn {
          when(underTest.mfaMandateService.showAdminMfaMandatedMessage(*[UserId])(*))
            .thenReturn(Future.successful(true))

          private val daysInTheFuture = 10
          when(underTest.mfaMandateService.daysTillAdminMfaMandate)
            .thenReturn(Some(daysInTheFuture))

          private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

          private val result = underTest.get2svRecommendationPage()(request)

          status(result) shouldBe OK

          contentAsString(result) should include("Add 2-step verification")
          contentAsString(result) should include("If you are the Administrator of an application you have 10 days until 2-step verification is mandatory")

          verify(underTest.mfaMandateService).showAdminMfaMandatedMessage(eqTo(loggedInDeveloper.userId))(*)
        }
      }
    }

    "they have logged in when MFA is mandated yet" should {
      "they have logged in when MFA is mandated is not configured" in new LoggedIn {
        when(underTest.mfaMandateService.showAdminMfaMandatedMessage(*[UserId])(*))
          .thenReturn(Future.successful(true))

        private val mfaMandateNotConfigured = None
        when(underTest.mfaMandateService.daysTillAdminMfaMandate)
          .thenReturn(mfaMandateNotConfigured)

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.get2svRecommendationPage()(request)

        status(result) shouldBe OK

        contentAsString(result) should include("Add 2-step verification")
        contentAsString(result) should include("Use 2-step verification to protect your Developer Hub account and application details from being compromised.")

        verify(underTest.mfaMandateService).showAdminMfaMandatedMessage(eqTo(loggedInDeveloper.userId))(*)
      }
    }
  }
}
