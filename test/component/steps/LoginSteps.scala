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
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.Matchers
import play.api.http.Status._
import play.api.libs.json.Json
import steps.PageSugar

import scala.collection.mutable

class LoginSteps extends ScalaDsl with EN with Matchers with NavigationSugar with PageSugar with CustomMatchers {

  import scala.collection.JavaConverters._

  implicit val webDriver: WebDriver = Env.driver


  Given("""^I am successfully logged in with '(.*)' and '(.*)'$""") { (email: String, password: String) =>
    goOn(SignInPage.default)
    webDriver.manage().deleteAllCookies()
    webDriver.navigate().refresh()
    Form.populate(mutable.Map("email address" -> email, "password" -> password))
    click on id("submit")
  }

  Given("""^I am registered with$""") { (data: DataTable) =>
    val result = data.asMaps(classOf[String], classOf[String]).get(0)
    val developer = Developer(result.get("Email address"), result.get("First name"), result.get("Last name"), None)
    val sessionId = "sessionId"
    val session = Session(sessionId, developer)
    val password = result.get("Password")

    Stubs.setupPostRequest("/check-password", NO_CONTENT)
    Stubs.setupPostRequest("/session", UNAUTHORIZED)

    Stubs.setupEncryptedPostRequest("/session", LoginRequest(developer.email, password),
      OK, Json.toJson(session).toString())

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)
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
      case _: org.openqa.selenium.NoSuchElementException => {
        val menu = webDriver.findElement(By.linkText("Menu"))
        menu.click()

        val link2 = webDriver.findElement(By.linkText("Sign out"))
        link2.click()
      }
    }
  }

}
