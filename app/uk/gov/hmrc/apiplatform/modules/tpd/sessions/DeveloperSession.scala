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

package uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import play.api.libs.json.{Format, Json}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.Developer

case class DeveloperSession(session: Session) {
  val developer: Developer         = session.developer
  val email: LaxEmailAddress       = developer.email
  val loggedInState: LoggedInState = session.loggedInState

  val displayedName: String = s"${developer.firstName} ${developer.lastName}"

  val displayedNameEncoded: String =
    URLEncoder.encode(displayedName, StandardCharsets.UTF_8.toString)

  val loggedInName: Option[String] =
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
      developer: Developer
    ): DeveloperSession = {
    new DeveloperSession(
      Session(sessionId, developer = developer, loggedInState = loggedInState)
    )
  }
}

sealed trait UserStatus

case object loggedInDeveloper extends UserStatus

case object AtLeastPartLoggedInEnablingMfa extends UserStatus

case class User(email: LaxEmailAddress, verified: Option[Boolean])

object User {
  implicit val format: Format[User] = Json.format[User]
}
