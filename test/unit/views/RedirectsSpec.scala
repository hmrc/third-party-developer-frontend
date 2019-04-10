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

import domain.Role.{ADMINISTRATOR, DEVELOPER}
import domain._
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import play.api.i18n.Messages.Implicits._
import config.ApplicationConfig
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import utils.ViewHelpers._

class RedirectsSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]
  val appId = "1234"
  val clientId = "clientId123"
  val loggedInUser = Developer("developer@example.com", "John", "Doe")
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq.empty, termsAndConditionsUrl = None)
  )

  "redirects page" should {
    val redirectLimit = 5

    def renderPageWithRedirectUris(role: Role, numberOfRedirectUris: Int) = {
      val request = FakeRequest().withCSRFToken
      val redirects = 1 to numberOfRedirectUris map(num => s"http://localhost:$num")
      val standardAccess = Standard(redirectUris = redirects, termsAndConditionsUrl = None)

      val applicationWithRedirects = application.copy(access = standardAccess)

      views.html.redirects.render(applicationWithRedirects, redirects, role, request, loggedInUser, applicationMessages, appConfig, "redirects")
    }

    def renderPageForStandardApplicationAsAdminWithRedirectUris(numberOfRedirectUris: Int) = {
      renderPageWithRedirectUris(ADMINISTRATOR, numberOfRedirectUris)
    }

    def renderPageForStandardApplicationAsDeveloperWithRedirectUris(numberOfRedirectUris: Int) = {
      renderPageWithRedirectUris(DEVELOPER, numberOfRedirectUris)
    }

    "show a button for adding a redirect uri" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(3).body)

      elementExistsByText(document, "a", "Add a redirect URI") shouldBe true
      elementExistsByText(document, "p", "This is the maximum number of redirect URIs. To add another, delete one first.") shouldBe false
    }

    "show a button for deleting a redirect uri when logged in as an administrator" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "input", "value", "Delete") shouldBe true
    }

    "not show a button for deleting a redirect uri when logged in as a developer" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsDeveloperWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "input", "value", "Delete") shouldBe false
    }

    "show a button for changing a redirect uri when logged in as an administrator" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "input", "value", "Change") shouldBe true
    }

    "not show a button for changing a redirect uri when logged in as a developer" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsDeveloperWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "input", "value", "Change") shouldBe false
    }

    "show a message informing the user that the redirect limit has been reached" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(redirectLimit).body)

      elementExistsByText(document, "a", "Add a redirect URI") shouldBe false
      elementExistsByText(document, "p", "This is the maximum number of redirect URIs. To add another, delete one first.") shouldBe true
    }
  }
}
