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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session

import play.api.libs.json.{Format, Json}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}

case class DeveloperSession(session: UserSession) {
  lazy val developer: User              = session.developer
  lazy val email: LaxEmailAddress       = developer.email
  lazy val loggedInState: LoggedInState = session.loggedInState

  lazy val displayedName: String = developer.displayedName

  lazy val loggedInName: Option[String] =
    if (loggedInState.isLoggedIn) {
      Some(displayedName)
    } else {
      None
    }
}

object DeveloperSession {
  implicit val format: Format[DeveloperSession] = Json.format[DeveloperSession]

  def apply(
      loggedInState: LoggedInState,
      sessionId: UserSessionId,
      developer: User
    ): DeveloperSession = {
    new DeveloperSession(
      UserSession(sessionId, developer = developer, loggedInState = loggedInState)
    )
  }
}
