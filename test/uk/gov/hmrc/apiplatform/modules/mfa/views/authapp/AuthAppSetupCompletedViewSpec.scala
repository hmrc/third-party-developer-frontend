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

package uk.gov.hmrc.apiplatform.modules.mfa.views.authapp

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppSetupCompletedView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class AuthAppSetupCompletedViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with DeveloperSessionBuilder
    with DeveloperBuilder
    with LocalUserIdTracker with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  implicit val loggedIn: DeveloperSession                  = buildDeveloperSession(
    loggedInState =
      LoggedInState.LOGGED_IN,
    buildDeveloper("developer@example.com", "Joe", "Bloggs")
  )
  val authAppSetupCompletedView: AuthAppSetupCompletedView = app.injector.instanceOf[AuthAppSetupCompletedView]

  "AuthAppSetupCompletedView" should {
    "render correctly when form is valid and showSmsText flag is false" in {
      val mainView = authAppSetupCompletedView.apply(false)(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "You can now get access codes by authenticator app"
      document.getElementById("paragraph").text should startWith("Keep the app on your smartphone or tablet.")
      document.getElementById("body").text shouldBe "You'll be able to choose between getting access codes by text or your authenticator app."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/applications"
    }

    "render correctly when form is valid and showSmsText flag is true" in {
      val mainView = authAppSetupCompletedView.apply(true)(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "You can now get access codes by authenticator app"
      document.getElementById("paragraph").text should startWith("Keep the app on your smartphone or tablet.")

      document.getElementById("body").text shouldBe "You need to add text messages to get access codes as an alternative to your authenticator app."
      document.getElementById("medium-heading").text shouldBe "You need to set up additional security"
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/profile/security-preferences/sms/setup"
      document.getElementById("link").text shouldBe "I can't do this right now"
      document.getElementById("link").attr("href") shouldBe "/developer/profile/security-preferences/sms/setup/skip"
    }
  }
}
