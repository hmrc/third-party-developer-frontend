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

package views

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.SignInView

import play.api.data.Form
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.LoginForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SignInSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperBuilder {

  val signInView = app.injector.instanceOf[SignInView]

  val loggedInDeveloper = buildDeveloperWithRandomId("admin@example.com".toLaxEmail, "firstName1", "lastName1").loggedIn

  "Sign in page" should {
    def renderPage(form: Form[LoginForm]) = {
      val request = FakeRequest().withCSRFToken
      signInView.render("heading", form, endOfJourney = true, request, messagesProvider, appConfig)
    }

    "show an error when email address is invalid" in {
      val error       = "Email error"
      val invalidForm = LoginForm.form.withError("emailaddress", error)
      val document    = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsById(document, "data-field-error-emailaddress") shouldBe true
    }

    "show an error when password is invalid" in {
      val error       = "Password error"
      val invalidForm = LoginForm.form.withError("password", error)
      val document    = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsById(document, "data-field-error-password") shouldBe true
    }
  }
}
