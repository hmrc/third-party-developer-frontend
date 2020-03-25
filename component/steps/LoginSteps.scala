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

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.client.WireMock._
import matchers.CustomMatchers
import pages._
import stubs.{DeveloperStub, Stubs}
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import domain._
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.Matchers
import play.api.http.Status._
import play.api.libs.json.{Format, Json}
import steps.PageSugar

import scala.collection.mutable

case class MfaSecret(secret: String)

object MfaSecret {
  implicit val format: Format[MfaSecret] = Json.format[MfaSecret]
}

object TestContext {
  var developer: Developer = null

  var sessionIdForLoggedInUser: String = ""
  var sessionIdForMfaMandatingUser: String = ""
}

class LoginSteps extends ScalaDsl with EN with Matchers with NavigationSugar with PageSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver

  private val accessCode = "123456"

  Given("""^I am successfully logged in with '(.*)' and '(.*)'$""") { (email: String, password: String) =>
    goOn(SignInPage.default)
    webDriver.manage().deleteAllCookies()
    webDriver.navigate().refresh()
    Form.populate(mutable.Map("email address" -> email, "password" -> password))
    click on id("submit")
    on(RecommendMfaPage)
    click on waitForElement(By.id("skip")) // Skip the 2SV reminder screen
    on(RecommendMfaSkipAcknowledgePage)
    click on waitForElement(By.id("submit")) // Continue past confirmation of skipping 2SV setup
  }

  Given("""^I am registered with$""") { data: DataTable =>
    val result = data.asMaps(classOf[String], classOf[String]).get(0)

    val password = result.get("Password")

    Stubs.setupPostRequest("/check-password", NO_CONTENT)
    Stubs.setupPostRequest("/authenticate", UNAUTHORIZED)

    val developer = Developer(result.get("Email address"), result.get("First name"), result.get("Last name"), None)

    TestContext.developer = developer

    TestContext.sessionIdForLoggedInUser = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.LOGGED_IN)
    TestContext.sessionIdForMfaMandatingUser = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

    setupGettingDeveloperByEmail(developer)

    setupGettingMfaSecret(developer)

    setupVerificationOfAccessCode(developer)

    setupEnablingMfa(developer)
  }

  private def setupVerificationOfAccessCode(developer: Developer): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.email}/mfa/verification"))
        .withRequestBody(equalTo(Json.toJson(VerifyMfaRequest(accessCode)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT)
        ))
  }

  private def setupEnablingMfa(developer: Developer): Unit = {
    stubFor(
      put(urlPathEqualTo(s"/developer/${developer.email}/mfa/enable"))
        .willReturn(aResponse()
          .withStatus(OK)
        ))
  }

  private def setupGettingMfaSecret(developer: Developer): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.email}/mfa"))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(MfaSecret("mySecret")).toString())))
  }

  private def setupGettingDeveloperByEmail(developer: Developer): Unit = {
    val encodedEmail = URLEncoder.encode(developer.email, "UTF-8")

    stubFor(get(urlPathEqualTo("/developer"))
      .withQueryParam("email", equalTo(encodedEmail))
      .willReturn(aResponse()
        .withStatus(OK)
        .withBody(Json.toJson(developer).toString())))
  }

  Given("""^'(.*)' session is uplifted to LoggedIn$""") { email: String =>
    if (email != TestContext.developer.email) {
      throw new IllegalArgumentException(s"Can only know how to uplift ${TestContext.developer.email}'s session")
    }

    val session = Session(TestContext.sessionIdForMfaMandatingUser, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK)
  }

  Given("""^I fill in the login form with$""") { (data: DataTable) =>
    val form = data.asMaps(classOf[String], classOf[String]).get(0).asScala
    Form.populate(form)
  }

  Then("""^I am logged in as '(.+)'$""") { userFullName: String =>
    val authCookie = webDriver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie should not be null
    ManageApplicationPage.validateLoggedInAs(userFullName)
  }

  Then("""^I am not logged in$""") { () =>
    val authCookie = webDriver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie shouldBe null
  }

  When("""^I attempt to Sign out when the session expires""") {
    val sessionId = "sessionId"
    Stubs.setupDeleteRequest(s"/session/$sessionId", NOT_FOUND)
    try {
      val link = webDriver.findElement(By.linkText("Sign out"))
      link.click()
    } catch {
      case _: org.openqa.selenium.NoSuchElementException =>
        val menu = webDriver.findElement(By.linkText("Menu"))
        menu.click()

        val link2 = webDriver.findElement(By.linkText("Sign out"))
        link2.click()
    }
  }

  When("""^I enter the correct access code and continue$""") {
    Setup2svEnterAccessCodePage.enterAccessCode(accessCode)
    Setup2svEnterAccessCodePage.clickContinue()
  }

  Then("""^I should be sent an email with a link to reset for '(.*)'$""") { email : String =>
    DeveloperStub.verifyResetPassword(email)
  }


  def setupLoggedOrPartLoggedInDeveloper(developer: Developer, password: String, loggedInState: LoggedInState): String = {

    val sessionId = "sessionId_" + loggedInState.toString

    val session = Session(sessionId, developer, loggedInState)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, session = Some(session))

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest("/authenticate", LoginRequest(developer.email, password, mfaMandatedForUser),
      OK, Json.toJson(userAuthenticationResponse).toString())

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }
}
