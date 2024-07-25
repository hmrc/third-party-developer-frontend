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

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SecurityPreferencesView
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{AuthenticatorAppMfaDetail, MfaId, SmsMfaDetail}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class SecurityPreferencesViewSpec extends CommonViewSpec with WithCSRFAddToken with UserTestData with DeveloperSessionBuilder with LocalUserIdTracker with FixedClock {
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val securityPreferencesView: SecurityPreferencesView      = app.injector.instanceOf[SecurityPreferencesView]

  "SecurityPreferences view" should {
    val authAppMfaDetail                  = AuthenticatorAppMfaDetail(MfaId(java.util.UUID.randomUUID()), "name", instant, verified = true)
    val smsMfaDetail                      = SmsMfaDetail(MfaId(java.util.UUID.randomUUID()), "name", instant, mobileNumber = "1234567890", verified = true)
    implicit val userSession: UserSession = buildTrackedUser().partLoggedInEnablingMFA

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
