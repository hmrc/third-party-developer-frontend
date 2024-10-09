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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User

trait ApplicationSyntaxes {

  implicit class ApplicationWithCollaboratorsSyntax(app: ApplicationWithCollaborators) {
    def termsOfUseStatus: TermsOfUseStatus = {
      val termsOfUseAgreementsAreEmpty = app.details.checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty).isEmpty

      if (app.details.deployedTo.isSandbox || !app.details.access.isStandard) {
        TermsOfUseStatus.NOT_APPLICABLE
      } else if (termsOfUseAgreementsAreEmpty) {
        TermsOfUseStatus.AGREEMENT_REQUIRED
      } else {
        TermsOfUseStatus.AGREED
      }
    }

    def findCollaboratorByHash(teamMemberHash: String): Option[Collaborator] = {
      import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
      app.collaborators.find(c => c.emailAddress.text.toSha256 == teamMemberHash)
    }

    def hasCapability(capability: Capability): Boolean = capability.hasCapability(app)

    private def allows(capability: Capability, developer: User, permission: Permission): Boolean = {
      hasCapability(capability) && permits(developer, permission)
    }

    def permits(developer: User, permission: Permission = Permissions.SandboxOrAdmin): Boolean = {
      permission.hasPermissions(app, developer)
    }

    def hasRedirectUri(redirectUri: RedirectUri): Boolean = app.access match {
      case s: Access.Standard => s.redirectUris.contains(redirectUri)
      case _                  => false
    }

    def termsOfUseAgreements: List[TermsOfUseAgreement] = app.details.checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty)

    def canChangeClientCredentials(developer: User): Boolean = allows(Capabilities.ChangeClientSecret, developer, Permissions.SandboxOrAdmin)

    def canViewPushSecret(developer: User): Boolean = {
      allows(Capabilities.ViewPushSecret, developer, Permissions.SandboxOrAdmin)
    }

    // TODO - move canAdd to domain model
    private val maximumNumberOfRedirectUris = 5

    def canAddRedirectUri: Boolean = app.access match {
      case s: Access.Standard => s.redirectUris.lengthCompare(maximumNumberOfRedirectUris) < 0
      case _                  => false
    }

    def hasResponsibleIndividual: Boolean = {
       app.access match {
          case Access.Standard(_, _, _, _, _, Some(_)) => true
          case _                                       => false
        }
      }

    def isPermittedToAgreeToTermsOfUse(developer: User): Boolean = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndAdmin)
    def isPermittedToEditAppDetails(developer: User): Boolean = allows(Capabilities.SupportsDetails, developer, Permissions.SandboxOnly)
    def isPermittedToEditProductionAppDetails(developer: User): Boolean   = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndAdmin)
    def isProductionAppButEditDetailsNotAllowed(developer: User): Boolean = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndDeveloper)
  }

  implicit class ApplicationWithSubscriptionsSyntax(app: ApplicationWithSubscriptions) {
    def termsOfUseStatus: TermsOfUseStatus = {
      val termsOfUseAgreementsAreEmpty = app.details.checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty).isEmpty

      if (app.details.deployedTo.isSandbox || !app.details.access.isStandard) {
        TermsOfUseStatus.NOT_APPLICABLE
      } else if (termsOfUseAgreementsAreEmpty) {
        TermsOfUseStatus.AGREEMENT_REQUIRED
      } else {
        TermsOfUseStatus.AGREED
      }
    }

    def findCollaboratorByHash(teamMemberHash: String): Option[Collaborator] = {
      import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
      app.collaborators.find(c => c.emailAddress.text.toSha256 == teamMemberHash)
    }
  }
}

object ApplicationSyntaxes extends ApplicationSyntaxes

      // def determineTermsOfUseStatus(app: ApplicationWithCollaborators): TermsOfUseStatus = {
      //     val termsOfUseAgreementsAreEmpty = app.details.checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty).isEmpty

      //     if (app.details.deployedTo.isSandbox || !app.details.access.isStandard) {
      //       TermsOfUseStatus.NOT_APPLICABLE
      //     } else if (termsOfUseAgreementsAreEmpty) {
      //       TermsOfUseStatus.AGREEMENT_REQUIRED
      //     } else {
      //       TermsOfUseStatus.AGREED
      //     }
      // }
// trait BaseApplication {
//   val defaultGrantLengthDays = 547

//   def id: ApplicationId
//   def clientId: ClientId
//   def name: String
//   def createdOn: Instant
//   def lastAccess: Option[Instant]

