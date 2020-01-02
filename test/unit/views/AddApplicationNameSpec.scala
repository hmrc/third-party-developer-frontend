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

package unit.views

import config.ApplicationConfig
import controllers.AddApplicationNameForm
import domain.{Environment, LoggedInState}
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class AddApplicationNameSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val loggedInUser = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  val appConfig = mock[ApplicationConfig]
  val subordinateEnvironment = Environment.from("sandbox")
  val appId = "1234"
  val principalEnvironment = Environment.from("production")

  "Add application page in subordinate" should {

    def renderPage(form: Form[AddApplicationNameForm]) = {
      val request = FakeRequest().withCSRFToken
      views.html.addApplicationName.render(form, appId, subordinateEnvironment, request, loggedInUser, applicationMessages, appConfig, "nav-section")
    }

    "show an error when application name is invalid" in {
      val error = "An error"
      val formWithInvalidName = AddApplicationNameForm.form.withError("applicationName", error)
      val document = Jsoup.parse(renderPage(formWithInvalidName).body)
      elementExistsByText(document, "span", error) shouldBe true
    }
  }
  "Add application page in principal" should {

    def renderPage(form: Form[AddApplicationNameForm]) = {
      val request = FakeRequest().withCSRFToken
      views.html.addApplicationName.render(form, appId, principalEnvironment, request, loggedInUser, applicationMessages, appConfig, "nav-section")
    }

    "show an error when application name is invalid" in {
      val error = "An error"
      val formWithInvalidName = AddApplicationNameForm.form.withError("applicationName", error)
      val document = Jsoup.parse(renderPage(formWithInvalidName).body)
      elementExistsByText(document, "span", error) shouldBe true
    }
  }
}