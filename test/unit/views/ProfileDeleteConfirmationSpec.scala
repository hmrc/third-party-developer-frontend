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
import controllers.DeleteProfileForm
import domain._
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class ProfileDeleteConfirmationSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]

  "Profile delete confirmation page" should {
    "render with no errors" in {
      val request = FakeRequest().withCSRFToken

      val developer = Developer("Test", "Test", "Test", None)

      val page = views.html.profileDeleteConfirmation.render(DeleteProfileForm.form, request, developer, appConfig, applicationMessages, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h2", "Are you sure you want us to delete your account?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountYes", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountNo", "No") shouldBe true
    }

    "render with error when no radio button has been selected" in {
      val request = FakeRequest().withCSRFToken

      val developer = Developer("Test", "Test", "Test", None)

      val formWithErrors = DeleteProfileForm.form.withError("confirmation", "Tell us if you want us to delete your account")

      val page = views.html.profileDeleteConfirmation.render(formWithErrors, request, developer, appConfig, applicationMessages, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementIdentifiedByAttrWithValueContainsText(document, "a", "href", "#confirmation", "Tell us if you want us to delete your account") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "span", "class", "error-message", "Tell us if you want us to delete your account") shouldBe true
    }
  }
}
