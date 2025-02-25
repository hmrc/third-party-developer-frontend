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

import play.api.libs.json.{Json, OFormat}

class UserNotFound       extends RuntimeException("User not found")
class InvalidCredentials extends RuntimeException("Login failed")
class InvalidEmail       extends RuntimeException("Invalid email")
class LockedAccount      extends RuntimeException("Account is locked")
class UnverifiedAccount  extends RuntimeException("Account is unverified")
class InvalidResetCode   extends RuntimeException("Invalid reset code")

class DeskproTicketCreationFailed(reason: String) extends RuntimeException(s"Failed to create deskpro ticket: $reason") {

  lazy val displayMessage =
    """Sorry, we're experiencing technical difficulties.
      |Your name has not been submitted. Please try again later.
      |""".stripMargin
}

case class Error(code: ErrorCode, message: String)

object Error {
  implicit val formatError: OFormat[Error] = Json.format[Error]

  object BadRequestError extends Error(ErrorCode.BAD_REQUEST, "Bad Request")
}
