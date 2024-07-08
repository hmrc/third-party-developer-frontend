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

package uk.gov.hmrc.apiplatform.modules.mfa.views.sms

import org.jsoup.Jsoup
import views.helper.CommonViewSpec

import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsSetupCompletedView
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, DeveloperTestData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SmsSetupCompletedViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with DeveloperSessionBuilder
    with DeveloperTestData
    with LocalUserIdTracker with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  implicit val loggedIn: DeveloperSession          = JoeBloggs.loggedIn
  val smsSetupCompletedView: SmsSetupCompletedView = app.injector.instanceOf[SmsSetupCompletedView]

  "SmsSetupCompletedView" should {
    "render correctly when form is valid and showAuthAppText flag is false" in {
      val mainView = smsSetupCompletedView.apply(false)(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "You can now get access codes by text"
      document.getElementById("paragraph").text shouldBe "Every time you sign in we will request an access code."

      document.getElementById("body").text shouldBe "You can choose between getting access codes by text or your authenticator app."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/applications"
    }

    "render correctly when form is valid and showAuthAppText flag is true" in {
      val mainView = smsSetupCompletedView.apply(true)(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "You can now get access codes by text"
      document.getElementById("paragraph").text shouldBe "Every time you sign in we will request an access code."

      document.getElementById("body").text shouldBe "You need to add an authenticator app to get access codes as an alternative to text."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/profile/security-preferences/auth-app/start"
      document.getElementById("link").text shouldBe "I can't do this right now"
      document.getElementById("link").attr("href") shouldBe "/developer/applications"
    }
  }
}
