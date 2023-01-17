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
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.AddApplicationSuccessView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class AddApplicationSuccessSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperBuilder {

  val addApplicationSuccess = app.injector.instanceOf[AddApplicationSuccessView]
  val sandboxMessage        = "You can now get your sandbox credentials for testing."
  val sandboxButton         = "Manage API subscriptions"

  "Add application success page" should {

    def testPage(applicationName: String, environment: Environment): Document = {
      val applicationId = ApplicationId("application-id")
      val loggedIn      = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("", "", "", None))
      val request       = FakeRequest().withCSRFToken
      val page          = addApplicationSuccess.render(applicationName, applicationId, environment, request, loggedIn, messagesProvider, appConfig, navSection = "nav-section")
      val document      = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", s"You added $applicationName") shouldBe true
      document
    }

    "allow manage API subscriptions for sandbox application" in {
      val applicationName = "an application name"
      val document        = testPage(applicationName, Environment.SANDBOX)
      elementExistsByText(document, "p", sandboxMessage) shouldBe true
      elementExistsByText(document, "a", sandboxButton) shouldBe true
    }
  }
}
