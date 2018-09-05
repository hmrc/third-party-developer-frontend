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
import controllers.RegistrationForm
import org.jsoup.Jsoup
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Flash
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class RegistrationSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite {
  "Registration page" should {
    val flash = mock[Flash]
    val request = FakeRequest().withCSRFToken

    "render with no errors when the form is valid" in {
      val page = views.html.registration.render(RegistrationForm.form, flash, request, applicationMessages, ApplicationConfig)
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
        .withError("organisation", "Organisation error message")

      val page = views.html.registration.render(formWithErrors, flash, request, applicationMessages, ApplicationConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h2", "There is a problem") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-firstname", "First name error message") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-lastname", "Last name error message") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-emailaddress", "Email address error message") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-password", "Password error message") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-organisation", "Organisation error message") shouldBe true
    }
  }
}
