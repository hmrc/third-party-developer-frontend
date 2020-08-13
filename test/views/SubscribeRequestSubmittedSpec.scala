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

import config.ApplicationConfig
import domain.models.applications.{Application, ApplicationState, Collaborator, Environment, Role, Standard}
import domain.models.developers.LoggedInState
import model.ApplicationViewModel
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.SubscribeRequestSubmittedView

class SubscribeRequestSubmittedSpec extends CommonViewSpec with WithCSRFAddToken {
  "Subscribe request submitted page" should {
    "render with no errors" in {
      val appConfig = mock[ApplicationConfig]
      val request = FakeRequest().withCSRFToken

      val appId = "1234"
      val apiName = "Test API"
      val apiVersion = "1.0"
      val clientId = "clientId123"
      val developer = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
      val application = Application(appId, clientId, "Test Application", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("Test Application Description"),
        Set(Collaborator(developer.email, Role.ADMINISTRATOR)), state = ApplicationState.production(developer.email, ""),
        access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

      val subscribeRequestSubmittedView = app.injector.instanceOf[SubscribeRequestSubmittedView]

      val page = subscribeRequestSubmittedView.render(
        ApplicationViewModel(application,hasSubscriptionsFields = false),
        apiName,
        apiVersion,
        request,
        developer,
        messagesProvider,
        appConfig,
        "subscriptions")

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Request submitted") shouldBe true
      elementExistsById(document, "success-request-subscribe-text") shouldBe true
    }
  }
}
