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
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsSetupSkippedView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class SmsSetupSkippedViewSpec extends CommonViewSpec
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
  val smsSetupSkippedView: SmsSetupSkippedView = app.injector.instanceOf[SmsSetupSkippedView]

  "SmsSetupSkippedView" should {
    "render as expected" in {
      val mainView = smsSetupSkippedView()(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "Get access codes by text later"
      document.getElementById("paragraph-1").text shouldBe "You didn't finish setting up getting access codes by text."
      document.getElementById("paragraph-2").text shouldBe "We'll remind you next time you sign in."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("submit").attr("href") shouldBe "/developer/applications"
    }
  }
}