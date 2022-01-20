/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoginForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.data.Form
import play.api.test.FakeRequest
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.SignInView

class SignInSpec extends CommonViewSpec with WithCSRFAddToken {
  val signInView = app.injector.instanceOf[SignInView]

  val loggedInDeveloper = utils.DeveloperSessionBuilder("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)

  "Sign in page" should {
    def renderPage(form: Form[LoginForm]) = {
      val request = FakeRequest().withCSRFToken
      signInView.render("heading", form, endOfJourney = true, request, messagesProvider, appConfig)
    }

    "show an error when email address is invalid" in {
      val error = "Email error"
      val invalidForm = LoginForm.form.withError("emailaddress", error)
      val document = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsById(document, "data-field-error-emailaddress") shouldBe true
    }

    "show an error when password is invalid" in {
      val error = "Password error"
      val invalidForm = LoginForm.form.withError("password", error)
      val document = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsById(document, "data-field-error-password") shouldBe true
    }
  }
}
