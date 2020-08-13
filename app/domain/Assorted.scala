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

import domain.models.applications.{Access, Application, Capability, CheckInformation, ClientSecret, Collaborator, ContactDetails, Environment, OverrideFlag, Permission, Role, Standard, State, TermsOfUseAgreement, TermsOfUseStatus}
import domain.models.developers.DeveloperSession

case class AddTeamMemberRequest(adminEmail: String, collaborator: Collaborator, isRegistered: Boolean, adminsToEmail: Set[String])

object AddTeamMemberRequest {
  import play.api.libs.json._
  implicit val format = Json.format[AddTeamMemberRequest]
}

case class AddTeamMemberResponse(registeredUser: Boolean)

object AddTeamMemberResponse {
  import play.api.libs.json._
  implicit val format = Json.format[AddTeamMemberResponse]
}

case class ClientSecretResponse(clientSecret: String)

object ClientSecretResponse {
  import play.api.libs.json._
  implicit val format = Json.format[ClientSecretResponse]
}

class ApplicationAlreadyExists extends RuntimeException

class ApplicationNotFound extends RuntimeException

class ApiContextVersionNotFound extends RuntimeException

class ClientSecretLimitExceeded extends RuntimeException

class CannotDeleteOnlyClientSecret extends RuntimeException

class TeamMemberAlreadyExists extends RuntimeException("This user is already a teamMember on this application.")

class ApplicationNeedsAdmin extends RuntimeException

case class ApplicationCreatedResponse(id: String)

sealed trait ApplicationUpdateSuccessful

case object ApplicationUpdateSuccessful extends ApplicationUpdateSuccessful


sealed trait ApplicationUpliftSuccessful

case object ApplicationUpliftSuccessful extends ApplicationUpliftSuccessful

sealed trait ApplicationVerificationSuccessful

case object ApplicationVerificationSuccessful extends ApplicationVerificationSuccessful

class ApplicationVerificationFailed(verificationCode: String) extends RuntimeException

sealed trait VerifyPasswordSuccessful

case object VerifyPasswordSuccessful extends VerifyPasswordSuccessful

final case class DeleteApplicationRequest(requester: DeveloperSession)
object DeleteApplicationRequest {
  import play.api.libs.json._
  implicit val format = Json.format[DeleteApplicationRequest]
}
