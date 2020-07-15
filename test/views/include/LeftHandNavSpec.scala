/*
 * Copyright 2020 HM Revenue & Customs
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

package views.include

import controllers.Credentials.serverTokenCutoffDate
import domain._
import model.ApplicationViewModel
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.elementExistsByText
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.include.LeftHandNav

class LeftHandNavSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val leftHandNavView = app.injector.instanceOf[LeftHandNav]

    val request = FakeRequest().withCSRFToken

    val applicationId = "1234"
    val clientId = "clientId123"
    val applicationName = "Test Application"

    val loggedInUser = utils.DeveloperSession("givenname.familyname@example.com", "Givenname", "Familyname", loggedInState = LoggedInState.LOGGED_IN)

    val application = Application(applicationId, clientId, applicationName,
      DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("Description 1"),
      Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
      state = ApplicationState.production(loggedInUser.email, ""),
      access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

    val applicationViewModelWithApiSubscriptions = ApplicationViewModel(application,hasSubscriptionsFields = true)
    val applicationViewModelWithNoApiSubscriptions = ApplicationViewModel(application,hasSubscriptionsFields = false)
  }

  "Left Hand Nav" when {
    "working with an application with no api subscriptions" should {
      "render correctly" in new Setup {
        val page = leftHandNavView.render(Some(applicationViewModelWithNoApiSubscriptions), Some("details"), request, loggedInUser)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "API subscriptions") shouldBe true
        elementExistsByText(document, "a", "Subscription configuration") shouldBe false
        elementExistsByText(document, "a", "Credentials") shouldBe true
        elementExistsByText(document, "a", "Client ID") shouldBe true
        elementExistsByText(document, "a", "Client secrets") shouldBe true
        elementExistsByText(document, "a", "Server token") shouldBe false
        elementExistsByText(document, "a", "Redirect URIs") shouldBe true
        elementExistsByText(document, "a", "Team members") shouldBe true
        elementExistsByText(document, "a", "Delete application") shouldBe true
      }

      "display server token link for old apps" in new Setup {
        val oldAppWithoutSubsFields = ApplicationViewModel(application.copy(createdOn = serverTokenCutoffDate.minusDays(1)), hasSubscriptionsFields = false)
        val page = leftHandNavView.render(Some(oldAppWithoutSubsFields), Some("details"), request, loggedInUser)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Server token") shouldBe true
      }
    }

    "working with an application with api subscriptions" should {
      "render correctly" in new Setup {
        val page = leftHandNavView.render(Some(applicationViewModelWithApiSubscriptions), Some("details"), request, loggedInUser)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "API subscriptions") shouldBe true
        elementExistsByText(document, "a", "Subscription configuration") shouldBe true
        elementExistsByText(document, "a", "Credentials") shouldBe true
        elementExistsByText(document, "a", "Client ID") shouldBe true
        elementExistsByText(document, "a", "Client secrets") shouldBe true
        elementExistsByText(document, "a", "Server token") shouldBe false
        elementExistsByText(document, "a", "Redirect URIs") shouldBe true
        elementExistsByText(document, "a", "Team members") shouldBe true
        elementExistsByText(document, "a", "Delete application") shouldBe true
      }

      "display server token link for old apps" in new Setup {
        val oldAppWithSubsFields = ApplicationViewModel(application.copy(createdOn = serverTokenCutoffDate.minusDays(1)), hasSubscriptionsFields = true)
        val page = leftHandNavView.render(Some(oldAppWithSubsFields), Some("details"), request, loggedInUser)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "a", "Server token") shouldBe true
      }
    }
  }
}
