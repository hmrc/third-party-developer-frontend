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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import java.time.{LocalDateTime, Period}

import play.api.libs.json.{OFormat, Reads}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{
  PrivacyPolicyLocation,
  PrivacyPolicyLocations,
  TermsAndConditionsLocation,
  TermsAndConditionsLocations
}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType.STANDARD
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, SupportsDetails, ViewPushSecret}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{ProductionAndAdmin, ProductionAndDeveloper, SandboxOnly, SandboxOrAdmin}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Developer
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.LocalDateTimeFormatters
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string.Digest
import java.time.temporal.ChronoUnit

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

  def role(email: LaxEmailAddress): Option[Collaborator.Role] = collaborators.find(_.emailAddress == email).map(_.role)

  def roleForCollaborator(userId: UserId): Option[Collaborator.Role] = collaborators.find(_.userId == userId).map(_.role)

  def isUserACollaboratorOfRole(userId: UserId, requiredRole: Collaborator.Role): Boolean = roleForCollaborator(userId).fold(false)(_ == requiredRole)

  def adminEmails: Set[LaxEmailAddress] = collaborators.filter(_.isAdministrator).map(_.emailAddress)

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

  def hasResponsibleIndividual: Boolean = {
    access match {
      case Standard(_, _, _, _, _, Some(_)) => true
      case _                                => false
    }
  }

  def privacyPolicyLocation: PrivacyPolicyLocation = access match {
    case Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) => privacyPolicyLocation
    case Standard(_, _, Some(url), _, _, None)                                                        => PrivacyPolicyLocations.Url(url)
    case _                                                                                            => PrivacyPolicyLocations.NoneProvided
  }

  def termsAndConditionsLocation: TermsAndConditionsLocation = access match {
    case Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, termsAndConditionsLocation, _, _))) => termsAndConditionsLocation
    case Standard(_, Some(url), _, _, _, None)                                                             => TermsAndConditionsLocations.Url(url)
    case _                                                                                                 => TermsAndConditionsLocations.NoneProvided
  }

  def isPermittedToEditAppDetails(developer: Developer): Boolean = allows(SupportsDetails, developer, SandboxOnly)

  def isPermittedToEditProductionAppDetails(developer: Developer): Boolean   = allows(SupportsDetails, developer, ProductionAndAdmin)
  def isProductionAppButEditDetailsNotAllowed(developer: Developer): Boolean = allows(SupportsDetails, developer, ProductionAndDeveloper)

  def isPermittedToAgreeToTermsOfUse(developer: Developer): Boolean = allows(SupportsDetails, developer, ProductionAndAdmin)

  /*
  Allows access to at least one of (client id, client secrets and server token) (where appropriate)
   */
  def canChangeClientCredentials(developer: Developer): Boolean = allows(ChangeClientSecret, developer, SandboxOrAdmin)

  def canPerformApprovalProcess(developer: Developer): Boolean = {
    import Collaborator.Roles._

    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (Environment.SANDBOX, _, _, _)                                                                => false
      case (Environment.PRODUCTION, STANDARD, State.TESTING, Some(ADMINISTRATOR))                        => true
      case (Environment.PRODUCTION, STANDARD, State.PENDING_GATEKEEPER_APPROVAL, Some(ADMINISTRATOR))    => true
      case (Environment.PRODUCTION, STANDARD, State.PENDING_REQUESTER_VERIFICATION, Some(ADMINISTRATOR)) => true
      case _                                                                                             => false
    }
  }

  def canViewServerToken(developer: Developer): Boolean = {
    import Collaborator.Roles._

    (deployedTo, access.accessType, state.name, role(developer.email)) match {
      case (Environment.SANDBOX, STANDARD, State.PRODUCTION, _)                      => true
      case (Environment.PRODUCTION, STANDARD, State.PRODUCTION, Some(ADMINISTRATOR)) => true
      case _                                                                         => false
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

  def isInTesting: Boolean            = state.isInTesting
  def isPendingApproval: Boolean      = state.name.isPendingApproval
  def isApproved: Boolean             = state.name.isApproved
  def hasLockedSubscriptions: Boolean = deployedTo.isProduction && !isInTesting

  def findCollaboratorByHash(teamMemberHash: String): Option[Collaborator] = {
    collaborators.find(c => c.emailAddress.text.toSha256 == teamMemberHash)
  }

  def grantLengthDisplayValue(): String =
    GrantLength.apply(grantLength.getDays).fold(s"${Math.round(grantLength.getDays.toFloat / 30)} months")(_.toString)
}

case class Application(
    id: ApplicationId,
    clientId: ClientId,
    name: String,
    createdOn: LocalDateTime,
    lastAccess: Option[LocalDateTime],
    lastAccessTokenUsage: Option[LocalDateTime] = None, // API-4376: Temporary inclusion whilst Server Token functionality is retired
    grantLength: Period,
    deployedTo: Environment,
    description: Option[String] = None,
    collaborators: Set[Collaborator] = Set.empty,
    access: Access = Standard(),
    state: ApplicationState = ApplicationState(updatedOn = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)),
    checkInformation: Option[CheckInformation] = None,
    ipAllowlist: IpAllowlist = IpAllowlist()
  ) extends BaseApplication

object Application {
  import play.api.libs.json.Json

  implicit val applicationFormat: OFormat[Application] = Json.format[Application]
  implicit val ordering: Ordering[Application]         = Ordering.by(_.name)
}

case class ApplicationWithSubscriptionIds(
    id: ApplicationId,
    clientId: ClientId,
    name: String,
    createdOn: LocalDateTime,
    lastAccess: Option[LocalDateTime],
    lastAccessTokenUsage: Option[LocalDateTime] = None,
    grantLength: Period = Period.ofDays(547),
    deployedTo: Environment,
    description: Option[String] = None,
    collaborators: Set[Collaborator] = Set.empty,
    access: Access = Standard(),
    state: ApplicationState = ApplicationState(updatedOn = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)),
    checkInformation: Option[CheckInformation] = None,
    ipAllowlist: IpAllowlist = IpAllowlist(),
    subscriptions: Set[ApiIdentifier] = Set.empty
  ) extends BaseApplication

object ApplicationWithSubscriptionIds extends LocalDateTimeFormatters {
  import play.api.libs.json.Json

  implicit val applicationWithSubsIdsReads: Reads[ApplicationWithSubscriptionIds] = Json.reads[ApplicationWithSubscriptionIds]
  implicit val ordering: Ordering[ApplicationWithSubscriptionIds]                 = Ordering.by(_.name)

  def from(app: Application): ApplicationWithSubscriptionIds =
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
