/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.AddTeamMemberForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import org.jsoup.Jsoup
import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.manageTeamViews.ManageTeamView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{DeveloperSessionBuilder, LocalUserIdTracker}

class ManageTeamViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  val appId = ApplicationId("1234")
  val clientId = ClientId("clientId123")
  val loggedInDeveloper = DeveloperSessionBuilder("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  val collaborator = DeveloperSessionBuilder("developer@example.com", "firstName2", "lastName2", loggedInState = LoggedInState.LOGGED_IN)
  val collaborators = Set(loggedInDeveloper.email.asAdministratorCollaborator, collaborator.email.asDeveloperCollaborator)
  val application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    Some(DateTimeUtils.now),
    None,
    grantLength,
    Environment.PRODUCTION,
    Some("Description 1"),
    collaborators,
    state = ApplicationState.production(loggedInDeveloper.email, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  "manageTeam view" should {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]

    def renderPage(role: CollaboratorRole, form: Form[AddTeamMemberForm] = AddTeamMemberForm.form) = {
      val request = FakeRequest().withCSRFToken

      manageTeamView.render(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false),
        role,
        form,
        Some(createFraudPreventionNavLinkViewModel(isVisible = true, "some/url")),
        request,
        messagesProvider,
        appConfig,
        "nav-section",
        loggedInDeveloper)
    }

    "show Add and Remove buttons for Admin" in {
      val document = Jsoup.parse(renderPage(role = CollaboratorRole.ADMINISTRATOR).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe true
      elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe false
      elementExistsByText(document, "td", loggedInDeveloper.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document,uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe true
    }

    "not show Add and Remove buttons for Developer" in {
      val document = Jsoup.parse(renderPage(role = CollaboratorRole.DEVELOPER).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe false
      elementExistsByText(document, "strong", "Warning You need admin rights to add or remove team members.") shouldBe true
      elementExistsByText(document, "td", loggedInDeveloper.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document,uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe false
    }
  }
}
