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

package uk.gov.hmrc.apiplatform.modules.mfa.views

import org.jsoup.Jsoup
import views.helper.CommonViewSpec

import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.views.html.RemoveMfaCompletedView
import uk.gov.hmrc.apiplatform.modules.tpd.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperTestData

class RemoveMfaCompletedViewSpec extends CommonViewSpec
    with DeveloperTestData
    with DeveloperSessionBuilder
    with LocalUserIdTracker
    with StubMessagesFactory {

  implicit val loggedIn: DeveloperSession = JoeBloggs.loggedIn

  val removeMfaCompletedView: RemoveMfaCompletedView = app.injector.instanceOf[RemoveMfaCompletedView]

  "RemoveMfaCompletedView" should {
    "render as expected" in {
      val mainView = removeMfaCompletedView.apply()(FakeRequest(), loggedIn, stubMessages(), appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("panel-title").text shouldBe "You've removed this security preference"
      document.getElementById("view-all-apps").attr("href") shouldBe "/developer/applications"
      document.getElementById("back-to-security-prefs").attr("href") shouldBe "/developer/profile/security-preferences"
    }
  }
}
