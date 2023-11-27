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

import java.time.{LocalDateTime, ZoneOffset}

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.manageTeamViews.ManageTeamView

import play.api.data.Form
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, Collaborator, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.AddTeamMemberForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class ManageTeamViewSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperTestData {

  val appId: ApplicationId                = ApplicationId.random
  val clientId: ClientId                  = ClientId("clientId123")
  val loggedInDeveloper: DeveloperSession = adminDeveloper.loggedIn
  val collaborator: DeveloperSession      = standardDeveloper.loggedIn
  val collaborators: Set[Collaborator]    = Set(loggedInDeveloper.email.asAdministratorCollaborator, collaborator.email.asDeveloperCollaborator)

  private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

  val application: Application = Application(
    appId,
    clientId,
    "App name 1",
    now,
    Some(now),
    None,
    grantLength,
    Environment.PRODUCTION,
    Some("Description 1"),
    collaborators,
    state = ApplicationState(State.PRODUCTION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), now),
    access = Access.Standard(redirectUris = List("https://red1", "https://red2").map(RedirectUri.unsafeApply), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  "manageTeam view" should {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]

    def renderPage(role: Collaborator.Role, form: Form[AddTeamMemberForm] = AddTeamMemberForm.form) = {
      val request = FakeRequest().withCSRFToken

      manageTeamView.render(
        ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false),
        role,
        form,
        Some(createFraudPreventionNavLinkViewModel(isVisible = true, "some/url")),
        request,
        messagesProvider,
        appConfig,
        "nav-section",
        loggedInDeveloper
      )
    }

    "show Add and Remove buttons for Admin" in {
      val document = Jsoup.parse(renderPage(role = Collaborator.Roles.ADMINISTRATOR).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe true
      elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false
      elementExistsByText(document, "td", loggedInDeveloper.email.text) shouldBe true
      elementExistsByText(document, "td", collaborator.email.text) shouldBe true
      linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.text.toSha256).url
      ) shouldBe true
    }

    "not show Add and Remove buttons for Developer" in {
      val document = Jsoup.parse(renderPage(role = Collaborator.Roles.DEVELOPER).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe false
      elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe true
      elementExistsByText(document, "td", loggedInDeveloper.email.text) shouldBe true
      elementExistsByText(document, "td", collaborator.email.text) shouldBe true
      linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.text.toSha256).url
      ) shouldBe false
    }
  }
}