//   def grantLength: Period
//   def lastAccessTokenUsage: Option[Instant]
//   def deployedTo: Environment
//   def description: Option[String]
//   def collaborators: Set[Collaborator]
//   def access: Access
//   def state: ApplicationState
//   def checkInformation: Option[CheckInformation]
//   def ipAllowlist: IpAllowlist

//   // def role(email: LaxEmailAddress): Option[Collaborator.Role] = collaborators.find(_.emailAddress == email).map(_.role)
//   def role(userId: UserId): Option[Collaborator.Role] = collaborators.find(_.userId == userId).map(_.role)

//   def roleForCollaborator(userId: UserId): Option[Collaborator.Role] = collaborators.find(_.userId == userId).map(_.role)

//   def isUserACollaboratorOfRole(userId: UserId, requiredRole: Collaborator.Role): Boolean = roleForCollaborator(userId).fold(false)(_ == requiredRole)

//   def termsOfUseAgreements: List[TermsOfUseAgreement] = checkInformation.map(_.termsOfUseAgreements).getOrElse(List.empty)

//   def hasCapability(capability: Capability): Boolean = capability.hasCapability(this)

//   def allows(capability: Capability, developer: User, permission: Permission): Boolean = {
//     hasCapability(capability) && permits(developer, permission)
//   }

//   def permits(developer: User, permission: Permission = SandboxOrAdmin): Boolean = {
//     permission.hasPermissions(this, developer)
//   }

//   def termsOfUseStatus: TermsOfUseStatus = {
//     if (deployedTo.isSandbox || access.accessType != AccessType.STANDARD) {
//       TermsOfUseStatus.NOT_APPLICABLE
//     } else if (termsOfUseAgreements.isEmpty) {
//       TermsOfUseStatus.AGREEMENT_REQUIRED
//     } else {
//       TermsOfUseStatus.AGREED
//     }
//   }

//   def hasResponsibleIndividual: Boolean = {
//     access match {
//       case Access.Standard(_, _, _, _, _, Some(_)) => true
//       case _                                       => false
//     }
//   }

//   // def privacyPolicyLocation: PrivacyPolicyLocation = access match {
//   //   case Access.Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, _, privacyPolicyLocation, _))) => privacyPolicyLocation
//   //   case Access.Standard(_, _, Some(url), _, _, None)                                                        => PrivacyPolicyLocations.Url(url)
//   //   case _                                                                                                   => PrivacyPolicyLocations.NoneProvided
//   // }

//   // def termsAndConditionsLocation: TermsAndConditionsLocation = access match {
//   //   case Access.Standard(_, _, _, _, _, Some(ImportantSubmissionData(_, _, _, termsAndConditionsLocation, _, _))) => termsAndConditionsLocation
//   //   case Access.Standard(_, Some(url), _, _, _, None)                                                             => TermsAndConditionsLocations.Url(url)
//   //   case _                                                                                                        => TermsAndConditionsLocations.NoneProvided
//   // }

//   def isPermittedToEditAppDetails(developer: User): Boolean = allows(SupportsDetails, developer, SandboxOnly)

//   def isPermittedToEditProductionAppDetails(developer: User): Boolean   = allows(SupportsDetails, developer, ProductionAndAdmin)
//   def isProductionAppButEditDetailsNotAllowed(developer: User): Boolean = allows(SupportsDetails, developer, ProductionAndDeveloper)

//   def isPermittedToAgreeToTermsOfUse(developer: User): Boolean = allows(SupportsDetails, developer, ProductionAndAdmin)

//   /*
//   Allows access to at least one of (client id, client secrets and server token) (where appropriate)
//    */
//   def canChangeClientCredentials(developer: User): Boolean = allows(ChangeClientSecret, developer, SandboxOrAdmin)

//   def canPerformApprovalProcess(developer: User): Boolean = {
//     import Collaborator.Roles._

//     (deployedTo, access.accessType, state.name, role(developer.userId)) match {
//       case (Environment.SANDBOX, _, _, _)                                                                           => false
//       case (Environment.PRODUCTION, AccessType.STANDARD, State.TESTING, Some(ADMINISTRATOR))                        => true
//       case (Environment.PRODUCTION, AccessType.STANDARD, State.PENDING_GATEKEEPER_APPROVAL, Some(ADMINISTRATOR))    => true
//       case (Environment.PRODUCTION, AccessType.STANDARD, State.PENDING_REQUESTER_VERIFICATION, Some(ADMINISTRATOR)) => true
//       case _                                                                                                        => false
//     }
//   }

