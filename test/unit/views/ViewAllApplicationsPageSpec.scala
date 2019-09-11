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
import controllers.ApplicationSummary
import domain.{Developer, _}
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.Flash
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText}

class ViewAllApplicationsPageSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]

  "veiw all applications page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request = FakeRequest()
      val loggedIn = Developer("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      views.html.manageApplications.render(appSummaries, request, Flash(), loggedIn, applicationMessages, appConfig, "nav-section")
    }

    "show the empty nest page when there are no applications" in {

      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Welcome to your account") shouldBe true
      elementExistsByText(document, "a", "Create your first application") shouldBe true
    }

    "show the applications page if there is more than 0 applications" in {

      val appName = "App name 1"
      val appEnvironment = "Sandbox"
      val appUserRole = Role.ADMINISTRATOR

      val appSummaries = Seq(ApplicationSummary("1111", appName, appEnvironment, appUserRole, TermsOfUseStatus.NOT_APPLICABLE, State.TESTING ))

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "View all applications") shouldBe true
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-environment", appEnvironment) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

    }

    "alert the user to agreeing to the terms of use on prod apps iff relevant" should {

      def shouldViewWithAppShowAlert(environment: String, termsOfUseStatus: TermsOfUseStatus, shouldAlert: Boolean) = {

        val appSummaries = Seq(ApplicationSummary("1111", "App name 1", environment, Role.ADMINISTRATOR, termsOfUseStatus, State.TESTING ))

        val document = Jsoup.parse(renderPage(appSummaries).body)

        elementExistsByText(document, "strong", "You must agree to the terms of use on all production applications.") shouldBe shouldAlert
      }

      "view with sandbox app with NA TouStatus should not show alert" in {

        shouldViewWithAppShowAlert("Sandbox", TermsOfUseStatus.NOT_APPLICABLE, shouldAlert = false)
      }

      "view with prod app which has agreed Terms of Use should not show alert" in {

        shouldViewWithAppShowAlert("Production", TermsOfUseStatus.AGREED, shouldAlert = false)
      }

      "view with prod app which has not agreed Terms of Use should show alert" in {

        shouldViewWithAppShowAlert("Production", TermsOfUseStatus.AGREEMENT_REQUIRED, shouldAlert = true)
      }
    }
  }
}
