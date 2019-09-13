/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.domain

import java.util.UUID

import domain.{Developer, DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.play.test.UnitSpec

class DevelopersSessionSpec extends UnitSpec {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(UUID.randomUUID().toString, developer, LoggedInState.LOGGED_IN)
  val expectedDeveloper = DeveloperSession("thirdpartydeveloper@example.com", "John", "Doe", None, None, LoggedInState.LOGGED_IN)

  "Developer.apply" should {
    "create a Developer when passed in a Session" in {
      val dev = DeveloperSession.createDeveloper(session)
      dev shouldBe expectedDeveloper
    }
  }
}