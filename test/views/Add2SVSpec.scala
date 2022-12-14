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

package views

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.Add2SVView
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker

class Add2SVSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with DeveloperSessionBuilder with LocalUserIdTracker {

  val add2SVView = app.injector.instanceOf[Add2SVView]

  implicit val loggedInDeveloper = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("admin@example.com", "firstName1", "lastName1", None))
  implicit val request = FakeRequest().withCSRFToken

  val developer = buildDeveloper()
  val session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
  implicit val developerSession = DeveloperSession(session)

  private def renderPage(isAdminOnProductionApp: Boolean): Html = {
    add2SVView.render(isAdminOnProductionApp,  messagesProvider, developerSession, request, appConfig)
  }

  "I Cant do this right now" should {
    "not be displayed when user is an admin" in {
      val page = renderPage(true)

      page.contentType should include("text/html")
      page.body should not include "I can't do this right now"
    }

    "displayed when user is an admin" in {
      val page = renderPage(false)

      page.contentType should include("text/html")
      page.body should include("I can't do this right now")
    }

  }
}