//   def canViewServerToken(developer: User): Boolean = {
//     import Collaborator.Roles._

//     (deployedTo, access.accessType, state.name, role(developer.userId)) match {
//       case (Environment.SANDBOX, AccessType.STANDARD, State.PRODUCTION, _)                      => true
//       case (Environment.PRODUCTION, AccessType.STANDARD, State.PRODUCTION, Some(ADMINISTRATOR)) => true
//       case _                                                                                    => false
//     }
//   }

//   def canViewPushSecret(developer: User): Boolean = {
//     allows(ViewPushSecret, developer, SandboxOrAdmin)
//   }

//   private val maximumNumberOfRedirectUris = 5

//   def canAddRedirectUri: Boolean = access match {
//     case s: Access.Standard => s.redirectUris.lengthCompare(maximumNumberOfRedirectUris) < 0
//     case _                  => false
//   }

//   def hasRedirectUri(redirectUri: RedirectUri): Boolean = access match {
//     case s: Access.Standard => s.redirectUris.contains(redirectUri)
//     case _                  => false
//   }

//   def findCollaboratorByHash(teamMemberHash: String): Option[Collaborator] = {
//     collaborators.find(c => c.emailAddress.text.toSha256 == teamMemberHash)
//   }

//   // def grantLengthDisplayValue(): String =
//   //   GrantLength.apply(grantLength.getDays).fold(s"${Math.round(grantLength.getDays.toFloat / 30)} months")(_.toString)
// }

// case class Application(
//     id: ApplicationId,
//     clientId: ClientId,
//     name: String,
//     createdOn: Instant,
//     lastAccess: Option[Instant],
//     lastAccessTokenUsage: Option[Instant] = None, // API-4376: Temporary inclusion whilst Server Token functionality is retired
//     grantLength: Period,
//     deployedTo: Environment,
//     description: Option[String] = None,
//     collaborators: Set[Collaborator] = Set.empty,
//     access: Access = Access.Standard(),
//     state: ApplicationState = ApplicationState(updatedOn = Instant.now().truncatedTo(ChronoUnit.MILLIS)),
//     checkInformation: Option[CheckInformation] = None,
//     ipAllowlist: IpAllowlist = IpAllowlist()
//   ) extends BaseApplication

// object Application {
//   import play.api.libs.json.Json

//   implicit val applicationFormat: OFormat[Application] = Json.format[Application]
//   implicit val ordering: Ordering[Application]         = Ordering.by(_.name)
// }

// case class ApplicationWithSubscriptionIds(
//     id: ApplicationId,
//     clientId: ClientId,
//     name: String,
//     createdOn: Instant,
//     lastAccess: Option[Instant],
//     lastAccessTokenUsage: Option[Instant] = None,
//     grantLength: Period = Period.ofDays(547),
//     deployedTo: Environment,
//     description: Option[String] = None,
//     collaborators: Set[Collaborator] = Set.empty,
//     access: Access = Access.Standard(),
//     state: ApplicationState = ApplicationState(updatedOn = Instant.now().truncatedTo(ChronoUnit.MILLIS)),
//     checkInformation: Option[CheckInformation] = None,
//     ipAllowlist: IpAllowlist = IpAllowlist(),
//     subscriptions: Set[ApiIdentifier] = Set.empty
//   ) extends BaseApplication

// object ApplicationWithSubscriptionIds {
//   import play.api.libs.json.Json
//   import uk.gov.hmrc.apiplatform.modules.common.domain.services.InstantJsonFormatter.WithTimeZone._

//   implicit val applicationWithSubsIdsReads: Reads[ApplicationWithSubscriptionIds] = Json.reads[ApplicationWithSubscriptionIds]
//   implicit val ordering: Ordering[ApplicationWithSubscriptionIds]                 = Ordering.by(_.name)

//   def from(app: Application): ApplicationWithSubscriptionIds =
//     ApplicationWithSubscriptionIds(
//       app.id,
//       app.clientId,
//       app.name,
//       app.createdOn,
//       app.lastAccess,
//       app.lastAccessTokenUsage,
//       app.grantLength,
//       app.deployedTo,
//       app.description,
//       app.collaborators,
//       app.access,
//       app.state,
//       app.checkInformation,
//       app.ipAllowlist,
//       Set.empty
//     )
// }
