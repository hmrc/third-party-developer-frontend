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
import controllers.ProfileForm
import domain._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class ProfileSpec extends UnitSpec with OneServerPerSuite {
  "Profile page" should {
    "render" in {
      val request = FakeRequest().withCSRFToken

      val developer = Developer("Test", "Test", "Test", None)

      val application = Application(
        "Test Application ID",
        "Test Application Client ID",
        "Test Application",
        DateTime.now(),
        Environment.PRODUCTION,
        Some("Test Application"),
        Set.empty,
        Standard(),
        false,
        ApplicationState.testing,
        None
      )

      val page = views.html.profile.render(developer,request, developer, ApplicationConfig, applicationMessages, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Manage profile") shouldBe true
      elementExistsByText(document, "h2", "Delete account") shouldBe true
      elementIdentifiedByIdContainsText(document, "account-deletion", "Request account deletion") shouldBe true
    }
  }
}
