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

package domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import play.api.libs.json.{Format, Json}
import play.api.mvc.Session

import scala.concurrent.Future

// TODO: add session
// TODO: Rename this to DeveloperSession
case class Developer(email: String,
                     firstName: String,
                     lastName: String,
                     organisation: Option[String] = None,
                     mfaEnabled: Option[Boolean] = None,
                     loggedInState: LoggedInState) {
  val displayedName = s"$firstName $lastName"
  val displayedNameEncoded: String = URLEncoder.encode(displayedName, StandardCharsets.UTF_8.toString)

  def isMfaEnabled: Boolean = mfaEnabled.getOrElse(false)
}

// TODO : Rename this to Developer?
case class DeveloperDto(email: String,
                        firstName: String,
                        lastName: String,
                        organisation: Option[String] = None,
                        mfaEnabled: Option[Boolean] = None) {
  val displayedName = s"$firstName $lastName"
  val displayedNameEncoded: String = URLEncoder.encode(displayedName, StandardCharsets.UTF_8.toString)

  def isMfaEnabled: Boolean = mfaEnabled.getOrElse(false)
}

object Developer {
  implicit val format: Format[Developer] = Json.format[Developer]

  // TODO: Test me
  implicit def apply(session: Session) : Developer = {
    Developer(session.developer.email,
      session.developer.firstName,
      session.developer.lastName,
      session.developer.organisation,
      session.developer.mfaEnabled,
      session.loggedInState)
  }
}

object DeveloperDto {
  implicit val format: Format[DeveloperDto] = Json.format[DeveloperDto]
}

sealed trait UserStatus {
  val app: Future[Application]
}

case object LoggedInUser extends UserStatus {
  override val app: Future[Application] = Future.failed(new IllegalStateException("Unsupported"))
}

case object PartLoggedInEnablingMfa extends UserStatus {
  override val app: Future[Application] = Future.failed(new IllegalStateException("Unsupported"))
}

case class AppAdmin(override val app: Future[Application]) extends UserStatus

case class AppTeamMember(override val app: Future[Application]) extends UserStatus

case class User(email: String, verified: Option[Boolean])

object User {
  implicit val format: Format[User] = Json.format[User]
}
