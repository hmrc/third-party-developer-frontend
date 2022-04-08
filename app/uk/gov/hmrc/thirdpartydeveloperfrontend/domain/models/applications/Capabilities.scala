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

sealed trait Capability {
  def hasCapability(app: BaseApplication): Boolean
}

object Capabilities {
  trait StandardAppCapability extends Capability {
    final def hasCapability(app: BaseApplication): Boolean = app.access.accessType.isStandard
  }

  case object Guaranteed extends Capability {
    def hasCapability(app: BaseApplication) = true
  }

  case object SupportsTermsOfUse extends Capability {
    def hasCapability(app: BaseApplication) = true
  }

  case object ViewCredentials extends Capability {
    def hasCapability(app: BaseApplication) = true
  }

  case object ChangeClientSecret extends Capability {
    def hasCapability(app: BaseApplication) = app.isApproved
  }

  case object SupportsTeamMembers extends Capability {
    def hasCapability(app: BaseApplication) = true
  }

  case object SupportsSubscriptions extends StandardAppCapability

  case object EditSubscriptionFields extends Capability {
    override def hasCapability(app: BaseApplication): Boolean = !app.isPendingApproval
  }

  case object SupportsDetails extends StandardAppCapability

  case object ManageLockedSubscriptions extends Capability {
    def hasCapability(app: BaseApplication) = app.hasLockedSubscriptions
  }

  case object SupportsRedirects extends StandardAppCapability

  case object SupportsDeletion extends StandardAppCapability

  case object SupportsAppChecks extends Capability {
    def hasCapability(app: BaseApplication): Boolean = app.isInTesting
  }

  case object SupportChangingAppDetails extends Capability {
    def hasCapability(app: BaseApplication): Boolean = app.isInTesting || app.deployedTo.isSandbox
  }

  case object SupportsIpAllowlist extends Capability {
    def hasCapability(app: BaseApplication): Boolean = true
  }

  case object ViewPushSecret extends Capability {
    def hasCapability(app: BaseApplication) = true
  }
}
