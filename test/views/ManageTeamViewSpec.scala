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
import views.html.manageTeamViews.ManageTeamView

import play.api.data.Form
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaboratorsFixtures, Collaborator}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.AddTeamMemberForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WithCSRFAddToken}

class ManageTeamViewSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with DeveloperSessionBuilder with UserTestData with CollaboratorTracker
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val appId: ApplicationId             = standardApp.id
  val clientId: ClientId               = standardApp.clientId
  val loggedInDeveloper: UserSession   = adminDeveloper.loggedIn
  val collaborator: UserSession        = standardDeveloper.loggedIn
  val collaborators: Set[Collaborator] = Set(loggedInDeveloper.developer.email.asAdministratorCollaborator, collaborator.developer.email.asDeveloperCollaborator)

  def unverifiedUser(emailAddress: LaxEmailAddress): User =
    User(
      emailAddress,
      "firstName",
      "lastName",
      instant,
      instant,
      false,
      accountSetup = None,
      List.empty,
      nonce = None,
      EmailPreferences.noPreferences,
      UserId.random
    )
  val collaboratorUsers: Seq[User]                        = List(adminDeveloper, standardDeveloper)
  val collaboratorUsersWithUnverifiedAdmin: Seq[User]     = List(adminDeveloper, unverifiedUser(collaborator.developer.email))
  val application                                         = standardApp.withCollaborators(collaborators.toList: _*)

  "manageTeam view" should {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]

    def renderPage(
        role: Collaborator.Role,
        collaboratorUsers: Seq[User] = collaboratorUsers,
        form: Form[AddTeamMemberForm] = AddTeamMemberForm.form,
        model: ApplicationViewModel = ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false)
      ) = {
      val request = FakeRequest().withCSRFToken

      manageTeamView.render(
        model,
        collaboratorUsers,
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

    "show Add and Remove links for Admin" in {
      val document = Jsoup.parse(renderPage(role = Collaborator.Roles.ADMINISTRATOR).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe true)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      withClue("collaborator email")(elementExistsByText(document, "td", s"${collaborator.developer.email.text}") shouldBe true)
      withClue("Remove link present for developer collaborator")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.developer.email.text.toSha256).url
      ) shouldBe true)
      withClue("Remove link present for sole verified admin")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, loggedInDeveloper.developer.email.text.toSha256).url
      ) shouldBe false)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        """[class="govuk-details__summary-text"]""",
        "Why can't I remove myself from this application?"
      ) shouldBe true)
    }

    "show Remove links for when multiple verified Admin" in {
      val document = Jsoup.parse(renderPage(
        role = Collaborator.Roles.ADMINISTRATOR,
        model = ApplicationViewModel(
          standardApp.withCollaborators(loggedInDeveloper.developer.email.asAdministratorCollaborator, collaborator.developer.email.asAdministratorCollaborator),
          hasSubscriptionsFields = false,
          hasPpnsFields = false
        )
      ).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe true)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      withClue("collaborator email")(elementExistsByText(document, "td", s"${collaborator.developer.email.text}") shouldBe true)
      withClue("Remove link present")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, loggedInDeveloper.developer.email.text.toSha256).url
      ) shouldBe true)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        "summary",
        "Why can't I remove myself from this application?"
      ) shouldBe false)
    }

    "show (Unverified) suffix for unverified Admin" in {
      val document = Jsoup.parse(renderPage(
        role = Collaborator.Roles.ADMINISTRATOR,
        collaboratorUsers = collaboratorUsersWithUnverifiedAdmin,
        model = ApplicationViewModel(
          standardApp.withCollaborators(loggedInDeveloper.developer.email.asAdministratorCollaborator, collaborator.developer.email.asAdministratorCollaborator),
          hasSubscriptionsFields = false,
          hasPpnsFields = false
        )
      ).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe true)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      withClue("collaborator email")(elementExistsByText(document, "td", s"${collaborator.developer.email.text} (Unverified)") shouldBe true)
      withClue("Remove link present")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, loggedInDeveloper.developer.email.text.toSha256).url
      ) shouldBe false)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        "summary",
        "Why can't I remove myself from this application?"
      ) shouldBe true)
    }

    "disallow sole verified Admin removing self" in {
      val document = Jsoup.parse(renderPage(
        role = Collaborator.Roles.ADMINISTRATOR,
        collaboratorUsers = collaboratorUsersWithUnverifiedAdmin,
        model = ApplicationViewModel(
          standardApp.withCollaborators(loggedInDeveloper.developer.email.asAdministratorCollaborator, collaborator.developer.email.asAdministratorCollaborator),
          hasSubscriptionsFields = false,
          hasPpnsFields = false
        )
      ).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe true)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      withClue("collaborator email")(elementExistsByText(document, "td", s"${collaborator.developer.email.text} (Unverified)") shouldBe true)
      withClue("Remove link present")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, loggedInDeveloper.developer.email.text.toSha256).url
      ) shouldBe false)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        "summary",
        "Why can't I remove myself from this application?"
      ) shouldBe true)
    }

    "not show Add and Remove links for Developer" in {
      val document = Jsoup.parse(renderPage(role = Collaborator.Roles.DEVELOPER).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe false)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe true)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      elementExistsByText(document, "td", s"${collaborator.developer.email.text}") shouldBe true
      withClue("Remove link present")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.developer.email.text.toSha256).url
      ) shouldBe false)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        "summary",
        "Why can't I remove myself from this application?"
      ) shouldBe false)
    }

    "not show Remove link table column for Developer" in {
      val document = Jsoup.parse(renderPage(role = Collaborator.Roles.DEVELOPER).body)

      withClue("Heading")(elementExistsByText(document, "h1", "Manage team members") shouldBe true)
      withClue("Add team member link")(elementExistsByText(document, "a", "Add a team member") shouldBe false)
      withClue("Warning")(elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe true)
      withClue("Session email")(elementExistsByText(document, "td", s"${loggedInDeveloper.developer.email.text}") shouldBe true)
      withClue("Not showing 'Remove' column")(document.select("table thead tr th").size() shouldBe 2)
      withClue("Remove link present")(linkExistsWithHref(
        document,
        uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.developer.email.text.toSha256).url
      ) shouldBe false)
      withClue("Why can't I remove myself details")(elementExistsByText(
        document,
        "summary",
        "Why can't I remove myself from this application?"
      ) shouldBe false)
    }
  }
}
