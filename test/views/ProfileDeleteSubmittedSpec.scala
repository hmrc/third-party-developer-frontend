/*
 * Copyright 2021 HM Revenue & Customs
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

import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ProfileDeleteSubmittedView

class ProfileDeleteSubmittedSpec extends CommonViewSpec with WithCSRFAddToken {
  "Profile delete submitted page" should {
    val profileDeleteSubmittedView = app.injector.instanceOf[ProfileDeleteSubmittedView]

    "render with no errors" in {
      val request = FakeRequest().withCSRFToken

      val developer = utils.DeveloperSession("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

      val page = profileDeleteSubmittedView.render(request, developer, appConfig, messagesProvider, "details")
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Request submitted") shouldBe true
    }
  }
}
