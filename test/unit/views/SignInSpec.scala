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

package unit.views

import config.ApplicationConfig
import controllers.LoginForm
import domain.Developer
import org.jsoup.Jsoup
import org.scalatestplus.play.OneServerPerSuite
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Flash
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class SignInSpec extends UnitSpec with OneServerPerSuite {

  val loggedInUser = Developer("admin@example.com", "firstName1", "lastName1")

  "Sign in page" should {

    def renderPage(form: Form[LoginForm] = LoginForm.form) = {
      val request = FakeRequest().withCSRFToken
      views.html.signIn.render("heading", form, endOfJourney = true, request, Flash(), applicationMessages, ApplicationConfig)
    }

    "show an error when email address is invalid" in {
      val error = "Email error"
      val invalidForm = LoginForm.form.withError("emailaddress", error)
      val document = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsByText(document, "span", error) shouldBe true
    }

    "show an error when password is invalid" in {
      val error = "Password error"
      val invalidForm = LoginForm.form.withError("password", error)
      val document = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsByText(document, "span", error) shouldBe true
    }
  }
}