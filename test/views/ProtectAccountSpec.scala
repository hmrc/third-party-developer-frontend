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
import play.api.test.FakeRequest
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.protectaccount.ProtectAccountView

class ProtectAccountSpec extends CommonViewSpec with WithCSRFAddToken {
  implicit val request = FakeRequest()
  val protectAccountView = app.injector.instanceOf[ProtectAccountView]

  "Protect Account view" should {
    val developer = Developer("email", "firstName", "lastName")
    "show the sensitive account warning if doing the mandated MFA enablement journey" in {

      val session = Session("sessionId", developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)
      implicit val developerSession = DeveloperSession(session)

      val mainView = protectAccountView.apply()
      mainView.body should include("Your account has access to sensitive information, for example client secrets")
    }

    "Don't show the sensitive account warning if logged in and not doing the mandated MFA enablement journey" in {
      val session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
      implicit val developerSession = DeveloperSession(session)

      val mainView = protectAccountView.apply()
      mainView.body should not include("Your account has access to sensitive information, for example client secrets")
    }
  }
}
