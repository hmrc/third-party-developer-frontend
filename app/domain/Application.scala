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

import controllers.{AddApplicationForm, EditApplicationForm, GroupedSubscriptions, _}
import domain.Environment.Environment
import domain.Role.Role
import domain.State.State
import org.joda.time.DateTime
import play.api.libs.json.{Format, JsError, _}
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.time.DateTimeUtils

object Role extends Enumeration {
  type Role = Value
  val DEVELOPER, ADMINISTRATOR = Value

  def from(role: Option[String]) = role match {
    case Some (r) => Role.values.find(e => e.toString == r.toUpperCase)
    case _ => Some(Role.DEVELOPER)}

  implicit val format = EnumJson.enumFormat(Role)
}

case class UpliftRequest(applicationName: String, requestedByEmailAddress: String)

object UpliftRequest {
  implicit val format = Json.format[UpliftRequest]
}

object State extends Enumeration {
  type State = Value
  val TESTING, PENDING_GATEKEEPER_APPROVAL, PENDING_REQUESTER_VERIFICATION, PRODUCTION = Value

  implicit val format = EnumJson.enumFormat(State)
}

case class ApplicationState(name: State, requestedByEmailAddress: Option[String], verificationCode: Option[String] = None, updatedOn: DateTime = DateTimeUtils.now)

object ApplicationState {
  implicit val format1 = EnumJson.enumFormat(State)
  implicit val format = Json.format[ApplicationState]

  val testing = ApplicationState(State.TESTING, None)

  def pendingGatekeeperApproval(requestedBy: String) =
    ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(requestedBy))

  def pendingRequesterVerification(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(requestedBy), Some(verificationCode))

  def production(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PRODUCTION, Some(requestedBy), Some(verificationCode))
}

case class Collaborator(emailAddress: String, role: Role.Role)

object Collaborator {
  implicit val format = Json.format[Collaborator]
}


case class ClientSecret(name: String, secret: String, createdOn: DateTime)

object ClientSecret {
  implicit val format = Json.format[ClientSecret]
}

case class EnvironmentToken(clientId: String,
                            clientSecrets: Seq[ClientSecret],
                            accessToken: String)

object EnvironmentToken {
  implicit val format1 = Json.format[ClientSecret]
  implicit val format2 = Json.format[EnvironmentToken]
}

object Environment extends Enumeration {
  type Environment = Value
  val PRODUCTION, SANDBOX = Value

  def from(env: String) = Environment.values.find(e => e.toString == env.toUpperCase)

  implicit val format = EnumJson.enumFormat(Environment)
}

case class ClientSecretRequest(name: String)

object ClientSecretRequest {
  implicit val format1 = EnumJson.enumFormat(Environment)
  implicit val format2 = Json.format[ClientSecretRequest]
}

case class DeleteClientSecretsRequest(secrets: Seq[String])

object DeleteClientSecretsRequest {
  implicit val format = Json.format[DeleteClientSecretsRequest]
}

case class ApplicationTokens(production: EnvironmentToken)

object ApplicationTokens {
  implicit val format = Json.format[ApplicationTokens]
}

case class OverrideFlag(overrideType: String)

object OverrideFlag {
  val reads = Reads[OverrideFlag] {
    case JsString(value) => JsSuccess(OverrideFlag(value))
    case o: JsObject => Json.reads[OverrideFlag].reads(o)
    case _ => JsError()
  }

  val writes = Json.writes[OverrideFlag]
  implicit val format = Format(reads, writes)
}

object TermsOfUseStatus extends Enumeration {
  type TermsOfUseStatus = Value
  val NOT_APPLICABLE, AGREEMENT_REQUIRED, AGREED = Value
}

sealed trait Access {
  val accessType: AccessType
}

object Access {
  implicit val formatStandard = Json.format[Standard]
  implicit val formatPrivileged = Json.format[Privileged]
  implicit val formatROPC = Json.format[ROPC]
  implicit val format = Union.from[Access]("accessType")
    .and[Standard](AccessType.STANDARD.toString)
    .and[Privileged](AccessType.PRIVILEGED.toString)
    .and[ROPC](AccessType.ROPC.toString)
    .format
}


