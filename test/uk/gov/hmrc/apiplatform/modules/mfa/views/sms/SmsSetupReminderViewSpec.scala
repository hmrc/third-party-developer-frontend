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

package uk.gov.hmrc.apiplatform.modules.mfa.views.sms

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsSetupReminderView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class SmsSetupReminderViewSpec extends CommonViewSpec
  with WithCSRFAddToken
  with DeveloperSessionBuilder
  with DeveloperBuilder
  with LocalUserIdTracker with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  implicit val loggedIn: DeveloperSession = buildDeveloperSession(
    loggedInState =
      LoggedInState.LOGGED_IN,
    buildDeveloper("developer@example.com", "Joe", "Bloggs")
  )
  val smsSetupReminderView: SmsSetupReminderView = app.injector.instanceOf[SmsSetupReminderView]

  "SmsSetupReminderView" should {
    "render as expected" in {
      val mainView = smsSetupReminderView()(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "Get access codes by text"
      document.getElementById("paragraph-1").text shouldBe "Use your mobile or tablet to get access codes as an alternative to your authenticator app."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/profile/security-preferences/sms/setup"
      document.getElementById("link").text shouldBe "I can't do this right now"
      document.getElementById("link").attr("href") shouldBe "/developer/profile/security-preferences/sms/setup/skip"
    }
  }
}