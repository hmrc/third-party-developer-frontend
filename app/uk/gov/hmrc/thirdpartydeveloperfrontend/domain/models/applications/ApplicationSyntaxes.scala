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

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, AccessType}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
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

    def allows(capability: Capability, developer: User, permission: Permission): Boolean = {
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

    def canViewServerToken(developer: User): Boolean = {
      import Collaborator.Roles._

      (app.deployedTo, app.access.accessType, app.state.name, app.roleFor(developer.userId)) match {
        case (Environment.SANDBOX, AccessType.STANDARD, State.PRODUCTION, _)                      => true
        case (Environment.PRODUCTION, AccessType.STANDARD, State.PRODUCTION, Some(ADMINISTRATOR)) => true
        case _                                                                                    => false
      }
    }

    def canPerformApprovalProcess(developer: User): Boolean = {
      import Collaborator.Roles._

      (app.deployedTo, app.access.accessType, app.state.name, app.roleFor(developer.userId)) match {
        case (Environment.SANDBOX, _, _, _)                                                                           => false
        case (Environment.PRODUCTION, AccessType.STANDARD, State.TESTING, Some(ADMINISTRATOR))                        => true
        case (Environment.PRODUCTION, AccessType.STANDARD, State.PENDING_GATEKEEPER_APPROVAL, Some(ADMINISTRATOR))    => true
        case (Environment.PRODUCTION, AccessType.STANDARD, State.PENDING_REQUESTER_VERIFICATION, Some(ADMINISTRATOR)) => true
        case _                                                                                                        => false
      }
    }

    def isPermittedToAgreeToTermsOfUse(developer: User): Boolean          = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndAdmin)
    def isPermittedToEditAppDetails(developer: User): Boolean             = allows(Capabilities.SupportsDetails, developer, Permissions.SandboxOnly)
    def isPermittedToEditProductionAppDetails(developer: User): Boolean   = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndAdmin)
    def isProductionAppButEditDetailsNotAllowed(developer: User): Boolean = allows(Capabilities.SupportsDetails, developer, Permissions.ProductionAndOnlyDeveloper)
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
