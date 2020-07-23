/*
 * Copyright 2020 HM Revenue & Customs
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

import domain.{Developer, DeveloperSession, LoggedInState, Session}
import model.MfaMandateDetails
import org.joda.time.LocalDate
import org.mockito.BDDMockito.given
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.Add2SVView

class Add2SVSpec extends CommonViewSpec with WithCSRFAddToken {

  val add2SVView = app.injector.instanceOf[Add2SVView]

  implicit val loggedInUser = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
  implicit val request = FakeRequest().withCSRFToken

  val developer = Developer("email", "firstName", "lastName")
  val session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
  implicit val developerSession = DeveloperSession(session)

  private def renderPage(mfaMandateDetails: MfaMandateDetails): Html = {
    add2SVView.render(mfaMandateDetails, messagesProvider, developerSession, request, appConfig)
  }

  "MFA Admin warning" should {
    "not be displayed" in {
      val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = false, daysTillAdminMfaMandate = 0))

      page.contentType should include("text/html")
      page.body should not include "If you are the Administrator of an application you have"
    }

    "is displayed with plural 'days remaining'" in {
      given(appConfig.dateOfAdminMfaMandate).willReturn(Some(new LocalDate().plusDays(1)))

      val daysRemaining = 10
      val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = true, daysTillAdminMfaMandate = daysRemaining))

      page.contentType should include("text/html")

      page.body should include(s"If you are the Administrator of an application you have $daysRemaining days until 2-step verification is mandatory")
    }

    "is displayed with singular 'day remaining'" in {
      given(appConfig.dateOfAdminMfaMandate).willReturn(Some(new LocalDate().plusDays(1)))

      val daysRemaining = 1
      val page = renderPage(MfaMandateDetails(showAdminMfaMandatedMessage = true, daysTillAdminMfaMandate = daysRemaining))

      page.contentType should include("text/html")

      page.body should include(s"If you are the Administrator of an application you have $daysRemaining day until 2-step verification is mandatory")
    }
  }
}
