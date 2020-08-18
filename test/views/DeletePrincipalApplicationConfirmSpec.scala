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

package views

import controllers.DeletePrincipalApplicationForm
import domain.models.applications._
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrWithValueContainsText}
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.DeletePrincipalApplicationConfirmView

class DeletePrincipalApplicationConfirmSpec extends CommonViewSpec with WithCSRFAddToken {

  val deletePrincipalApplicationConfirmView = app.injector.instanceOf[DeletePrincipalApplicationConfirmView]

  "delete application confirm page" should {

    val request = FakeRequest().withCSRFToken
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

    "render with no errors" in {

      val page = deletePrincipalApplicationConfirmView.render(application, DeletePrincipalApplicationForm.form, request, loggedInUser, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Delete application") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want us to delete this application?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "yes", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "no", "No") shouldBe true
    }

    "render with error when no radio button has been selected" in {

      val formWithErrors = DeletePrincipalApplicationForm.form.withError("confirmation", "Confirmation error message")

      val page = deletePrincipalApplicationConfirmView.render(application, formWithErrors, request, loggedInUser, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      document.body().toString.contains("Confirmation error message") shouldBe true
      elementExistsByText(document, "h1", "Delete application") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want us to delete this application?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "yes", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "no", "No") shouldBe true
    }
  }
}
