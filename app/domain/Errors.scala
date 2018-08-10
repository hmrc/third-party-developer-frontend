/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.i18n.Messages.Implicits._
import play.api.Play.current

class InvalidCredentials extends RuntimeException("Login failed")
class InvalidEmail extends RuntimeException("Invalid email")
class LockedAccount extends RuntimeException("Account is locked")
class UnverifiedAccount extends RuntimeException("Account is unverified")
class InvalidResetCode extends RuntimeException("Invalid reset code")
class DeskproTicketCreationFailed(reason:String) extends RuntimeException(s"Failed to create deskpro ticket: $reason") {
  lazy val displayMessage =
    """Sorry, we're experiencing technical difficulties.
      |Your name has not been submitted. Please try again later.
      |""".stripMargin
}

case class Error(code: ErrorCode.Value, message: String)

object Error {
  implicit val format1 = EnumJson.enumFormat(ErrorCode)
  implicit val format2 = Json.format[Error]
}

object LockedAccountError extends Error(ErrorCode.LOCKED_ACCOUNT, "Locked Account")
object BadRequestError extends Error(ErrorCode.BAD_REQUEST, "Bad Request")
object InvalidPasswordError extends Error(ErrorCode.INVALID_PASSWORD, "Invalid password")
object PasswordRequiredError extends Error(ErrorCode.PASSWORD_REQUIRED, "Password is required")
object TeamMemberAlreadyExistsInApplication extends Error(ErrorCode.USER_ALREADY_EXISTS, Messages("team.member.error.emailAddress.already.exists.field"))

object ErrorCode extends Enumeration {

  type ErrorCode = Value

  val LOCKED_ACCOUNT = Value("LOCKED_ACCOUNT")
  val BAD_REQUEST = Value("BAD_REQUEST")
  val INVALID_PASSWORD = Value("INVALID_PASSWORD")
  val PASSWORD_REQUIRED = Value("PASSWORD_REQUIRED")
  val USER_ALREADY_EXISTS = Value("USER_ALREADY_EXISTS")

  implicit val format = EnumJson.enumFormat(ErrorCode)
}
