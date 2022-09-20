/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.mfa.views.authapp

import org.jsoup.Jsoup
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppSetupCompletedView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class AuthAppSetupCompletedViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperSessionBuilder with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {
  implicit val request = FakeRequest()
  val authAppSetupCompletedView = app.injector.instanceOf[AuthAppSetupCompletedView]
  implicit val loggedIn: DeveloperSession = buildDeveloperSession( loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("developer@example.com", "Joe", "Bloggs"))

  "AuthAppSetupCompletedView view" should {
    "render correctly when form is valid" in {
      val mainView = authAppSetupCompletedView.apply()(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "You can now get access codes by authenticator app"
      document.getElementById("smartphone-text").text should include("Keep the app on your smartphone or tablet")
      document.getElementById("medium-heading").text shouldBe "You need to set up additional security"
      document.getElementById("body").text shouldBe "You need to add text messages to get access codes as an alternative to your authenticator app."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("link").text shouldBe "I can't do this right now"
      document.getElementById("link").attr("href") shouldBe "/developer/applications"
    }

  }
}
