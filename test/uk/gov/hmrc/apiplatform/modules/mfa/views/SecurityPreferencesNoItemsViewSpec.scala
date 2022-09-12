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

package uk.gov.hmrc.apiplatform.modules.mfa.views

import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesNoItemsView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec


class SecurityPreferencesNoItemsViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {
  implicit val request = FakeRequest()
  val securityPreferencesNoItemsView = app.injector.instanceOf[SecurityPreferencesNoItemsView]

  "SecurityPreferencesNoItemsView view" should {
    "render" in {
      val mainView = securityPreferencesNoItemsView.apply()
      val document = Jsoup.parse(mainView.body)
      document.getElementById("paragraph").text shouldBe "You need to set up additional security so only you can sign in."
      document.getElementById("button").text shouldBe "Continue"

    }

  }
}
