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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, DeveloperSession, Session, LoggedInState}

trait SampleSession {
  self: DeveloperBuilder => 

  lazy val developer: Developer = buildDeveloper()
  lazy val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
  lazy val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
  lazy val sessionId = "sessionId"

  val partLoggedInSessionId = "partLoggedInSessionId"
  lazy val partLoggedInSession: Session = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

}
