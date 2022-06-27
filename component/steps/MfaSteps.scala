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

package steps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.{WebDriver, Cookie => SCookie}
import org.scalatest.matchers.should.Matchers
import pages._
import play.api.http.Status._
import play.api.libs.json.Json
import stubs.{DeveloperStub, DeviceSessionStub, MfaStub, Stubs}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{LoginRequest, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session}
import utils.ComponentTestDeveloperBuilder

import java.util.UUID


class MfaSteps extends ScalaDsl with EN with Matchers with NavigationSugar with PageSugar
  with CustomMatchers with ComponentTestDeveloperBuilder {


  implicit val webDriver: WebDriver = Env.driver

  private val accessCode = "123456"
  private val deviceCookieName = "DEVICE_SESS_ID"
  private val deviceCookieValue = "a6b5b0cca96fef5ffc66edffd514a9239b46b4e869fc10f6-9193-42b4-97f2-87886c972ad4"


  When("""^I enter the correct access code during 2SVSetup with mfaMandated '(.*)'$""") { (mfaMandated: String) =>
    val isMfaMandated = java.lang.Boolean.parseBoolean(mfaMandated)
    MfaStub.stubAuthenticateTotpSuccess()
    MfaStub.stubUpliftAuthSession(isMfaMandated)
    Setup2svEnterAccessCodePage.enterAccessCode(accessCode)
    Setup2svEnterAccessCodePage.clickContinue()
  }

  When("""^I enter the correct access code during remove2SV then click continue$""") {
    MfaStub.stubAuthenticateTotpSuccess()
    MfaRemovePage.enterAccessCode(accessCode)
    MfaRemovePage.clickContinue()
  }


  When("""^I enter the correct access code and click remember me for 7 days then click continue$""") {
     MfaStub.stubAuthenticateTotpSuccess()
    Login2svEnterAccessCodePage.enterAccessCode(accessCode, rememberMe = true)
    DeviceSessionStub.createDeviceSession(staticUserId, CREATED)
    Login2svEnterAccessCodePage.clickContinue()

  }

  When("""^I enter the correct access code and do NOT click remember me for 7 days then click continue$""") {
    MfaStub.stubAuthenticateTotpSuccess()
    Login2svEnterAccessCodePage.enterAccessCode(accessCode)
    Login2svEnterAccessCodePage.clickContinue()
  }

  Then("""My device session is set$""") { () =>
    val deviceSessionCookie = webDriver.manage().getCookieNamed(deviceCookieName)
    deviceSessionCookie should not be null
  }
  Then("""My device session is not set$""") { () =>
    val authCookie = webDriver.manage().getCookieNamed(deviceCookieName)
    authCookie shouldBe null
  }



  Given("""^I am mfaEnabled without a DeviceSession and registered with$"""){  data: DataTable =>
    val result: Map[String,String] = data.asScalaRawMaps[String, String].head
  
    val password = result("Password")

      val developer = buildDeveloper(emailAddress = result("Email address"), firstName = result("First name"), lastName = result("Last name"))
    .copy(mfaEnabled = Some(true), userId = staticUserId)
   setUpDeveloperStub(developer, password, None, false)

  }

  Given("""^I am mfaEnabled and with a DeviceSession registered with$""") {  data: DataTable =>
    val result: Map[String,String] = data.asScalaRawMaps[String, String].head
  
    val password = result("Password")

      val developer = buildDeveloper(emailAddress = result("Email address"), firstName = result("First name"), lastName = result("Last name"))
    .copy(mfaEnabled = Some(true), userId = staticUserId)
   setUpDeveloperStub(developer, password, Some(DeviceSessionStub.staticDeviceSessionId), true)

  }



  def setUpDeveloperStub(developer: Developer, password: String, deviceSessionId: Option[UUID], deviceSessionFound: Boolean) ={
    webDriver.manage().deleteAllCookies()
    val mfaEnabled = developer.mfaEnabled.getOrElse(false)

        val accessCodeRequired = deviceSessionId.isEmpty && mfaEnabled

      TestContext.sessionIdForloggedInDeveloper =
         setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.LOGGED_IN, deviceSessionId , accessCodeRequired, mfaEnabled)
    deviceSessionId match {
      case Some(_) => deviceSessionId.map(_ =>
        if(deviceSessionFound) DeviceSessionStub.getDeviceSessionForSessionIdAndUserId(staticUserId)
        else DeviceSessionStub.getDeviceSessionNotFound(staticUserId))
      case None => ()
    }

    MfaStub.stubRemoveMfa(developer)
    MfaStub.setupVerificationOfAccessCode(developer)
    DeveloperStub.findUserIdByEmailAddress(developer.email)
    Stubs.setupPostRequest("/check-password", NO_CONTENT)

    TestContext.developer = developer

    DeveloperStub.setupGettingDeveloperByEmail(developer)

    DeveloperStub.setUpGetCombinedApis()
  }

  Given("""^I already have a device cookie$""") {
    val cookie = new SCookie(deviceCookieName, deviceCookieValue)
    webDriver.manage().addCookie(cookie)
  }



  def setupLoggedOrPartLoggedInDeveloper(developer: Developer, password: String, loggedInState: LoggedInState, deviceSessionId: Option[UUID], accessCodeRequired: Boolean, mfaEnabled : Boolean): String = {
    val sessionId = "sessionId_" + loggedInState.toString

    val session = Session(sessionId, developer, loggedInState)

    val actualSession = if(accessCodeRequired) None else Some(session)

    val nonce = if(accessCodeRequired) Some(MfaStub.nonce) else None

    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired, mfaEnabled, session = actualSession, nonce = nonce)

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest("/authenticate", LoginRequest(developer.email, password, mfaMandatedForUser, deviceSessionId),
      OK, Json.toJson(userAuthenticationResponse).toString())

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }

}