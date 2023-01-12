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

import java.time.{LocalDateTime, Period, ZoneOffset}

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.RedirectsView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.{ADMINISTRATOR, DEVELOPER}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class RedirectsSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperBuilder {

  val appId             = ApplicationId("1234")
  val clientId          = ClientId("clientId123")
  val loggedInDeveloper = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloperWithRandomId("developer@example.com", "John", "Doe"))
  val loggedInDev       = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloperWithRandomId("developer2@example.com", "Billy", "Fontaine"))

  val application = Application(
    appId,
    clientId,
    "App name 1",
    LocalDateTime.now(ZoneOffset.UTC),
    Some(LocalDateTime.now(ZoneOffset.UTC)),
    None,
    Period.ofDays(547),
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(loggedInDeveloper.email.asAdministratorCollaborator, loggedInDev.email.asDeveloperCollaborator),
    state = ApplicationState.production(loggedInDeveloper.email, loggedInDeveloper.displayedName, ""),
    access = Standard(redirectUris = List.empty, termsAndConditionsUrl = None)
  )

  "redirects page" should {
    val redirectLimit = 5

    def renderPageWithRedirectUris(role: CollaboratorRole, numberOfRedirectUris: Int) = {
      val request        = FakeRequest().withCSRFToken
      val redirects      = 1 to numberOfRedirectUris map (num => s"http://localhost:$num")
      val standardAccess = Standard(redirectUris = redirects.toList, termsAndConditionsUrl = None)

      val applicationWithRedirects = application.copy(access = standardAccess)
      val user                     = if (role.isAdministrator) {
        loggedInDeveloper
      } else {
        loggedInDev
      }

      val redirectsView = app.injector.instanceOf[RedirectsView]

      redirectsView.render(
        ApplicationViewModel(applicationWithRedirects, hasSubscriptionsFields = false, hasPpnsFields = false),
        redirects,
        Some(createFraudPreventionNavLinkViewModel(isVisible = true, "some/url")),
        request,
        user,
        messagesProvider,
        appConfig,
        "redirects"
      )
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

      elementExistsByAttrWithValue(document, "button", "value", "Delete") shouldBe true
    }

    "not show a button for deleting a redirect uri when logged in as a developer" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsDeveloperWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "button", "value", "Delete") shouldBe false
    }

    "show a button for changing a redirect uri when logged in as an administrator" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "button", "value", "Change") shouldBe true
    }

    "not show a button for changing a redirect uri when logged in as a developer" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsDeveloperWithRedirectUris(3).body)

      elementExistsByAttrWithValue(document, "button", "value", "Change") shouldBe false
    }

    "show a message informing the user that the redirect limit has been reached" in {
      val document = Jsoup.parse(renderPageForStandardApplicationAsAdminWithRedirectUris(redirectLimit).body)

      elementExistsByText(document, "a", "Add a redirect URI") shouldBe false
      elementExistsByText(document, "p", "This is the maximum number of redirect URIs. To add another, delete one first.") shouldBe true
    }
  }
}
