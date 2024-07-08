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

package steps

import java.util.UUID

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.{Cookie => SCookie}
import org.scalatest.matchers.should.Matchers
import pages._
import stubs.{DeveloperStub, DeviceSessionStub, MfaStub, Stubs}
import utils.{BrowserDriver, MfaData}

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.selenium.webdriver.Driver

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.mfa.utils.MfaDetailHelper
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.Developer
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{LoginRequest, UserAuthenticationResponse}

class MfaSteps extends ScalaDsl with EN with Matchers with NavigationSugar with BrowserDriver
    with CustomMatchers with MfaData {

  When("""^I enter the correct access code during 2SVSetup with mfaMandated '(.*)'$""") { (mfaMandated: String) =>
    val isMfaMandated = java.lang.Boolean.parseBoolean(mfaMandated)
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
    MfaStub.stubUpliftAuthSession(isMfaMandated)
    AuthenticatorAppAccessCodePage.enterAccessCode(accessCode)
  }

  When("""^I enter the correct access code during Auth App removal then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
    AuthenticatorAppAccessCodePage.enterAccessCode(accessCode)
  }

  When("""^I enter the mobile number then click continue$""") {
    SmsMobileNumberPage.enterMobileNumber(mobileNumber)
  }

  When("""^I enter the correct Sms access code then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
    SmsAccessCodePage.enterAccessCode(accessCode)
  }

  When("""^I enter the correct access code for SMS and click remember me for 7 days then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
    DeviceSessionStub.createDeviceSession(staticUserId, CREATED)
    SmsLoginAccessCodePage.page.enterAccessCode(accessCode, rememberMe = true)
  }

  When("""^I enter the correct access code SMS and do NOT click remember me for 7 days then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(smsMfaId)
    SmsLoginAccessCodePage.page.enterAccessCode(accessCode)
  }

  When("""^I enter the correct access code for Authenticator App and click remember me for 7 days then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
    DeviceSessionStub.createDeviceSession(staticUserId, CREATED)
    AuthAppLoginAccessCodePage.page.enterAccessCode(accessCode, rememberMe = true)
  }

  When("""^I enter the correct access code Authenticator App and do NOT click remember me for 7 days then click continue$""") {
    MfaStub.stubMfaAccessCodeSuccess(authAppMfaId)
    AuthAppLoginAccessCodePage.page.enterAccessCode(accessCode)
  }

  Then("""^I enter an authenticator app name$""") { () =>
    val authAppName = "SomeAuthApp"
    CreateNameForAuthAppPage.enterName(authAppName)
  }

  Then("""My device session is set$""") { () =>
    val deviceSessionCookie = Driver.instance.manage().getCookieNamed(deviceCookieName)
    deviceSessionCookie should not be null
  }
  Then("""My device session is not set$""") { () =>
    val authCookie = Driver.instance.manage().getCookieNamed(deviceCookieName)
    authCookie shouldBe null
  }

  Given("""^I have SMS enabled as MFA method, without a DeviceSession and registered with$""") { data: DataTable =>
    val result: Map[String, String] = data.asScalaRawMaps[String, String].head

    val password = result("Password")

    val developer =
      buildDeveloper(emailAddress = result("Email address").toLaxEmail, firstName = result("First name"), lastName = result("Last name"), mfaDetails = List(smsMfaDetails))

    setUpDeveloperStub(developer, smsMfaId, password, None, deviceSessionFound = false)

    MfaStub.stubSendSms(developer, smsMfaId)
    MfaStub.setupSmsAccessCode(developer, smsMfaId, mobileNumber)
    MfaStub.setupVerificationOfAccessCode(developer, smsMfaId)
  }

  Given("""^I have Authenticator App enabled as MFA method, without a DeviceSession and registered with$""") { data: DataTable =>
    val result: Map[String, String] = data.asScalaRawMaps[String, String].head

    val password = result("Password")

    val developer =
      buildDeveloper(
        emailAddress = result("Email address").toLaxEmail,
        firstName = result("First name"),
        lastName = result("Last name"),
        mfaDetails = List(authenticatorAppMfaDetails)
      )

    setUpDeveloperStub(developer, authAppMfaId, password, None, deviceSessionFound = false)

  }

  Given("""^I am mfaEnabled and with a DeviceSession registered with$""") { data: DataTable =>
    val result: Map[String, String] = data.asScalaRawMaps[String, String].head

    val password = result("Password")

    val developer =
      buildDeveloper(
        emailAddress = result("Email address").toLaxEmail,
        firstName = result("First name"),
        lastName = result("Last name"),
        mfaDetails = List(authenticatorAppMfaDetails)
      )

    setUpDeveloperStub(developer, authAppMfaId, password, Some(DeviceSessionStub.staticDeviceSessionId), true)

  }

  def setUpDeveloperStub(developer: Developer, mfaId: MfaId, password: String, deviceSessionId: Option[UUID], deviceSessionFound: Boolean) = {
    driver.manage().deleteAllCookies()
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

  Given("""^I already have a device cookie$""") {
    val cookie = new SCookie(deviceCookieName, deviceCookieValue)
    driver.manage().addCookie(cookie)
  }

  def setupLoggedOrPartLoggedInDeveloper(
      developer: Developer,
      password: String,
      loggedInState: LoggedInState,
      deviceSessionId: Option[UUID],
      accessCodeRequired: Boolean,
      mfaEnabled: Boolean
    ): String = {
    val sessionId = "sessionId_" + loggedInState.toString

    val session = Session(sessionId, developer, loggedInState)

    val actualSession = if (accessCodeRequired) None else Some(session)

    val nonce = if (accessCodeRequired) Some(MfaStub.nonce) else None

    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired, mfaEnabled, session = actualSession, nonce = nonce)

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest(
      "/authenticate",
      LoginRequest(developer.email, password, mfaMandatedForUser, deviceSessionId),
      OK,
      Json.toJson(userAuthenticationResponse).toString()
    )

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }

}
