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

package unit.views

import config.ApplicationConfig
import controllers.AddTeamMemberForm
import domain._
import helpers.string._
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.data.Form
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown
import utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}

class ManageTeamViewSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown with MockitoSugar {

  val appConfig = mock[ApplicationConfig]
  val appId = "1234"
  val clientId = "clientId123"
  val loggedInUser = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  val collaborator = utils.DeveloperSession("developer@example.com", "firstName2", "lastName2", loggedInState = LoggedInState.LOGGED_IN)
  val collaborators = Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR), Collaborator(collaborator.email, Role.DEVELOPER))
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    collaborators, state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  "manageTeam view" should {

    def renderPage(role: Role = Role.ADMINISTRATOR, form: Form[AddTeamMemberForm] = AddTeamMemberForm.form) = {
      val request = FakeRequest().withCSRFToken
      views.html.manageTeam.manageTeam.render(application, role, form, loggedInUser, request, applicationMessages, appConfig, "nav-section")
    }

    "show Add and Remove buttons for Admin" in {
      val document = Jsoup.parse(renderPage(role = Role.ADMINISTRATOR).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe true
      elementExistsByText(document, "p", "You need admin rights to add or remove team members.") shouldBe false
      elementExistsByText(document, "td", loggedInUser.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document, controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe true
    }

    "not show Add and Remove buttons for Developer" in {
      val document = Jsoup.parse(renderPage(role = Role.DEVELOPER).body)

      elementExistsByText(document, "h1", "Manage team members") shouldBe true
      elementExistsByText(document, "a", "Add a team member") shouldBe false
      elementExistsByText(document, "p", "You need admin rights to add or remove team members.") shouldBe true
      elementExistsByText(document, "td", loggedInUser.email) shouldBe true
      elementExistsByText(document, "td", collaborator.email) shouldBe true
      linkExistsWithHref(document, controllers.routes.ManageTeam.removeTeamMember(appId, collaborator.email.toSha256).url) shouldBe false
    }
  }
}
