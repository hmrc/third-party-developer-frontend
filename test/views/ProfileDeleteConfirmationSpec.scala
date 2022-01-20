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

import controllers.DeleteProfileForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ProfileDeleteConfirmationView

class ProfileDeleteConfirmationSpec extends CommonViewSpec with WithCSRFAddToken {

  val profileDeleteConfirmation = app.injector.instanceOf[ProfileDeleteConfirmationView]

  "Profile delete confirmation page" should {
    "render with no errors" in {
      val request = FakeRequest().withCSRFToken

      val developer = utils.DeveloperSessionBuilder("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = profileDeleteConfirmation.render(DeleteProfileForm.form, request, developer, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "legend", "Are you sure you want us to delete your account?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountYes", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountNo", "No") shouldBe true
    }

    "render with error when no radio button has been selected" in {
      val request = FakeRequest().withCSRFToken

      val developer = utils.DeveloperSessionBuilder("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

      val formWithErrors = DeleteProfileForm.form.withError("confirmation", "Tell us if you want us to delete your account")

      val page = profileDeleteConfirmation.render(formWithErrors, request, developer, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementIdentifiedByAttrWithValueContainsText(document, "a", "href", "#confirmation", "Tell us if you want us to delete your account") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "span", "class", "govuk-error-message", "Error: Tell us if you want us to delete your account") shouldBe true
    }
  }
}
