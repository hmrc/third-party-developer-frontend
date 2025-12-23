/*
 * Copyright 2025 HM Revenue & Customs
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

package views.include.teamMembers

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator.Role
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession

object TeamMember {

  def verified(user: Option[User]): Boolean = {
    user match {
      case Some(u) => u.verified
      case None    => false
    }
  }

  private def oneOnlyVerifiedAdmin(collaboratorUsers: Seq[User], appCollaborators: Set[Collaborator]) = {
    val admins: Set[Collaborator] = appCollaborators.filter(_.isAdministrator)
    collaboratorUsers.filter(u => admins.exists(_.emailAddress == u.email)).count(_.verified) == 1
  }

  def displayUnverified(collaborator: Collaborator, collaboratorUsers: Seq[User]): Boolean = {
    unregistered(collaborator, collaboratorUsers) || !verified(collaboratorUsers.find(_.email == collaborator.emailAddress))
  }

  private def unregistered(collaborator: Collaborator, collaboratorUsers: Seq[User]) = {
    !collaboratorUsers.exists(_.email == collaborator.emailAddress)
  }

  def loggedInUserIsOnlyVerifiedAdmin(role: Role, loggedIn: UserSession, collaboratorUsers: Seq[User], appCollaborators: Set[Collaborator]): Boolean = {
    role.isAdministrator && loggedIn.developer.verified && oneOnlyVerifiedAdmin(collaboratorUsers, appCollaborators)
  }
}
