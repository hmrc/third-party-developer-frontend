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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import play.api.libs.json.{EnvReads, EnvWrites, Format, OFormat, Reads}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{LocalDateTime, Period}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType.STANDARD
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, SupportsDetails, ViewPushSecret}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{ProductionAndAdmin, ProductionAndDeveloper, SandboxOnly, SandboxOrAdmin}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.ADMINISTRATOR
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.State.{PENDING_GATEKEEPER_APPROVAL, PENDING_REQUESTER_VERIFICATION, TESTING}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Developer
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string.Digest

import java.util.UUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier

case class ApplicationId(value: String) extends AnyVal

object ApplicationId {
  import play.api.libs.json.Json
  implicit val applicationIdFormat: Format[ApplicationId] = Json.valueFormat[ApplicationId]

  def random: ApplicationId = ApplicationId(UUID.randomUUID().toString)
}

case class ClientId(value: String) extends AnyVal


object ClientId {
  import play.api.libs.json.Json
  implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]
}

trait BaseApplication {
  val defaultGrantLengthDays = 547
  def id: ApplicationId
  def clientId: ClientId
  def name: String
  def createdOn: LocalDateTime
  def lastAccess: Option[LocalDateTime]

  def grantLength: Period
  def lastAccessTokenUsage: Option[LocalDateTime]
  def deployedTo: Environment
  def description: Option[String]
  def collaborators: Set[Collaborator]
  def access: Access
  def state: ApplicationState
  def checkInformation: Option[CheckInformation]
  def ipAllowlist: IpAllowlist

  def role(email: String): Option[CollaboratorRole] = collaborators.find(_.emailAddress == email).map(_.role)
  def roleForCollaborator(userId: UserId): Option[CollaboratorRole] = collaborators.find(_.userId == userId).map(_.role)

  def isUserACollaboratorOfRole(userId: UserId, requiredRole: CollaboratorRole): Boolean = roleForCollaborator(userId).fold(false)(_ == requiredRole)


  def adminEmails: Set[String] = collaborators.filter(_.role.isAdministrator).map(_.emailAddress)

  def termsOfUseAgreements: List[TermsOfUseAgreement] = checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty)

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

  def privacyPolicyLocation = access match {
    case Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) => privacyPolicyLocation
    case Standard(_, _, Some(url), _, _, None)                                                        => PrivacyPolicyLocation.Url(url)
    case _                                                                                            => PrivacyPolicyLocation.NoneProvided
  }

  def termsAndConditionsLocation = access match {
    case Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, termsAndConditionsLocation, _, _))) => termsAndConditionsLocation
    case Standard(_, Some(url), _, _, _, None)                                                             => TermsAndConditionsLocation.Url(url)
    case _                                                                                                 => TermsAndConditionsLocation.NoneProvided
  }

  def isPermittedToEditAppDetails(developer: Developer): Boolean = allows(SupportsDetails, developer, SandboxOnly)

  def isPermittedToEditProductionAppDetails(developer: Developer): Boolean = allows(SupportsDetails, developer, ProductionAndAdmin)
  def isProductionAppButEditDetailsNotAllowed(developer: Developer): Boolean = allows(SupportsDetails, developer, ProductionAndDeveloper)

  def isPermittedToAgreeToTermsOfUse(developer: Developer): Boolean = allows(SupportsDetails, developer, ProductionAndAdmin)

  /*
  Allows access to at least one of (client id, client secrets and server token) (where appropriate)
   */
  def canChangeClientCredentials(developer: Developer): Boolean = allows(ChangeClientSecret, developer, SandboxOrAdmin)

  def canPerformApprovalProcess(developer: Developer): Boolean = {
    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (SANDBOX, _, _, _)                                                          => false
      case (PRODUCTION, STANDARD, TESTING, Some(ADMINISTRATOR))                        => true
      case (PRODUCTION, STANDARD, PENDING_GATEKEEPER_APPROVAL, Some(ADMINISTRATOR))    => true
      case (PRODUCTION, STANDARD, PENDING_REQUESTER_VERIFICATION, Some(ADMINISTRATOR)) => true
      case _                                                                           => false
    }
  }

  def canViewServerToken(developer: Developer): Boolean = {
    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (SANDBOX, STANDARD, State.PRODUCTION, _)                      => true
      case (PRODUCTION, STANDARD, State.PRODUCTION, Some(ADMINISTRATOR)) => true
      case _                                                             => false
    }
  }

  def canViewPushSecret(developer: Developer): Boolean = {
    allows(ViewPushSecret, developer, SandboxOrAdmin)
  }

  private val maximumNumberOfRedirectUris = 5

  def canAddRedirectUri: Boolean = access match {
    case s: Standard => s.redirectUris.lengthCompare(maximumNumberOfRedirectUris) < 0
    case _           => false
  }

  def hasRedirectUri(redirectUri: String): Boolean = access match {
    case s: Standard => s.redirectUris.contains(redirectUri)
    case _           => false
  }

  def isInTesting = state.isInTesting
  def isPendingApproval = state.isPendingApproval
  def isApproved = state.isApproved
  def hasLockedSubscriptions = deployedTo.isProduction && !isInTesting

  def findCollaboratorByHash(teamMemberHash: String): Option[Collaborator] = {
    collaborators.find(c => c.emailAddress.toSha256 == teamMemberHash)
  }

  def grantLengthDisplayValue() : String = {
    grantLength match {
      case GrantLength.MONTH =>  "1 month"
      case GrantLength.THREE_MONTHS =>  "3 months"
      case GrantLength.SIX_MONTHS =>  "6 months"
      case GrantLength.ONE_YEAR =>  "1 year"
      case GrantLength.EIGHTEEN_MONTHS =>  "18 months"
      case GrantLength.THREE_YEARS =>  "3 years"
      case GrantLength.FIVE_YEARS =>  "5 years"
      case GrantLength.TEN_YEARS =>  "10 years"
      case GrantLength.HUNDRED_YEARS =>  "100 years"
      case _ => s"${Math.round(grantLength.getDays/30)} months"
    }
  }
}

