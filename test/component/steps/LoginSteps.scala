/*
 * Copyright 2018 HM Revenue & Customs
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

package component.steps

import component.matchers.CustomMatchers
import component.pages._
import component.stubs.Stubs
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import domain.{Developer, LoginRequest, Session}
import org.joda.time.DateTime
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.Matchers
import play.api.libs.json.Json
import steps.PageSugar

import scala.collection.mutable

class LoginSteps extends ScalaDsl with EN with Matchers with NavigationSugar with PageSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver

  Given("""^I am not logged in the Developer Hub$""") {
    webDriver.manage().deleteAllCookies()
  }

  Given("""^I am successfully logged in with '(.*)' and '(.*)'$""") { (email: String, password: String) =>
    goOn(SignInPage.default)
    webDriver.manage().deleteAllCookies()
    webDriver.navigate().refresh()
    Form.populate(mutable.Map("email address" -> email, "password" -> password))
    click on id("submit")
  }

  Given("""^I am registered with$""") { (data: DataTable) =>
    val result = data.asMaps(classOf[String], classOf[String]).get(0)
    val incomplete = result.getOrDefault("incomplete account setup", "false").toBoolean
    val developer = Developer(result.get("Email address"), result.get("First name"), result.get("Last name"), None)
    val sessionId = "sessionId"
    val session = Session(sessionId, developer)
    val password = result.get("Password")

    Stubs.setupPostRequest("/check-password", 204)
    Stubs.setupPostRequest("/session", 401)

    Stubs.setupEncryptedPostRequest("/session", LoginRequest(developer.email, password),
      200, Json.toJson(session).toString())

    Stubs.setupRequest(s"/session/$sessionId", 200, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", 200)
  }

  Given("""^I fill in the login form with$""") { (data: DataTable) =>
    val form = data.asMaps(classOf[String], classOf[String]).get(0).asScala
    Form.populate(form)
  }

  When("""^I click on 'Sign out'""") {
    try {
      val link = webDriver.findElement(By.linkText("Sign out"))
      link.click()
    } catch {
      case ex: org.openqa.selenium.NoSuchElementException => {
        val menu = webDriver.findElement(By.linkText("Menu"))
        menu.click()

        val link2 = webDriver.findElement(By.linkText("Sign out"))
        link2.click()
      }
    }
  }

  Then("""^I am logged in as '(.+)'$""") { userFullName: String =>
    val authCookie = webDriver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie should not be null
    ManageApplicationPage.validateLoggedInAs(userFullName)
  }


  Given("""^I Signout from application if i have Signed In$""") {
    webDriver.manage().deleteAllCookies()
  }


  Then("""^I am not logged in$""") { () =>
    val authCookie = webDriver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie shouldBe null
  }

  Then("""^the user-nav header contains a sign in link""") { () =>
    val header = webDriver.findElement(By.id("user-nav-links"))
    header.findElements(By.linkText("Sign out")) shouldBe empty
    header.findElement(By.linkText("Sign in")).isDisplayed shouldBe true
  }

  Then("""^My session is set to expire within ([0-9]+) minutes$""") { minutes: Int =>
    val authCookie = webDriver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    val expiry = new DateTime(authCookie.getExpiry)
    val inNMinutes = DateTime.now.plusMinutes(minutes)

    expiry.isBefore(inNMinutes) shouldBe true
  }


  When("""^I attempt to Sign out when the session expires""") {
    val sessionId = "sessionId"
    Stubs.setupDeleteRequest(s"/session/$sessionId", 404)
    try {
      val link = webDriver.findElement(By.linkText("Sign out"))
      link.click()
    } catch {
      case ex: org.openqa.selenium.NoSuchElementException => {
        val menu = webDriver.findElement(By.linkText("Menu"))
        menu.click()

        val link2 = webDriver.findElement(By.linkText("Sign out"))
        link2.click()
      }
    }
  }

}
