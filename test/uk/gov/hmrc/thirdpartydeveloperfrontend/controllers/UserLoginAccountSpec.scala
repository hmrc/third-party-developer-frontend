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

import java.time.Period
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

import _root_.uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ThirdPartyDeveloperConnectorMockModule, ThirdPartyDeveloperMfaConnectorMockModule}
import views.html.{AccountLockedView, Add2SVView, SelectLoginMfaView, SignInView, UserDidNotAdd2SVView}

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptionsData
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{UserId, _}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppLoginAccessCodeView
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsLoginAccessCodeView
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RequestMfaRemovalCompleteView, RequestMfaRemovalView}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaType.{AUTHENTICATOR_APP, SMS}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{DeviceSessionId, MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.UserAuthenticationResponse
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.{MfaDetailBuilder, UserBuilder}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.AppsByTeamMemberServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.CookieEncoding
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WithCSRFAddToken}

class UserLoginAccountSpec extends BaseControllerSpec with WithCSRFAddToken
    with UserBuilder with LocalUserIdTracker with CollaboratorTracker with CookieEncoding with MfaDetailBuilder {

  trait Setup extends ThirdPartyDeveloperConnectorMockModule with ThirdPartyDeveloperMfaConnectorMockModule with AppsByTeamMemberServiceMock {

    val developerWithAuthAppMfa                      = buildTrackedUser(mfaDetails = List(verifiedAuthenticatorAppMfaDetail))
    val developerWithSmsMfa                          = buildTrackedUser(mfaDetails = List(verifiedSmsMfaDetail))
    val developerWithAuthAppAndSmsMfa                = buildTrackedUser(mfaDetails = List(verifiedAuthenticatorAppMfaDetail, verifiedSmsMfaDetail))
    val authAppMfaId                                 = verifiedAuthenticatorAppMfaDetail.id
    val smsMfaId                                     = verifiedSmsMfaDetail.id
    val sessionWithAuthAppMfa                        = UserSession(UserSessionId.random, LoggedInState.LOGGED_IN, developerWithAuthAppMfa)
    val sessionWithSmsMfa                            = UserSession(UserSessionId.random, LoggedInState.LOGGED_IN, developerWithSmsMfa)
    val sessionContainsDeveloperWithAuthAppAndSmsMfa = sessionWithAuthAppMfa.copy(developer = developerWithAuthAppAndSmsMfa)
    val emailFieldName: String                       = "emailaddress"
    val passwordFieldName: String                    = "password"
    val userPassword                                 = "Password1!"
    val accessCode                                   = "123456"
    val nonce                                        = "ABC-123"

    val deviceSessionId     = DeviceSessionId.random
    val deviceSessionCookie = createDeviceCookie(deviceSessionId.toString)

    val sessionId         = UserSessionId.random
    val loggedInDeveloper = buildTrackedUser()

    val signInView                    = app.injector.instanceOf[SignInView]
    val accountLockedView             = app.injector.instanceOf[AccountLockedView]
    val authApploginAccessCodeView    = app.injector.instanceOf[AuthAppLoginAccessCodeView]
    val smsLoginAccessCodeView        = app.injector.instanceOf[SmsLoginAccessCodeView]
    val selectLoginMfaView            = app.injector.instanceOf[SelectLoginMfaView]
    val requestMfaRemovalView         = app.injector.instanceOf[RequestMfaRemovalView]
    val requestMfaRemovalCompleteView = app.injector.instanceOf[RequestMfaRemovalCompleteView]
    val userDidNotAdd2SVView          = app.injector.instanceOf[UserDidNotAdd2SVView]
    val add2SVView                    = app.injector.instanceOf[Add2SVView]

    val errorHandler = app.injector.instanceOf[ErrorHandler]

    val underTest = new UserLoginAccount(
      mock[AuditService],
      errorHandler,
      mock[ApplicationService],
      mock[SubscriptionFieldsService],
      TPDMock.aMock,
      sessionServiceMock,
      TPDMFAMock.aMock,
      mcc,
      appsByTeamMemberServiceMock,
      cookieSigner,
      signInView,
      accountLockedView,
      authApploginAccessCodeView,
      smsLoginAccessCodeView,
      selectLoginMfaView,
      requestMfaRemovalView,
      requestMfaRemovalCompleteView,
      userDidNotAdd2SVView,
      add2SVView
    )

    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val applicationId       = ApplicationId.random
    val clientId            = ClientId("myClientId")
    val grantLength: Period = Period.ofDays(547)

    val applicationsWhereUserIsAdminInProduction =
      Seq(
        ApplicationWithSubscriptionsData.one
      )

    val sessionParams: Seq[(String, String)] = Seq(
      "userId"    -> sessionWithAuthAppMfa.developer.userId.value.toString,
      "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    )

    val testRequest = FakeRequest()
      .withSession(sessionParams
        :+ "userId"       -> sessionWithAuthAppMfa.developer.userId.value.toString
        :+ "emailAddress" -> sessionWithAuthAppMfa.developer.email.text :+ "nonce" -> nonce: _*)

    def mockAuthenticate(email: LaxEmailAddress, password: String, result: Future[UserAuthenticationResponse], resultShowAdminMfaMandateMessage: Future[Boolean]): Unit = {

      when(underTest.sessionService.authenticate(eqTo(email), eqTo(password), eqTo(Some(deviceSessionId)))(*))
        .thenReturn(result.map(x => (x, sessionWithAuthAppMfa.developer.userId)))
    }

    def mockAuthenticateAccessCode(email: LaxEmailAddress, accessCode: String, nonce: String, mfaId: MfaId, result: Future[UserSession]): Unit =
      when(underTest.sessionService.authenticateAccessCode(eqTo(email), eqTo(accessCode), eqTo(nonce), eqTo(mfaId))(*))
        .thenReturn(result)

    def mockLogout(): Unit =
      when(underTest.sessionService.destroy(eqTo(sessionWithAuthAppMfa.sessionId))(*))
        .thenReturn(Future.successful(NO_CONTENT))

    when(underTest.sessionService.authenticate(*[LaxEmailAddress], *, *)(*)).thenReturn(failed(new InvalidCredentials))
    when(underTest.sessionService.authenticateAccessCode(*[LaxEmailAddress], *, *, *[MfaId])(*)).thenReturn(failed(new InvalidCredentials))

    def mockAudit(auditAction: AuditAction, result: Future[AuditResult]): Unit =
      when(underTest.auditService.audit(eqTo(auditAction), *)(*)).thenReturn(result)
  }

  trait SetupWithUserAuthRespRequiringMfaAndMfaEnabled extends Setup {
    val userAuthRespNotRequiringMfaAndMfaEnabled = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled = true, session = Some(sessionWithAuthAppMfa))
  }

  trait SetupWithUserAuthRespNotRequiringMfa extends Setup {
    val userAuthRespNotRequiringMfa = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled = false, session = Some(sessionWithAuthAppMfa))

    def testWhenMfaMandatedIs(mfaMandated: Boolean) = {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespNotRequiringMfa), resultShowAdminMfaMandateMessage = successful(mfaMandated))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.get2svRecommendationPage().url)

      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
    }
  }

  trait SetupWithUserAuthResRequiringMfaEnablement extends Setup {
    val sessionPartLoggedInEnablingMfa = UserSession(UserSessionId.random, LoggedInState.PART_LOGGED_IN_ENABLING_MFA, developerWithAuthAppMfa)

    val userAuthRespRequiringMfaEnablement = UserAuthenticationResponse(
      accessCodeRequired = false,
      mfaEnabled = false,
      session = Some(sessionPartLoggedInEnablingMfa)
    )
  }

  trait SetupWithUserAuthRespRequiringMfaAccessCode extends Setup {

    val userAuthRespRequiringMfaAccessCode = UserAuthenticationResponse(
      accessCodeRequired = true,
      mfaEnabled = true,
      nonce = Some(nonce),
      session = None
    )
  }

  trait SetupWithUserAuthRespWithInconsistentState extends Setup {

    val userAuthRespWithInconsistentState = UserAuthenticationResponse(
      accessCodeRequired = true, // should be false if we already have a login session
      mfaEnabled = true,
      session = Some(sessionWithAuthAppMfa)
    )
  }

  trait PartLogged extends Setup {
    def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
    FetchSessionById.succeedsWith(sessionId, UserSession(sessionId, loggedInState, loggedInDeveloper))
  }

  trait LoggedIn extends Setup {
    def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
    FetchSessionById.succeedsWith(sessionId, UserSession(sessionId, loggedInState, loggedInDeveloper))
  }

  "authenticate with username and password" should {

    "display list applications page after successfully logging in with MFA enabled but access code not required" in new SetupWithUserAuthRespRequiringMfaAndMfaEnabled {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespNotRequiringMfaAndMfaEnabled), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.ManageApplications.manageApps().url)

      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
    }

    "display the MFA recommendation page after successfully logging in with MFA not enabled but mandated" in new SetupWithUserAuthRespNotRequiringMfa {
      testWhenMfaMandatedIs(true)
    }

    "display the MFA recommendation page after successfully logging in with MFA not enabled and not mandated" in new SetupWithUserAuthRespNotRequiringMfa {
      testWhenMfaMandatedIs(false)
    }

    "display the 2-step start page after successfully logging in with MFA not enabled but mandated" in new SetupWithUserAuthResRequiringMfaEnablement {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaEnablement), resultShowAdminMfaMandateMessage = successful(true))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = underTest.authenticate()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.get2svRecommendationPage().url)
    }

    "display the enter access code page after successfully logging in with MFA configured as AUTHENTICATOR_APP" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppMfa))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.loginAccessCodePage(authAppMfaId, AUTHENTICATOR_APP).url)
    }

    "display the enter access code page after successfully logging in with MFA configured as SMS" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithSmsMfa))
      TPDMFAMock.SendSms.thenReturn(sessionWithAuthAppMfa.developer.userId, smsMfaId)(flag = true)

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.loginAccessCodePage(smsMfaId, SMS).url)
    }

    "return error when MFA configured as SMS and it fails to send the sms" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithSmsMfa))
      TPDMFAMock.SendSms.thenReturn(sessionWithAuthAppMfa.developer.userId, smsMfaId)(flag = false)

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "display select MFA method page after successful login with both MFA methods configured" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppAndSmsMfa))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(("userId" -> sessionWithAuthAppMfa.developer.userId.value.toString) +: sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.selectLoginMfaPage(authAppMfaId, smsMfaId).url)
    }

    "display the login page when fetch developer fails" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(None)

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Sorry, there is a problem with the service")

    }

    "default case" in new SetupWithUserAuthRespWithInconsistentState {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespWithInconsistentState), successful(false))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return the login page when the password is incorrect" in new Setup {

      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, "wrongPassword1!"))
      private val result  = addToken(underTest.authenticate())(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidPassword),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text))
      )(*)
    }

    "return the login page when the password is invalid" in new Setup {
      private val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, " "))
      private val result  = addToken(underTest.authenticate())(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Enter your password")
    }

    "return the login page when the email has not been registered" in new Setup {
      private val unregisteredEmail = "unregistered@email.com".toLaxEmail
      mockAuthenticate(unregisteredEmail, userPassword, failed(new InvalidEmail), successful(false))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, unregisteredEmail.text), (passwordFieldName, userPassword))
      private val result  = addToken(underTest.authenticate())(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidEmail),
        eqTo(Map("devEmail" -> unregisteredEmail.text))
      )(*)
    }

    "return to the login page when the account is unverified" in new Setup {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, failed(new UnverifiedAccount), successful(false))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))
      private val result  = addToken(underTest.authenticate())(request)

      status(result) shouldBe FORBIDDEN
      contentAsString(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
      await(result).session(request).get("email").mkString shouldBe sessionWithAuthAppMfa.developer.email.text
    }

    "display the Account locked page when the account is locked" in new Setup {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, failed(new LockedAccount), successful(false))

      private val request = FakeRequest()
        .withCookies(deviceSessionCookie)
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, sessionWithAuthAppMfa.developer.email.text), (passwordFieldName, userPassword))

      private val result = addToken(underTest.authenticate())(request)

      status(result) shouldBe LOCKED
      contentAsString(result) should include("You've entered details that do not match our records. Reset your password to sign in.")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToLockedAccount),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text))
      )(*)
    }
  }

  "selectLoginAccessPage" should {

    "display both SMS and Authenticator App MFA methods to select for login" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)

      private val result = addToken(underTest.selectLoginMfaPage(authAppMfaId, smsMfaId))(request)

      status(result) shouldBe OK
      val content: String = contentAsString(result)

      content should include("How do you want to get access codes?")
      content should include("Text message")
      content should include("Get codes sent to a mobile phone.")
      content should include("Authenticator app for smartphone or tablet")
      content should include("Get codes generated by an authenticator app on your mobile device such as a smartphone or tablet.")
    }
  }

  "selectLoginMfaAction" should {

    "return Authenticator App access code page when mfa method is AUTHENTICATOR_APP" in new Setup {
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppAndSmsMfa))

      private val request = testRequest
        .withFormUrlEncodedBody(("mfaId", authAppMfaId.value.toString))

      private val result = underTest.selectLoginMfaAction()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.loginAccessCodePage(authAppMfaId, MfaType.AUTHENTICATOR_APP).url)
    }

    "return Sms access code page when mfa method is SMS" in new Setup {
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppAndSmsMfa))
      TPDMFAMock.SendSms.thenReturn(sessionWithAuthAppMfa.developer.userId, smsMfaId)(flag = true)

      private val request = testRequest
        .withFormUrlEncodedBody(("mfaId", smsMfaId.value.toString))

      private val result = underTest.selectLoginMfaAction()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.loginAccessCodePage(smsMfaId, MfaType.SMS).url)
    }

    "return error when user is not found" in new Setup {
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(None)

      private val request = testRequest
        .withFormUrlEncodedBody(("mfaId", authAppMfaId.value.toString))

      private val result = underTest.selectLoginMfaAction()(request)

      status(result) shouldBe NOT_FOUND
      contentAsString(result) should include("User not found")
    }

    "return error when mfaId is not valid" in new Setup {
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppAndSmsMfa))

      private val request = testRequest
        .withFormUrlEncodedBody(("mfaId", ""))

      private val result = underTest.selectLoginMfaAction()(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Error while selecting mfaId")
    }

    "return error when mfaDetail not found against the mfaId" in new Setup {
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppAndSmsMfa))

      private val request = testRequest
        .withFormUrlEncodedBody(("mfaId", UUID.randomUUID().toString))

      private val result = underTest.selectLoginMfaAction()(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include("Access code required but mfa not set up")
    }
  }

  "loginAccessCodePage" should {

    "display the enter access code page for Authenticator App" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithAuthAppMfa))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)

      private val result = addToken(underTest.loginAccessCodePage(authAppMfaId, AUTHENTICATOR_APP))(request)

      status(result) shouldBe OK

      contentAsString(result) should include("Enter your access code")
    }

    "display the enter access code page for SMS" in new SetupWithUserAuthRespRequiringMfaAccessCode {
      mockAuthenticate(sessionWithAuthAppMfa.developer.email, userPassword, successful(userAuthRespRequiringMfaAccessCode), successful(false))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))
      TPDMock.FetchDeveloper.thenReturn(sessionWithAuthAppMfa.developer.userId)(Some(developerWithSmsMfa))

      private val request = FakeRequest()
        .withSession(sessionParams: _*)

      private val result = addToken(underTest.loginAccessCodePage(smsMfaId, SMS))(request)

      status(result) shouldBe OK

      contentAsString(result) should include("Enter the access code")
      contentAsString(result) should include("We have sent a 6 digit access code")
      contentAsString(result) should include("It may take a few minutes to arrive")
      contentAsString(result) should include("If you have a UK phone number your 6-digit code will arrive from the phone number 60 551.")
    }
  }

  "authenticateAccessCode" should {

    "return the Sms Setup Reminder page when the credentials are correct and mfa method is AUTHENTICATOR_APP" in new Setup {
      mockAuthenticateAccessCode(sessionWithAuthAppMfa.developer.email, accessCode, nonce, authAppMfaId, successful(sessionWithAuthAppMfa))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", accessCode))

      private val result = underTest.authenticateAccessCode(authAppMfaId, AUTHENTICATOR_APP, userHasMultipleMfa = false)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.smsSetupReminderPage().url)
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
    }

    "return the AuthApp Setup Reminder page when the credentials are correct and mfa method is SMS" in new Setup {
      mockAuthenticateAccessCode(sessionWithAuthAppMfa.developer.email, accessCode, nonce, smsMfaId, successful(sessionWithSmsMfa))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", accessCode))

      private val result = underTest.authenticateAccessCode(smsMfaId, SMS, userHasMultipleMfa = false)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile.routes.MfaController.authAppSetupReminderPage().url)
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
    }

    "return the error page when mfa method is AUTHENTICATOR_APP and access code is incorrect" in new Setup {
      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", "654321"))

      private val result = addToken(underTest.authenticateAccessCode(authAppMfaId, AUTHENTICATOR_APP, userHasMultipleMfa = false))(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("You have entered an incorrect access code")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidAccessCode),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text))
      )(*)
    }

    "return the error page when mfa method is AUTHENTICATOR_APP and access code is invalid" in new Setup {
      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", "123xx"))

      private val result = addToken(underTest.authenticateAccessCode(authAppMfaId, AUTHENTICATOR_APP, userHasMultipleMfa = false))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("You have entered an invalid access code")
    }

    "return the manage Applications page when the credentials are correct and mfa method is SMS" in new Setup {
      mockAuthenticateAccessCode(sessionWithAuthAppMfa.developer.email, accessCode, nonce, smsMfaId, successful(sessionWithAuthAppMfa))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", accessCode))

      private val result = underTest.authenticateAccessCode(smsMfaId, SMS, userHasMultipleMfa = false)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageApplications.manageApps().url)
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
    }

    "return the error page when mfa method is SMS and access code is incorrect" in new Setup {
      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", "654321"))

      private val result = addToken(underTest.authenticateAccessCode(smsMfaId, SMS, userHasMultipleMfa = false))(request)

      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("You have entered an incorrect access code")
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginFailedDueToInvalidAccessCode),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text))
      )(*)
    }

    "return the error page when mfa method is SMS and access code is invalid" in new Setup {
      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", "123xxx"))

      private val result = addToken(underTest.authenticateAccessCode(smsMfaId, SMS, userHasMultipleMfa = false))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("You have entered an invalid access code")
    }

    "return the manage Applications page when user have both MFA methods setup and selected mfa method is SMS" in new Setup {
      mockAuthenticateAccessCode(sessionWithAuthAppMfa.developer.email, accessCode, nonce, smsMfaId, successful(sessionContainsDeveloperWithAuthAppAndSmsMfa))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      private val request = testRequest
        .withFormUrlEncodedBody(("accessCode", accessCode))

      private val result = underTest.authenticateAccessCode(smsMfaId, SMS, userHasMultipleMfa = true)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageApplications.manageApps().url)
      verify(underTest.auditService, times(1)).audit(
        eqTo(LoginSucceeded),
        eqTo(Map("devEmail" -> sessionWithAuthAppMfa.developer.email.text, "developerFullName" -> sessionWithAuthAppMfa.developer.displayedName))
      )(*)
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

      private val request = FakeRequest().withSession(sessionParams :+ "emailAddress" -> sessionWithAuthAppMfa.developer.email.text: _*)

      val userId = UserId.random
      TPDMock.FindUserId.thenReturn(sessionWithAuthAppMfa.developer.email)(userId)
      TPDMock.FetchDeveloper.thenReturn(userId)(Some(developerWithAuthAppMfa))
      when(underTest.applicationService.request2SVRemoval(*[Option[UserId]], *, eqTo(sessionWithAuthAppMfa.developer.email))(*))
        .thenReturn(Future.successful(TicketCreated))

      private val result = addToken(underTest.confirm2SVHelp())(request)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("We have received your request to remove 2-step verification from your account")
      body should include("Request submitted")
      verify(underTest.applicationService).request2SVRemoval(*[Option[UserId]], *, eqTo(sessionWithAuthAppMfa.developer.email))(*)
    }
  }

  "accountLocked" should {
    "destroy session when locked" in new Setup {
      mockLogout()
      private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionWithAuthAppMfa.sessionId)
      await(underTest.accountLocked()(request))
      verify(underTest.sessionService, atLeastOnce).destroy(eqTo(sessionWithAuthAppMfa.sessionId))(*)
    }
  }

  "login" when {
    "not logged in" should {
      "show the sign-in page" in new Setup {
        private val request = FakeRequest()

        private val result = addToken(underTest.login())(request)

        status(result) shouldBe OK
        contentAsString(result) should include("Sign in")
      }
    }

    "partially logged in" should {
      "show the sign-in page" in new SetupWithUserAuthResRequiringMfaEnablement {
        FetchSessionById.succeedsWith(sessionPartLoggedInEnablingMfa.sessionId, sessionPartLoggedInEnablingMfa)

        private val partLoggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(sessionPartLoggedInEnablingMfa.sessionId)
          .withSession(sessionParams: _*)

        private val result = addToken(underTest.login())(partLoggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Sign in")
      }
    }

    "already logged in" should {
      "show the list applications page" in new Setup {
        FetchSessionById.succeedsWith(sessionWithAuthAppMfa.sessionId, sessionWithAuthAppMfa)

        private val loggedInRequest = FakeRequest()
          .withLoggedIn(underTest, implicitly)(sessionWithAuthAppMfa.sessionId)
          .withSession(sessionParams: _*)

        private val result = addToken(underTest.login())(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageApplications.manageApps().url)
      }
    }
  }

  "get2svRecommendationPage" when {
    "a user for whom MFA will be mandated in the future logs in" should {
      "show the MFA recommendation" in new LoggedIn {
        fetchProductionSummariesByAdmin(sessionWithAuthAppMfa.developer.userId, applicationsWhereUserIsAdminInProduction)

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.get2svRecommendationPage()(request)

        status(result) shouldBe OK

        contentAsString(result) should include("Add 2-step verification")
      }
    }

    "a user for whom MFA is already mandated logs in" should {
      "not show MFA recommendation" in new LoggedIn {
        fetchProductionSummariesByAdmin(sessionWithAuthAppMfa.developer.userId, applicationsWhereUserIsAdminInProduction)

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        private val result = underTest.get2svRecommendationPage()(request)

        status(result) shouldBe OK

        contentAsString(result) should include("Add 2-step verification")
        contentAsString(result) should include("Use 2-step verification to protect your Developer Hub account and application details from being compromised.")
      }
    }
  }
}
