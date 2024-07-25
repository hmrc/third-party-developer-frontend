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

package views

import views.helper.CommonViewSpec
import views.html.Add2SVView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class Add2SVSpec extends CommonViewSpec with WithCSRFAddToken with UserBuilder with DeveloperSessionBuilder with LocalUserIdTracker with UserTestData {

  val add2SVView = app.injector.instanceOf[Add2SVView]

  implicit val loggedInDeveloper: UserSession               = adminDeveloper.loggedIn
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

  val developer                         = buildTrackedUser()
  val session                           = UserSession(UserSessionId.random, LoggedInState.LOGGED_IN, developer)
  implicit val userSession: UserSession = session

  private def renderPage(isAdminOnProductionApp: Boolean): Html = {
    add2SVView.render(isAdminOnProductionApp, messagesProvider, userSession, request, appConfig)
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
