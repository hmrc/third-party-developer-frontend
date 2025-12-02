/*
 * Copyright 2025 HM Revenue & Customs
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

import org.openqa.selenium.{Cookie => SCookie}
import play.api.http.Status._
import uk.gov.hmrc.selenium.webdriver.Driver
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.UserAuthenticationResponse
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.SessionCreateWithDeviceRequest
import play.api.libs.json.Json

object MfaStepsSteps extends MfaData with Matchers with NavigationSugar {

  // ^I enter the correct access code during 2SVSetup with mfaMandated '(.*)'$
  def whenIEnterTheCorrectAccessCodeDuring2SVSetupWithMfaMandated(isMfaMandated: Boolean): Unit = {
        MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
        MfaStub.stubUpliftAuthSession(isMfaMandated)
        AuthenticatorAppAccessCodePage.enterAccessCode(accessCode)
  }

  // ^I enter the correct access code during Auth App removal then click continue$
  def whenIEnterTheCorrectAccessCodeDuringAuthAppRemovalThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
        AuthenticatorAppAccessCodePage.enterAccessCode(accessCode)
  }

  // ^I enter the mobile number then click continue$
  def whenIEnterTheMobileNumberThenClickContinue(): Unit = {
    SmsMobileNumberPage.enterMobileNumber(mobileNumber)
  }

  // ^I enter the correct Sms access code then click continue$
  def whenIEnterTheCorrectSmsAccessCodeThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
        SmsAccessCodePage.enterAccessCode(accessCode)
  }

  // ^I enter the correct access code for SMS and click remember me for 7 days then click continue$
  def whenIEnterTheCorrectAccessCodeForSMSAndClickRememberMeFor7DaysThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
        DeviceSessionStub.createDeviceSession(staticUserId, CREATED)
        SmsLoginAccessCodePage.page.enterAccessCode(accessCode, rememberMe = true)
  }

  // ^I enter the correct access code SMS and do NOT click remember me for 7 days then click continue$
  def whenIEnterTheCorrectAccessCodeSMSAndDoNOTClickRememberMeFor7DaysThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
        SmsLoginAccessCodePage.page.enterAccessCode(accessCode)
  }

  // ^I enter the correct access code for Authenticator App and click remember me for 7 days then click continue$
  def whenIEnterTheCorrectAccessCodeForAuthenticatorAppAndClickRememberMeFor7DaysThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
        DeviceSessionStub.createDeviceSession(staticUserId, CREATED)
        AuthAppLoginAccessCodePage.page.enterAccessCode(accessCode, rememberMe = true)
  }

  // ^I enter the correct access code Authenticator App and do NOT click remember me for 7 days then click continue$
  def whenIEnterTheCorrectAccessCodeAuthenticatorAppAndDoNOTClickRememberMeFor7DaysThenClickContinue(): Unit = {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
        AuthAppLoginAccessCodePage.page.enterAccessCode(accessCode)
  }

  // ^I enter an authenticator app name$
  def thenIEnterAnAuthenticatorAppName(): Unit = {
    val authAppName = "SomeAuthApp"
        CreateNameForAuthAppPage.enterName(authAppName)
  }

  // My device session is set$
  def thenMyDeviceSessionIsSet(): Unit = {
    val deviceSessionCookie = Driver.instance.manage().getCookieNamed(deviceCookieName)
        deviceSessionCookie should not be null
  }

  // My device session is not set$
  def thenMyDeviceSessionIsNotSet(): Unit = {
    val authCookie = Driver.instance.manage().getCookieNamed(deviceCookieName)
        authCookie shouldBe null
  }

  // ^I have SMS enabled as MFA method, without a DeviceSession and registered with$
  def givenIHaveSMSEnabledAsMFAMethodWithoutADeviceSessionAndRegisteredWith(result: Map[String, String]): Unit = {
    val password = result("Password")

    val developer =
      buildUser(emailAddress = result("Email address").toLaxEmail, firstName = result("First name"), lastName = result("Last name"), mfaDetails = List(smsMfaDetails))

    setUpDeveloperStub(developer, smsMfaId, password, None, deviceSessionFound = false)

    MfaStub.stubSendSms(developer, smsMfaId)
    MfaStub.setupSmsAccessCode(developer, smsMfaId, mobileNumber)
    MfaStub.setupVerificationOfAccessCode(developer, smsMfaId)
  }

  // ^I have Authenticator App enabled as MFA method, without a DeviceSession and registered with$
  def givenIHaveAuthenticatorAppEnabledAsMFAMethodWithoutADeviceSessionAndRegisteredWith(result: Map[String, String]): Unit = {
    val password = result("Password")

    val developer =
      buildUser(
        emailAddress = result("Email address").toLaxEmail,
        firstName = result("First name"),
        lastName = result("Last name"),
        mfaDetails = List(authenticatorAppMfaDetails)
      )

    setUpDeveloperStub(developer, authAppMfaId, password, None, deviceSessionFound = false)
  }

  // ^I am mfaEnabled and with a DeviceSession registered with$
  def givenIAmMfaEnabledAndWithADeviceSessionRegisteredWith(result: Map[String, String]): Unit = {
    val password = result("Password")

    val developer =
      buildUser(
        emailAddress = result("Email address").toLaxEmail,
        firstName = result("First name"),
        lastName = result("Last name"),
        mfaDetails = List(authenticatorAppMfaDetails)
      )

    setUpDeveloperStub(developer, authAppMfaId, password, Some(DeviceSessionStub.staticDeviceSessionId), true)
}

  // ^I already have a device cookie$
  def givenIAlreadyHaveADeviceCookie(): Unit = {
    val cookie = new SCookie(deviceCookieName, deviceCookieValue)
        Driver.instance.manage().addCookie(cookie)
  }

    def setUpDeveloperStub(developer: User, mfaId: MfaId, password: String, deviceSessionId: Option[DeviceSessionId], deviceSessionFound: Boolean) = {
    Driver.instance.manage().deleteAllCookies()
    val mfaEnabled         = MfaDetailHelper.isAuthAppMfaVerified(developer.mfaDetails) || MfaDetailHelper.isSmsMfaVerified(developer.mfaDetails)
    val accessCodeRequired = deviceSessionId.isEmpty && mfaEnabled

    TestContext.sessionIdForloggedInDeveloper =
      setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.LOGGED_IN, deviceSessionId, accessCodeRequired, mfaEnabled)

    deviceSessionId match {
      case Some(_) => deviceSessionId.map(_ =>
          if (deviceSessionFound) DeviceSessionStub.getDeviceSessionForSessionIdAndUserId(staticUserId)
          else DeviceSessionStub.getDeviceSessionNotFound(staticUserId)
        )
      case None    => ()
    }

    if (mfaEnabled) {
      MfaStub.stubRemoveMfaById(developer, mfaId)
      MfaStub.setupVerificationOfAccessCode(developer, mfaId)
    }
    DeveloperStub.findUserIdByEmailAddress(developer.email)
    Stubs.setupPostRequest("/check-password", NO_CONTENT)

    TestContext.developer = developer

    DeveloperStub.setupGettingDeveloperByUserId(developer)
  }

  def setupLoggedOrPartLoggedInDeveloper(
      developer: User,
      password: String,
      loggedInState: LoggedInState,
      deviceSessionId: Option[DeviceSessionId],
      accessCodeRequired: Boolean,
      mfaEnabled: Boolean
    ): UserSessionId = {
    val sessionId = UserSessionId.random

    val session = UserSession(sessionId, loggedInState, developer)

    val actualSession = if (accessCodeRequired) None else Some(session)

    val nonce = if (accessCodeRequired) Some(MfaStub.nonce) else None

    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired, mfaEnabled, session = actualSession, nonce = nonce)

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest(
      "/authenticate",
      SessionCreateWithDeviceRequest(developer.email, password, Some(mfaMandatedForUser), deviceSessionId),
      OK,
      Json.toJson(userAuthenticationResponse).toString()
    )

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }


}
