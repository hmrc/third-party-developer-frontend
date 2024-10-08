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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators

sealed trait Permission {
  def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean
}

object Permissions {

  case object SandboxOrAdmin extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.isSandbox || app.isAdministrator(developer.userId)
  }

  case object ProductionAndAdmin extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.isProduction && app.isAdministrator(developer.userId)
  }

  case object ProductionAndDeveloper extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.isProduction && app.isCollaborator(developer.userId)
  }

  case object SandboxOnly extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.deployedTo match {
        case Environment.SANDBOX => true
        case _                   => false
      }
  }

  case object AdministratorOnly extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.isAdministrator(developer.userId)
  }

  case object TeamMembersOnly extends Permission {

    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean =
      app.isCollaborator(developer.userId)
  }

  case object Unrestricted extends Permission {
    override def hasPermissions(app: ApplicationWithCollaborators, developer: User): Boolean = true
  }
}
