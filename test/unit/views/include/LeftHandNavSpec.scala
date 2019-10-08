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

package unit.views.include

import domain._
import org.jsoup.Jsoup
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.ViewHelpers.elementExistsByText

class LeftHandNavSpec extends UnitSpec with OneServerPerSuite {

  "left hand nav" should {
      val request = FakeRequest().withCSRFToken

      val applicationId = "1234"
      val clientId = "clientId123"
      val applicationName = "Test Application"

      val loggedInUser = utils.DeveloperSession("givenname.familyname@example.com", "Givenname", "Familyname", loggedInState = LoggedInState.LOGGED_IN)

      val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
        Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
        access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

    "render with no errors" in {
      val page = views.html.include.leftHandNav.render(Some(application), Some("details"))

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "a", "Manage API subscriptions") shouldBe true
      elementExistsByText(document, "a", "Manage credentials") shouldBe true
      elementExistsByText(document, "a", "Manage redirect URIs") shouldBe true
      elementExistsByText(document, "a", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Delete application") shouldBe true
    }
  }
}
