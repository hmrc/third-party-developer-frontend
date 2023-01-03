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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ProfileForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec
import views.html.{ChangeProfileView, ProfileView}

class ProfileSpec extends CommonViewSpec
  with WithCSRFAddToken
  with LocalUserIdTracker
  with DeveloperSessionBuilder
  with DeveloperBuilder {

  private val request = FakeRequest().withCSRFToken

  val developer = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloperWithRandomId("developer@example.com", "FirstName", "LastName", Some("TestOrganisation")))

  "Profile page" should {
    val profileView = app.injector.instanceOf[ProfileView]

    "render" in {
      val page = profileView.render(request, developer, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Manage profile") shouldBe true
      elementExistsByText(document, "h2", "Delete account") shouldBe true
      elementIdentifiedByIdContainsText(document, "account-deletion", "Request account deletion") shouldBe true
    }
  }

  "Change profile page" should {
    val changeProfileView = app.injector.instanceOf[ChangeProfileView]

    "error for invalid form" in {
      val formWithErrors = ProfileForm.form
        .withError("firstname", "First name error message")
        .withError("lastname", "Last name error message")

      val page = changeProfileView.render(formWithErrors, request, developer, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementIdentifiedByAttrWithValueContainsText(document, "span", "id", "data-field-error-firstname", "Error: First name error message") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "span", "id", "data-field-error-lastname", "Error: Last name error message") shouldBe true

    }
  }
}
