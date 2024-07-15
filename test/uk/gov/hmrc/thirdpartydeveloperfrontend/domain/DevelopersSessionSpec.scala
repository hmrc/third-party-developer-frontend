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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{DeveloperSession, LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}

class DevelopersSessionSpec extends AsyncHmrcSpec with DeveloperBuilder with LocalUserIdTracker {
  val email     = "thirdpartydeveloper@example.com".toLaxEmail
  val firstName = "John"
  val lastName  = "Doe"
  val developer = buildDeveloper(emailAddress = email, firstName = firstName, lastName = lastName)

  val loggedInSession     = UserSession(UserSessionId.random, LoggedInState.LOGGED_IN, developer)
  val partLoggedInSession = UserSession(UserSessionId.random, LoggedInState.PART_LOGGED_IN_ENABLING_MFA, developer)

  "Developer.apply" should {
    "create a Developer when passed in a Session" in {
      val expectedDeveloper = DeveloperSession(loggedInSession.loggedInState, loggedInSession.sessionId, developer)

      val dev = DeveloperSession(loggedInSession)
      dev shouldBe expectedDeveloper
    }
  }

  "Logged in name" should {
    "be none" when {
      "Part logged in" in {
        DeveloperSession(session = partLoggedInSession).loggedInName shouldBe None
      }
    }

    "be the user's name" when {
      "logged in" in {
        DeveloperSession(session = loggedInSession).loggedInName shouldBe Some("John Doe")
      }
    }
  }
}
