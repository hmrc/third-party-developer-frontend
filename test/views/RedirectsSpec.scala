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
import views.html.RedirectsView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, Collaborator, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.GlobalUserIdTracker.CollaboratorSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class RedirectsSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperBuilder
    with SampleSession with SampleApplication {

  val loggedInDeveloper1: DeveloperSession = buildDeveloperWithRandomId("developer@example.com".toLaxEmail, "John", "Doe").loggedIn
  val loggedInDeveloper2: DeveloperSession = buildDeveloperWithRandomId("developer2@example.com".toLaxEmail, "Billy", "Fontaine").loggedIn

  "redirects page" should {
    val redirectLimit = 5

    def renderPageWithRedirectUris(role: Collaborator.Role, numberOfRedirectUris: Int) = {
      val request        = FakeRequest().withCSRFToken
      val redirects      = 1 to numberOfRedirectUris map (num => RedirectUri.unsafeApply(s"http://localhost:$num"))
      val standardAccess = Access.Standard(redirectUris = redirects.toList, termsAndConditionsUrl = None)

      val applicationWithRedirects =
        sampleApp.copy(access = standardAccess, collaborators = Set(loggedInDeveloper1.email.asAdministratorCollaborator, loggedInDeveloper2.email.asDeveloperCollaborator))
      val user                     = if (role.isAdministrator) {
        loggedInDeveloper1
      } else {
        loggedInDeveloper2
      }

      val redirectsView = app.injector.instanceOf[RedirectsView]

      redirectsView.render(
        ApplicationViewModel(applicationWithRedirects, hasSubscriptionsFields = false, hasPpnsFields = false),
        redirects.toList,
        Some(createFraudPreventionNavLinkViewModel(isVisible = true, "some/url")),
        request,
        user,
        messagesProvider,
        appConfig,
        "redirects"
      )
    }

    def renderPageForStandardApplicationAsAdminWithRedirectUris(numberOfRedirectUris: Int) = {
      renderPageWithRedirectUris(Collaborator.Roles.ADMINISTRATOR, numberOfRedirectUris)
    }

    def renderPageForStandardApplicationAsDeveloperWithRedirectUris(numberOfRedirectUris: Int) = {
      renderPageWithRedirectUris(Collaborator.Roles.DEVELOPER, numberOfRedirectUris)
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