case class Application(
    val id: ApplicationId,
    val clientId: ClientId,
    val name: String,
    val createdOn: LocalDateTime,
    val lastAccess: Option[LocalDateTime],
    val lastAccessTokenUsage: Option[LocalDateTime] = None, // API-4376: Temporary inclusion whilst Server Token functionality is retired
    val grantLength: Period,
    val deployedTo: Environment,
    val description: Option[String] = None,
    val collaborators: Set[Collaborator] = Set.empty,
    val access: Access = Standard(),
    val state: ApplicationState = ApplicationState.testing,
    val checkInformation: Option[CheckInformation] = None,
    val ipAllowlist: IpAllowlist = IpAllowlist())
    extends BaseApplication

object Application {
  import play.api.libs.json.Json

  implicit val applicationFormat: OFormat[Application] = Json.format[Application]
  implicit val ordering: Ordering[Application] = Ordering.by(_.name)
}

case class ApplicationWithSubscriptionIds(
    val id: ApplicationId,
    val clientId: ClientId,
    val name: String,
    val createdOn: LocalDateTime,
    val lastAccess: Option[LocalDateTime],
    val lastAccessTokenUsage: Option[LocalDateTime] = None,
    val grantLength: Period = Period.ofDays(547),
    val deployedTo: Environment,
    val description: Option[String] = None,
    val collaborators: Set[Collaborator] = Set.empty,
    val access: Access = Standard(),
    val state: ApplicationState = ApplicationState.testing,
    val checkInformation: Option[CheckInformation] = None,
    val ipAllowlist: IpAllowlist = IpAllowlist(),
    val subscriptions: Set[ApiIdentifier] = Set.empty)
    extends BaseApplication

object ApplicationWithSubscriptionIds {
  import play.api.libs.json.Json
  import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._

  implicit val dateFormat: Format[LocalDateTime] = MongoJavatimeFormats.localDateTimeFormat
  implicit val applicationWithSubsIdsReads: Reads[ApplicationWithSubscriptionIds] = Json.reads[ApplicationWithSubscriptionIds]
  implicit val ordering: Ordering[ApplicationWithSubscriptionIds] = Ordering.by(_.name)

  def from(app: Application) =
    ApplicationWithSubscriptionIds(
      app.id,
      app.clientId,
      app.name,
      app.createdOn,
      app.lastAccess,
      app.lastAccessTokenUsage,
      app.grantLength,
      app.deployedTo,
      app.description,
      app.collaborators,
      app.access,
      app.state,
      app.checkInformation,
      app.ipAllowlist,
      Set.empty
    )
}
