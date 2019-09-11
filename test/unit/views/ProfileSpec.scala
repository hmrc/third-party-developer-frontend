/*
 * Copyright 2019 HM Revenue & Customs
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
import controllers.ProfileForm
import domain._
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class ProfileSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]
  private val request = FakeRequest().withCSRFToken

  val developer = Developer("developer@example.com", "FirstName", "LastName", Some("TestOrganisation"), loggedInState = LoggedInState.LOGGED_IN)

  "Profile page" should {

    "render" in {

      val page = views.html.profile.render(request, developer, appConfig, applicationMessages, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Manage profile") shouldBe true
      elementExistsByText(document, "h2", "Delete account") shouldBe true
      elementIdentifiedByIdContainsText(document, "account-deletion", "Request account deletion") shouldBe true
    }
  }

  "Change profile page" should {

    "error for invalid form" in {

      val formWithErrors = ProfileForm.form
        .withError("firstname", "First name error message")
        .withError("lastname", "Last name error message")

      val page = views.html.changeProfile.render(formWithErrors, request, developer, appConfig, applicationMessages, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-firstname", "First name error message") shouldBe true
      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-lastname", "Last name error message") shouldBe true

    }
  }
}
