/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.AddTeamMemberForm
import domain.models.applications._
import domain.models.developers.LoggedInState
import helpers.string._
import model.ApplicationViewModel
import org.jsoup.Jsoup
import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.manageTeamViews.ManageTeamView
import domain.models.developers.UserId

class ManageTeamViewSpec extends CommonViewSpec with WithCSRFAddToken {

  val appId = ApplicationId("1234")
  val clientId = ClientId("clientId123")
  val loggedInUser = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  val collaborator = utils.DeveloperSession("developer@example.com", "firstName2", "lastName2", loggedInState = LoggedInState.LOGGED_IN)
  val collaborators = Set(Collaborator(loggedInUser.email, CollaboratorRole.ADMINISTRATOR, loggedInUser.developer.userId), Collaborator(collaborator.email, CollaboratorRole.DEVELOPER, UserId.random))
  val application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Environment.PRODUCTION,
    Some("Description 1"),
    collaborators,
    state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  "manageTeam view" should {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]

    def renderPage(role: CollaboratorRole, form: Form[AddTeamMemberForm] = AddTeamMemberForm.form) = {
      val request = FakeRequest().withCSRFToken

      manageTeamView.render(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), role, form, request, messagesProvider, appConfig, "nav-section", loggedInUser)
    }

    "show Add and Remove buttons for Admin" in {
      val document = Jsoup.parse(renderPage(role = CollaboratorRole.ADMINISTRATOR).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe true
      elementExistsByText(document, "p", "You need admin rights to add or remove team members.") shouldBe false
      elementExistsByText(document, "td", loggedInUser.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document, controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe true
    }

    "not show Add and Remove buttons for Developer" in {
      val document = Jsoup.parse(renderPage(role = CollaboratorRole.DEVELOPER).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe false
      elementExistsByText(document, "p", "You need admin rights to add or remove team members.") shouldBe true
      elementExistsByText(document, "td", loggedInUser.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document, controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe false
    }
  }
}
