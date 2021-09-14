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

import domain.models.apidefinitions.ApiVersion
import domain.models.applications._
import domain.models.developers.LoggedInState
import domain.models.controllers.ApplicationViewModel
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers._
import utils._
import views.helper.CommonViewSpec
import views.html.UnsubscribeRequestSubmittedView

class UnsubscribeRequestSubmittedSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker {
  "Unsubscribe request submitted page" should {
    "render with no errors" in {

      val request = FakeRequest().withCSRFToken

      val appId = ApplicationId("1234")
      val apiName = "Test API"
      val apiVersion = ApiVersion("1.0")
      val clientId = ClientId("clientId123")
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
      val application = Application(
        appId,
        clientId,
        "Test Application",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        Environment.PRODUCTION,
        Some("Test Application Description"),
        Set(developer.email.asAdministratorCollaborator),
        state = ApplicationState.production(developer.email, ""),
        access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
      )

      val unsubscribeRequestSubmittedView = app.injector.instanceOf[UnsubscribeRequestSubmittedView]

      val page =
        unsubscribeRequestSubmittedView.render(ApplicationViewModel(application, false, false), apiName, apiVersion, request, developer, messagesProvider, appConfig, "subscriptions")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Request submitted") shouldBe true
      elementExistsById(document, "success-request-unsubscribe-text") shouldBe true
    }
  }
}
