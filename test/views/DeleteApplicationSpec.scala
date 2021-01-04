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

import domain.models.applications._
import domain.models.applications.Role.{ADMINISTRATOR, DEVELOPER}
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.DeleteApplicationView

class DeleteApplicationSpec extends CommonViewSpec with WithCSRFAddToken {

  val deleteApplicationView = app.injector.instanceOf[DeleteApplicationView]
  val appId = ApplicationId("1234")
  val clientId = ClientId("clientId123")
  val loggedInUser = utils.DeveloperSession("developer@example.com", "John", "Doe", loggedInState = LoggedInState.LOGGED_IN)
  val application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
    state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )
  val prodAppId = ApplicationId("prod123")
  val sandboxAppId = ApplicationId("sand123")
  val prodApp: Application = application.copy(id = prodAppId)
  val sandboxApp: Application = application.copy(id = sandboxAppId, deployedTo = Environment.SANDBOX)

  "delete application page" should {
    "show content and link to delete application for Administrator" when {
      "on Production" in {
        val request = FakeRequest().withCSRFToken

        val page = deleteApplicationView.render(prodApp, ADMINISTRATOR, request, loggedInUser, messagesProvider, appConfig)

        page.contentType should include("text/html")
        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Delete application") shouldBe true
        elementExistsByText(document, "p", "We'll respond to your request within 2 working days.") shouldBe true
        elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe true
      }

      "on Sandbox" in {
        val request = FakeRequest().withCSRFToken

        val page = deleteApplicationView.render(sandboxApp, ADMINISTRATOR, request, loggedInUser, messagesProvider, appConfig)

        page.contentType should include("text/html")
        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Delete application") shouldBe true
        elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Continue") shouldBe true
      }
    }

    "show no link with explanation to contact the administrator" when {
      "there is only one administrator" in {
        Seq(prodApp, sandboxApp) foreach { application =>
          val request = FakeRequest().withCSRFToken

          val page = deleteApplicationView.render(application, DEVELOPER, request, loggedInUser, messagesProvider, appConfig)

          page.contentType should include("text/html")
          val document = Jsoup.parse(page.body)
          elementExistsByText(document, "h1", "Delete application") shouldBe true
          elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe false
          elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Continue") shouldBe false
          elementExistsByText(document, "p", "You cannot delete this application because you're not an administrator.") shouldBe true
          elementExistsByText(document, "p", s"Ask the administrator ${loggedInUser.email} to delete it.") shouldBe true
        }
      }

      "there are multiple administrators" in {
        val extraAdmin = Collaborator("admin@test.com", Role.ADMINISTRATOR)
        Seq(prodApp.copy(collaborators = prodApp.collaborators + extraAdmin), sandboxApp.copy(collaborators = sandboxApp.collaborators + extraAdmin))
          .foreach { application =>
            val request = FakeRequest().withCSRFToken

            val page = deleteApplicationView.render(application, DEVELOPER, request, loggedInUser, messagesProvider, appConfig)

            page.contentType should include("text/html")
            val document = Jsoup.parse(page.body)
            elementExistsByText(document, "h1", "Delete application") shouldBe true
            elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe false
            elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Continue") shouldBe false
            elementExistsByText(document, "p", "You cannot delete this application because you're not an administrator.") shouldBe true
            elementExistsByText(document, "p", "Ask one of these administrators to delete it:") shouldBe true
            application.collaborators foreach { admin => elementExistsByText(document, "li", admin.emailAddress) shouldBe true }
          }
      }
    }
  }
}