case class Standard(redirectUris: Seq[String] = Seq.empty,
                    termsAndConditionsUrl: Option[String] = None,
                    privacyPolicyUrl: Option[String] = None,
                    overrides: Set[OverrideFlag] = Set.empty) extends Access {
  override val accessType = AccessType.STANDARD
}

case class Privileged(scopes: Set[String] = Set.empty) extends Access {
  override val accessType = AccessType.PRIVILEGED
}

case class ROPC(scopes: Set[String] = Set.empty) extends Access {
  override val accessType = AccessType.ROPC
}

trait ApplicationRequest {
  protected def normalizeDescription(description: Option[String]) = description.map(_.trim.take(250))
}

case class CreateApplicationRequest(name: String,
                                    environment: Environment,
                                    description: Option[String],
                                    collaborators: Seq[Collaborator],
                                    access: Access = Standard(Seq.empty, None, None, Set.empty))

object CreateApplicationRequest extends ApplicationRequest {
  implicit val format = Json.format[CreateApplicationRequest]

  def from(user: Developer, application: AddApplicationForm) = CreateApplicationRequest(
    application.applicationName.trim,
    application.environment.flatMap(Environment.from).getOrElse(Environment.SANDBOX),
    normalizeDescription(application.description),
    Seq(Collaborator(user.email, Role.ADMINISTRATOR)))
}

case class UpdateApplicationRequest(id: String,
                                    environment: Environment,
                                    name: String,
                                    description: Option[String] = None,
                                    access: Access = Standard(Seq.empty, None, None, Set.empty))

object UpdateApplicationRequest extends ApplicationRequest {

  implicit val format = Json.format[UpdateApplicationRequest]

  def from(form: EditApplicationForm, application: Application) = {
    val name = if (application.state.name == State.TESTING || application.deployedTo == Environment.SANDBOX) {
      form.applicationName.trim
    } else {
      application.name
    }

    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      name,
      normalizeDescription(form.description),
      Standard(
        access.redirectUris.map(_.trim).filter(_.nonEmpty).distinct,
        form.termsAndConditionsUrl.map(_.trim),
        form.privacyPolicyUrl.map(_.trim)
      )
    )
  }

  def from(application: Application, form: AddRedirectForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = (access.redirectUris ++ Seq(form.redirectUri)).distinct)
    )
  }

  def from(application: Application, form: DeleteRedirectConfirmationForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = access.redirectUris.filter(uri => uri != form.redirectUri))
    )
  }

  def from(application: Application, form: ChangeRedirectForm): UpdateApplicationRequest = {
    val access = application.access.asInstanceOf[Standard]

    UpdateApplicationRequest(
      application.id,
      application.deployedTo,
      application.name,
      normalizeDescription(application.description),
      access.copy(redirectUris = access.redirectUris.map {
        case form.originalRedirectUri => form.newRedirectUri
        case s => s
      })
    )
  }
}

case class AddTeamMemberRequest(adminEmail: String, collaborator: Collaborator, isRegistered: Boolean, adminsToEmail: Set[String])

object AddTeamMemberRequest {
  implicit val format = Json.format[AddTeamMemberRequest]
}

case class AddTeamMemberResponse(registeredUser: Boolean)

object AddTeamMemberResponse {
  implicit val format = Json.format[AddTeamMemberResponse]
}

case class ContactDetails(fullname: String, email: String, telephoneNumber: String)

object ContactDetails {
  implicit val format = Json.format[ContactDetails]
}

case class TermsOfUseAgreement(emailAddress: String, timeStamp: DateTime, version: String)

object TermsOfUseAgreement {
  implicit val format = Json.format[TermsOfUseAgreement]
}

case class CheckInformationForm(confirmedNameComplete: Boolean = false,
                                applicationDetailsComplete: Boolean = false,
                                apiSubscriptionsComplete: Boolean = false,
                                contactDetailsComplete: Boolean = false,
                                providedPrivacyPolicyURLComplete: Boolean = false,
                                providedTermsAndConditionsURLComplete: Boolean = false,
                                termsOfUseAgreementComplete : Boolean = false)

case class CheckInformation(confirmedName: Boolean = false,
                            applicationDetails: Option[String] = None,
                            apiSubscriptionsConfirmed: Boolean = false,
                            contactDetails: Option[ContactDetails] = None,
                            providedPrivacyPolicyURL: Boolean = false,
                            providedTermsAndConditionsURL: Boolean = false,
                            termsOfUseAgreements: Seq[TermsOfUseAgreement] = Seq.empty)

