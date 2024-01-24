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
import views.html.DeleteApplicationView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class DeleteApplicationSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker with SampleSession with SampleApplication
    with DeveloperSessionBuilder
    with DeveloperTestData {

  val deleteApplicationView = app.injector.instanceOf[DeleteApplicationView]

  val application             = sampleApp
  val prodAppId               = ApplicationId.random
  val sandboxAppId            = ApplicationId.random
  val prodApp: Application    = application.copy(id = prodAppId)
  val sandboxApp: Application = application.copy(id = sandboxAppId, deployedTo = Environment.SANDBOX)

  "delete application page" should {
    "show content and link to delete application for Administrator" when {
      "on Production" in {
        val request = FakeRequest().withCSRFToken

        val page = deleteApplicationView.render(prodApp, Collaborator.Roles.ADMINISTRATOR, request, loggedInDeveloper, messagesProvider, appConfig)

        page.contentType should include("text/html")
        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Delete application") shouldBe true
        elementExistsByText(document, "p", "We'll respond to your request within 2 working days.") shouldBe true
        elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "govuk-button govuk-button--warning", "Request deletion") shouldBe true
      }

      "on Sandbox" in {
        val request = FakeRequest().withCSRFToken

        val page = deleteApplicationView.render(sandboxApp, Collaborator.Roles.ADMINISTRATOR, request, loggedInDeveloper, messagesProvider, appConfig)

        page.contentType should include("text/html")
        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Delete application") shouldBe true
        elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "govuk-button", "Continue") shouldBe true
      }
    }

    "show no link with explanation to contact the administrator" when {
      "there is only one administrator" in {
        Seq(prodApp, sandboxApp) foreach { application =>
          val request = FakeRequest().withCSRFToken

          val page = deleteApplicationView.render(application, Collaborator.Roles.DEVELOPER, request, loggedInDeveloper, messagesProvider, appConfig)

          page.contentType should include("text/html")
          val document = Jsoup.parse(page.body)
          elementExistsByText(document, "h1", "Delete application") shouldBe true
          elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe false
          elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Continue") shouldBe false
          elementExistsByText(document, "div", "You cannot delete this application because you're not an administrator.") shouldBe true
          elementExistsByText(document, "p", s"Ask the administrator ${loggedInDeveloper.email.text} to delete it.") shouldBe true
        }
      }

      "there are multiple administrators" in {
        val extraAdmin = "admin@test.com".toLaxEmail.asAdministratorCollaborator
        Seq(prodApp.copy(collaborators = prodApp.collaborators + extraAdmin), sandboxApp.copy(collaborators = sandboxApp.collaborators + extraAdmin))
          .foreach { application =>
            val request = FakeRequest().withCSRFToken

            val page = deleteApplicationView.render(application, Collaborator.Roles.DEVELOPER, request, loggedInDeveloper, messagesProvider, appConfig)

            page.contentType should include("text/html")
            val document = Jsoup.parse(page.body)
            elementExistsByText(document, "h1", "Delete application") shouldBe true
            elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe false
            elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Continue") shouldBe false
            elementExistsByText(document, "div", "You cannot delete this application because you're not an administrator.") shouldBe true
            elementExistsByText(document, "p", "Ask one of these administrators to delete it:") shouldBe true
            application.collaborators foreach { admin => elementExistsByText(document, "li", admin.emailAddress.text) shouldBe true }
          }
      }
    }
  }
}
