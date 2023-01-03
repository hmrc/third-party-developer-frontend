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
import play.api.test.FakeRequest
import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaId, SmsMfaDetailSummary}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.time.LocalDateTime


class SecurityPreferencesViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {
  implicit val request = FakeRequest()
  val securityPreferencesView = app.injector.instanceOf[SecurityPreferencesView]

  "SecurityPreferences view" should {
    val developer = buildDeveloper()
    val authAppMfaDetail = AuthenticatorAppMfaDetailSummary(MfaId(java.util.UUID.randomUUID()), "name", LocalDateTime.now(), verified = true)
    val smsMfaDetail = SmsMfaDetailSummary(MfaId(java.util.UUID.randomUUID()), "name", LocalDateTime.now(), mobileNumber = "1234567890", verified = true)
    val session = Session("sessionId", developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)
    implicit val developerSession = DeveloperSession(session)

    "show suggest 'Get access codes by text message' and display auth app details when developer has only auth app set up" in {
      val mainView = securityPreferencesView.apply(List(authAppMfaDetail))

      val document = Jsoup.parse(mainView.body)
      document.getElementById("no-sms-mfa").text() should include("Get access codes by text message")
    }

    "show suggest 'Get access codes by an authenticator app' and display sms mfa details when developer has only sms mfa set up" in {
      val mainView = securityPreferencesView.apply(List(smsMfaDetail))

      val document = Jsoup.parse(mainView.body)
     document.getElementById("no-auth-app-mfa").text() should include("Get access codes by an authenticator app")
    }




  }
}
