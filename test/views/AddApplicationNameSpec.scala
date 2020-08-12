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

package views

import controllers.AddApplicationNameForm
import domain.models.applications.Environment
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.data.Form
import play.api.test.FakeRequest
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.AddApplicationNameView

class AddApplicationNameSpec extends CommonViewSpec with WithCSRFAddToken {

  val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]
  val loggedInUser = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  val subordinateEnvironment = Environment.SANDBOX
  val appId = "1234"
  val principalEnvironment = Environment.PRODUCTION

  "Add application page in subordinate" should {

    def renderPage(form: Form[AddApplicationNameForm]) = {
      val request = FakeRequest().withCSRFToken
      addApplicationNameView.render(form, subordinateEnvironment, request, loggedInUser, messagesProvider, appConfig)
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
      addApplicationNameView.render(form, principalEnvironment, request, loggedInUser, messagesProvider, appConfig)
    }

    "show an error when application name is invalid" in {
      val error = "An error"
      val formWithInvalidName = AddApplicationNameForm.form.withError("applicationName", error)
      val document = Jsoup.parse(renderPage(formWithInvalidName).body)
      elementExistsByText(document, "span", error) shouldBe true
    }
  }
}
