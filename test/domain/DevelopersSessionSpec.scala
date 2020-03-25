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

package domain

import java.util.UUID

import uk.gov.hmrc.play.test.UnitSpec

class DevelopersSessionSpec extends UnitSpec {
  val email = "thirdpartydeveloper@example.com"
  val firstName = "John"
  val lastName = "Doe"
  val developer = Developer(email, firstName = firstName, lastName = lastName)

  val loggedInSession = Session(UUID.randomUUID().toString, developer, LoggedInState.LOGGED_IN)
  val partLoggedInSession = Session(UUID.randomUUID().toString, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

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