object CheckInformation {
  implicit val format = Json.format[CheckInformation]
}

object CheckInformationForm {
  def fromCheckInformation(checkInformation: CheckInformation) = {
    CheckInformationForm(
      confirmedNameComplete = checkInformation.confirmedName,
      applicationDetailsComplete = checkInformation.applicationDetails.isDefined,
      apiSubscriptionsComplete = checkInformation.apiSubscriptionsConfirmed,
      contactDetailsComplete = checkInformation.contactDetails.isDefined,
      providedPrivacyPolicyURLComplete = checkInformation.providedPrivacyPolicyURL,
      providedTermsAndConditionsURLComplete = checkInformation.providedTermsAndConditionsURL,
      termsOfUseAgreementComplete = checkInformation.termsOfUseAgreements.exists(terms => terms.version.nonEmpty)
    )
  }
}

case class SubscriptionData (role: Role.Role, application: Application, subscriptions: Option[GroupedSubscriptions], hasSubscriptions: Boolean)

case class Application(id: String,
                       clientId: String,
                       name: String,
                       createdOn: DateTime,
                       deployedTo: Environment,
                       description: Option[String] = None,
                       collaborators: Set[Collaborator] = Set.empty,
                       access: Access = Standard(),
                       trusted: Boolean = false,
                       state: ApplicationState = ApplicationState.testing,
                       checkInformation: Option[CheckInformation] = None) {

  def role(email: String): Option[Role] = collaborators.find(_.emailAddress == email).map(_.role)

  def termsOfUseAgreements = checkInformation.map(_.termsOfUseAgreements).getOrElse(Seq.empty)

  def termsOfUseStatus = {
    if (deployedTo == Environment.SANDBOX || access.accessType != AccessType.STANDARD) {
      TermsOfUseStatus.NOT_APPLICABLE
    } else if (termsOfUseAgreements.isEmpty) {
      TermsOfUseStatus.AGREEMENT_REQUIRED
    } else {
      TermsOfUseStatus.AGREED
    }
  }

  def privacyPolicyUrl = access match {
    case x: Standard => x.privacyPolicyUrl
    case _ => None
  }

  def termsAndConditionsUrl = access match {
    case x: Standard => x.termsAndConditionsUrl
    case _ => None
  }

  def isPermittedToMakeChanges(role: Role) = (deployedTo, role) match {
    case (Environment.SANDBOX, _) => true
    case (_, Role.ADMINISTRATOR) => true
    case _ => false
  }

  def canAddRedirectUri = access match {
    case s: Standard => s.redirectUris.lengthCompare(5) < 0
    case _ => false
  }

  def hasRedirectUri(redirectUri: String): Boolean = access match {
    case s: Standard => s.redirectUris.contains(redirectUri)
    case _ => false
  }
}

object Application {
  implicit val applicationFormat = Json.format[Application]

  implicit val ordering: Ordering[Application] = Ordering.by(_.name)
}

case class ClientSecretResponse(clientSecret: String)

object ClientSecretResponse {
  implicit val format = Json.format[ClientSecretResponse]
}

class ApplicationAlreadyExists extends Throwable

class ApplicationNotFound extends Throwable

class ClientSecretLimitExceeded extends Throwable

class CannotDeleteOnlyClientSecret extends Throwable

class TeamMemberAlreadyExists extends RuntimeException("This user is already a teamMember on this application.")

class ApplicationNeedsAdmin extends Throwable

case class ApplicationCreatedResponse(id: String)

sealed trait ApplicationUpdateSuccessful

case object ApplicationUpdateSuccessful extends ApplicationUpdateSuccessful


sealed trait ApplicationUpliftSuccessful

case object ApplicationUpliftSuccessful extends ApplicationUpliftSuccessful

sealed trait ApplicationVerificationSuccessful

case object ApplicationVerificationSuccessful extends ApplicationVerificationSuccessful

class ApplicationVerificationFailed(verificationCode: String) extends Throwable

sealed trait VerifyPasswordSuccessful

case object VerifyPasswordSuccessful extends VerifyPasswordSuccessful
