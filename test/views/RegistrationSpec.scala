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
import views.html.RegistrationView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.RegistrationForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class RegistrationSpec extends CommonViewSpec with WithCSRFAddToken {
  "Registration page" should {
    val registrationView = app.injector.instanceOf[RegistrationView]
    val request          = FakeRequest().withCSRFToken

    "render with no errors when the form is valid" in {
      val page = registrationView.render(RegistrationForm.form, request, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Register for a developer account") shouldBe true
      elementExistsByText(document, "h2", "There is a problem") shouldBe false
    }

    "render with errors when the form is invalid" in {
      val formWithErrors = RegistrationForm.form
        .withError("firstname", "First name error message")
        .withError("lastname", "Last name error message")
        .withError("emailaddress", "Email address error message")
        .withError("password", "Password error message")
        .withError("confirmpassword", "Confirm password error message")

      val page = registrationView.render(formWithErrors, request, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h2", "There is a problem") shouldBe true
      elementExistsById(document, "data-field-error-firstname") shouldBe true
      elementExistsById(document, "data-field-error-lastname") shouldBe true
      elementExistsById(document, "data-field-error-emailaddress") shouldBe true
      elementExistsById(document, "data-field-error-password") shouldBe true
    }
  }
}
