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

import config.ApplicationConfig
import domain.Role.{ADMINISTRATOR, DEVELOPER}
import domain._
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.ViewHelpers._

class DeleteApplicationSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]
  val appId = "1234"
  val clientId = "clientId123"
  val loggedInUser = utils.DeveloperSession("developer@example.com", "John", "Doe", loggedInState = LoggedInState.LOGGED_IN)
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))
  val prodAppId = "prod123"
  val sandboxAppId = "sand123"
  val prodApp = application.copy(id = prodAppId)
  val sandboxApp = application.copy(id = sandboxAppId, deployedTo = Environment.SANDBOX)


  "delete application page" should {
    "show content and link to delete application for appropriate role/environment combinations" in {

      def verifyRequestDeletionContent(role: Role, app: Application)(implicit playApp: play.api.Application) {

        val request = FakeRequest().withCSRFToken

        val page = views.html.deleteApplication.render(app, role, request, loggedInUser, applicationMessages, appConfig, "details")

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)

        elementExistsByText(document, "h1", "Delete application") shouldBe true
        elementExistsByText(document, "p", "We'll respond to your request within 2 working days.") shouldBe true
        elementIdentifiedByAttrWithValueContainsText(document, "a", "class", "button", "Request deletion") shouldBe true
      }

      verifyRequestDeletionContent(ADMINISTRATOR, prodApp)
      verifyRequestDeletionContent(ADMINISTRATOR, sandboxApp)
      verifyRequestDeletionContent(DEVELOPER, sandboxApp)
    }

    "show no link with explanation to developer in a prod app" in {

      val request = FakeRequest().withCSRFToken

      val page = views.html.deleteApplication.render(prodApp, DEVELOPER, request, loggedInUser, applicationMessages, appConfig, "details")

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Delete application") shouldBe true
      elementExistsByText(document, "p", "You need admin rights to delete an application") shouldBe true
      elementIdentifiedByIdContainsText(document, "submit", "Request deletion") shouldBe false
    }
  }
}
