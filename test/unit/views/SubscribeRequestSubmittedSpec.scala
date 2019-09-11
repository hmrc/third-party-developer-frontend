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

class SubscribeRequestSubmittedSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {
  "Subscribe request submitted page" should {
    "render with no errors" in {

      val appConfig = mock[ApplicationConfig]
      val request = FakeRequest().withCSRFToken

      val appId = "1234"
      val apiName = "Test API"
      val apiVersion = "1.0"
      val clientId = "clientId123"
      val developer = Developer("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
      val application = Application(appId, clientId, "Test Application", DateTimeUtils.now, Environment.PRODUCTION, Some("Test Application Description"),
        Set(Collaborator(developer.email, Role.ADMINISTRATOR)), state = ApplicationState.production(developer.email, ""),
        access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

      val page = views.html.subscribeRequestSubmitted.render(application, apiName, apiVersion, request, developer, applicationMessages, appConfig, "subscriptions")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Request submitted") shouldBe true
      elementExistsById(document, "success-request-subscribe-text") shouldBe true
    }
  }
}
