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

import controllers._
import domain.AccessType.STANDARD
import domain.Capabilities.{HasReachedProductionState, SupportsDetails}
import domain.Environment.{PRODUCTION, SANDBOX}
import domain.Permissions._
import domain.Role.ADMINISTRATOR
import domain.State.{PENDING_GATEKEEPER_APPROVAL, PENDING_REQUESTER_VERIFICATION, TESTING}
import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.play.json.Union
import uk.gov.hmrc.time.DateTimeUtils
import helpers.string._

case class UpliftRequest(applicationName: String, requestedByEmailAddress: String)

object UpliftRequest {
  implicit val format = Json.format[UpliftRequest]
}

case class ApplicationState(
    name: State,
    requestedByEmailAddress: Option[String],
    verificationCode: Option[String] = None,
    updatedOn: DateTime = DateTimeUtils.now
)

object ApplicationState {
  implicit val format = Json.format[ApplicationState]

  val testing = ApplicationState(State.TESTING, None)

  def pendingGatekeeperApproval(requestedBy: String) =
    ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(requestedBy))

  def pendingRequesterVerification(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(requestedBy), Some(verificationCode))

  def production(requestedBy: String, verificationCode: String) =
    ApplicationState(State.PRODUCTION, Some(requestedBy), Some(verificationCode))
}

case class Collaborator(emailAddress: String, role: Role)

object Collaborator {
  implicit val format = Json.format[Collaborator]
}

case class ClientSecret(name: String, secret: String, createdOn: DateTime, lastAccess: Option[DateTime] = None)

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

case class ClientSecretRequest(name: String)

object ClientSecretRequest {
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

  def fromAddApplicationJourney(user: DeveloperSession, form: AddApplicationNameForm, environment: Environment) = CreateApplicationRequest(
    form.applicationName.trim,
    environment,
    None,
    Seq(Collaborator(user.email, Role.ADMINISTRATOR))
  )
}

case class UpdateApplicationRequest(id: String,
                                    environment: Environment,
                                    name: String,
                                    description: Option[String] = None,
                                    access: Access = Standard(Seq.empty, None, None, Set.empty))

object UpdateApplicationRequest extends ApplicationRequest {

  implicit val format = Json.format[UpdateApplicationRequest]

  def from(form: EditApplicationForm, application: Application) = {
    val name = if (application.state.name == State.TESTING || application.deployedTo.isSandbox) {
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
                                apiSubscriptionsComplete: Boolean = false,
                                contactDetailsComplete: Boolean = false,
                                providedPrivacyPolicyURLComplete: Boolean = false,
                                providedTermsAndConditionsURLComplete: Boolean = false,
                                teamConfirmedComplete: Boolean = false,
                                termsOfUseAgreementComplete: Boolean = false)

case class CheckInformation(confirmedName: Boolean = false,
                            apiSubscriptionsConfirmed: Boolean = false,
                            contactDetails: Option[ContactDetails] = None,
                            providedPrivacyPolicyURL: Boolean = false,
                            providedTermsAndConditionsURL: Boolean = false,
                            teamConfirmed: Boolean = false,
                            termsOfUseAgreements: Seq[TermsOfUseAgreement] = Seq.empty)

object CheckInformation {
  implicit val format = Json.format[CheckInformation]
}

object CheckInformationForm {
  def fromCheckInformation(checkInformation: CheckInformation) = {
    CheckInformationForm(
      confirmedNameComplete = checkInformation.confirmedName,
      apiSubscriptionsComplete = checkInformation.apiSubscriptionsConfirmed,
      contactDetailsComplete = checkInformation.contactDetails.isDefined,
      providedPrivacyPolicyURLComplete = checkInformation.providedPrivacyPolicyURL,
      providedTermsAndConditionsURLComplete = checkInformation.providedTermsAndConditionsURL,
      teamConfirmedComplete = checkInformation.teamConfirmed,
      termsOfUseAgreementComplete = checkInformation.termsOfUseAgreements.exists(terms => terms.version.nonEmpty)
    )
  }
}

case class SubscriptionData(role: Role, application: Application, subscriptions: Option[GroupedSubscriptions])



case class Application(id: String,
                       clientId: String,
                       name: String,
                       createdOn: DateTime,
                       lastAccess: DateTime,
                       deployedTo: Environment,
                       description: Option[String] = None,
                       collaborators: Set[Collaborator] = Set.empty,
                       access: Access = Standard(),
                       state: ApplicationState = ApplicationState.testing,
                       checkInformation: Option[CheckInformation] = None,
                       ipWhitelist: Set[String] = Set.empty) {

  def role(email: String): Option[Role] = collaborators.find(_.emailAddress == email).map(_.role)

  def termsOfUseAgreements: Seq[TermsOfUseAgreement] = checkInformation.map(_.termsOfUseAgreements).getOrElse(Seq.empty)

  def hasCapability(capability: Capability): Boolean = capability.hasCapability(this)

  def allows(capability: Capability, developer: Developer, permission: Permission): Boolean = {
    hasCapability(capability) && permits(developer, permission)
  }

  def permits(developer: Developer, permission: Permission = SandboxOrAdmin): Boolean = {
    permission.hasPermissions(this, developer)
  }

  def termsOfUseStatus: TermsOfUseStatus = {
    if (deployedTo.isSandbox || access.accessType.isNotStandard) {
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

  def isPermittedToEditAppDetails(developer: Developer): Boolean = allows(SupportsDetails, developer, SandboxOrAdmin)

  /*
  Allows access to at least one of (client id, client secrets and server token) (where appropriate)
   */
  def canViewClientCredentials(developer: Developer): Boolean = allows(HasReachedProductionState, developer, SandboxOrAdmin)

  def canPerformApprovalProcess(developer: Developer): Boolean = {
    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (Environment.SANDBOX, _, _, _) => false
      case (PRODUCTION, STANDARD, TESTING, Some(ADMINISTRATOR)) => true
      case (PRODUCTION, STANDARD, PENDING_GATEKEEPER_APPROVAL, Some(ADMINISTRATOR)) => true
      case (PRODUCTION, STANDARD, PENDING_REQUESTER_VERIFICATION, Some(ADMINISTRATOR)) => true
      case _ => false
    }
  }

  def canViewServerToken(developer: Developer): Boolean = {
    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (SANDBOX, STANDARD, State.PRODUCTION, _) => true
      case (PRODUCTION, STANDARD, State.PRODUCTION, Some(ADMINISTRATOR)) => true
      case _ => false
    }
  }

  private val maximumNumberOfRedirectUris = 5

  def canAddRedirectUri: Boolean = access match {
    case s: Standard => s.redirectUris.lengthCompare(maximumNumberOfRedirectUris) < 0
    case _ => false
  }

  def hasRedirectUri(redirectUri: String): Boolean = access match {
    case s: Standard => s.redirectUris.contains(redirectUri)
    case _ => false
  }

  def hasLockedSubscriptions = deployedTo.isProduction && state.name != State.TESTING

  def findCollaboratorByHash(teamMemberHash: String) : Option[Collaborator] = {
    collaborators.find(c => c.emailAddress.toSha256 == teamMemberHash)
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

class ApplicationAlreadyExists extends RuntimeException

class ApplicationNotFound extends RuntimeException

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
  implicit val format = Json.format[DeleteApplicationRequest]
}
