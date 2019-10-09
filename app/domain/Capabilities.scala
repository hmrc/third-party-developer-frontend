/*
 * Copyright 2019 HM Revenue & Customs
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

sealed trait Capability {
  def hasCapability(app: Application): Boolean
}

object Capabilities {
  trait StandardAppCapability extends Capability {
    final def hasCapability(app: Application): Boolean = app.access.accessType.isStandard
  }

  case object Guaranteed extends Capability {
    def hasCapability(app: Application) = true
  }

  case object SupportsTermsOfUse extends Capability {
    def hasCapability(app: Application) = true
  }

  case object ViewCredentials extends Capability {
    def hasCapability(app: Application) = true
  }
  case object EditCredentials extends StandardAppCapability
  case object ChangeClientSecret extends StandardAppCapability

  case object SupportsProductionClientSecret extends Capability {
    def hasCapability(app: Application): Boolean = app.access.accessType.isStandard && app.state.name == State.PRODUCTION
  }

  case object SupportsTeamMembers extends StandardAppCapability

  case object SupportsSubscriptions extends StandardAppCapability

  case object SupportsDetails extends StandardAppCapability

  case object HasLockedSubscriptions extends Capability {
    def hasCapability(app: Application) = app.hasLockedSubscriptions
  }

  case object SupportsRedirects extends StandardAppCapability

  case object SupportsDeletion extends StandardAppCapability

  case object SupportsInTesting extends Capability {
    def hasCapability(app: Application): Boolean = app.state.name == State.TESTING
  }
}
