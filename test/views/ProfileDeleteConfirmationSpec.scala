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
import views.html.ProfileDeleteConfirmationView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.DeleteProfileForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ProfileDeleteConfirmationSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder {

  val profileDeleteConfirmation = app.injector.instanceOf[ProfileDeleteConfirmationView]

  "Profile delete confirmation page" should {
    val developer        = buildTrackedUser("Test".toLaxEmail, "Test", "Test", None)
    val developerSession = developer.loggedIn

    "render with no errors" in {
      val request = FakeRequest().withCSRFToken

      val page = profileDeleteConfirmation.render(DeleteProfileForm.form, request, developerSession, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "legend", "Are you sure you want us to delete your account?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountYes", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteAccountNo", "No") shouldBe true
    }

    "render with error when no radio button has been selected" in {
      val request = FakeRequest().withCSRFToken

      val formWithErrors = DeleteProfileForm.form.withError("confirmation", "Tell us if you want us to delete your account")

      val page = profileDeleteConfirmation.render(formWithErrors, request, developerSession, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementIdentifiedByAttrWithValueContainsText(document, "a", "href", "#confirmation", "Tell us if you want us to delete your account") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "span", "class", "govuk-error-message", "Error: Tell us if you want us to delete your account") shouldBe true
    }
  }
}
