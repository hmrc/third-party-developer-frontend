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

import component.pages.{NavigationSugar, ResetPasswordLink}
import component.stubs.{DeveloperStub, Stubs}
import cucumber.api.scala.{EN, ScalaDsl}
import org.openqa.selenium.WebDriver
import org.scalatest.Matchers

class PasswordSteps extends ScalaDsl with EN with Matchers with NavigationSugar {

  implicit val webDriver: WebDriver = Env.driver

  Given( """^my registration is unverified$""") {
    Stubs.setupPostRequest("/john.smith@example.com/password-reset-request", 403)
    Stubs.setupPostRequest("/session", 403)
    Stubs.setupPostRequest("/john.smith@example.com/resend-verification", 200)
  }

  Given( """^my registration is verified$""") {
    Stubs.setupPostRequest("/john.smith@example.com/password-reset-request", 200)
  }
  Given( """^I am not registered$""") {
    Stubs.setupPostRequest("/john.smith@example.com/password-reset-request", 404)
  }

  Given( """^I requested password reset$""") {
    DeveloperStub.setupReset("resetCode", 200, """{"email":"john.smith@example.com"}""")
    Stubs.setupPostRequest("/reset-password", 204)
  }

  When( """^I click on the reset password link$""") {
    go(ResetPasswordLink("john.smith@example.com", "resetCode"))
  }

  Given( """^my login failed for last (\d+) times$""") { (times: Int) =>
    Stubs.setupPostRequest("/session", 423)
  }

  Given( """^I entered wrong current password$""") {
    Stubs.setupPostRequest("/change-password", 401)
  }

  Given( """^I entered password for a locked account$""") {
    Stubs.setupPostRequest("/change-password", 423)
  }
}
