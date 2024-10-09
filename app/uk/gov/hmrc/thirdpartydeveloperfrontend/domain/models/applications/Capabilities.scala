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

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators

sealed trait Capability {
  def hasCapability(app: ApplicationWithCollaborators): Boolean
}

object Capabilities {

  trait StandardAppCapability extends Capability {
    final def hasCapability(app: ApplicationWithCollaborators): Boolean = app.isStandard
  }

  case object Guaranteed extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = true
  }

  case object SupportsTermsOfUse extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = true
  }

  case object ViewCredentials extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = true
  }

  case object ChangeClientSecret extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = app.isApproved
  }

  case object SupportsTeamMembers extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = true
  }

  case object SupportsSubscriptions extends StandardAppCapability

  case object EditSubscriptionFields extends Capability {
    override def hasCapability(app: ApplicationWithCollaborators): Boolean = !app.isPendingGatekeeperApproval
  }

  case object SupportsDetails extends StandardAppCapability

  case object ManageLockedSubscriptions extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = app.areSubscriptionsLocked
  }

  case object SupportsRedirects extends StandardAppCapability

  case object SupportsDeletion extends StandardAppCapability

  case object SupportsAppChecks extends Capability {

    def hasSubmissions(access: Access): Boolean                   = access match {
      case Access.Standard(_, _, _, _, _, importantSubmissionData) => importantSubmissionData.nonEmpty
      case _                                                       => false
    }
    def hasCapability(app: ApplicationWithCollaborators): Boolean = app.isInTesting && false == hasSubmissions(app.access)
  }

  case object SupportChangingAppDetails extends Capability {
    def hasCapability(app: ApplicationWithCollaborators): Boolean = app.isInTesting || app.deployedTo.isSandbox
  }

  case object SupportsIpAllowlist extends Capability {
    def hasCapability(app: ApplicationWithCollaborators): Boolean = true
  }

  case object SupportsResponsibleIndividual extends Capability {
    def hasCapability(app: ApplicationWithCollaborators): Boolean = app.isProduction
  }

  case object ViewPushSecret extends Capability {
    def hasCapability(app: ApplicationWithCollaborators) = true
  }
}
