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
import views.helper.CommonViewSpec

import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppStartView
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class AuthAppStartViewSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with UserTestData
    with DeveloperSessionBuilder
    with LocalUserIdTracker
    with StubMessagesFactory {
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val authAppStartView                                      = app.injector.instanceOf[AuthAppStartView]
  implicit val loggedIn: UserSession                        = JoeBloggs.loggedIn

  "AuthAppStartView view" should {
    "render correctly when form is valid" in {
      val mainView = authAppStartView.apply()(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "You need an authenticator app on your device"
      document.getElementById("submit").text shouldBe "Continue"
    }

  }
}
