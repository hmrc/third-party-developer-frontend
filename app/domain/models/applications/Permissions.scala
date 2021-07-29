/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.applications

import domain.models.developers.Developer

sealed trait Permission {
  def hasPermissions(app: BaseApplication, developer: Developer): Boolean
}

object Permissions {
  case object SandboxOrAdmin extends Permission {
    override def hasPermissions(app: BaseApplication, developer: Developer): Boolean =
      (app.deployedTo, app.role(developer.email)) match {
        case (Environment.SANDBOX, _) => true
        case (_, Some(CollaboratorRole.ADMINISTRATOR)) => true
        case _ => false
      }
  }

  case object AdministratorOnly extends Permission {
    override def hasPermissions(app: BaseApplication, developer: Developer): Boolean =
      app.role(developer.email).contains(CollaboratorRole.ADMINISTRATOR)
  }

  case object TeamMembersOnly extends Permission {
    override def hasPermissions(app: BaseApplication, developer: Developer): Boolean =
      app.role(developer.email).isDefined
  }
  case object Unrestricted extends Permission {
    override def hasPermissions(app: BaseApplication, developer: Developer): Boolean = true
  }
}
