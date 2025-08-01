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

package uk.gov.hmrc.apiplatform.modules.applications.services

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{DispatchSuccessResult, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._

@Singleton
class CollaboratorService @Inject() (
    apmCmdModule: ApmConnectorCommandModule,
    developerConnector: ThirdPartyDeveloperConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  def addTeamMember(
      app: ApplicationWithCollaborators,
      newTeamMemberEmail: LaxEmailAddress,
      newTeamMemberRole: Collaborator.Role,
      requestingEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): AppCmdResult = {
    val setOfAdminEmails = app.collaborators.filter(_.isAdministrator).map(_.emailAddress)

    for {
      adminsAsUsers <- developerConnector.fetchByEmails(setOfAdminEmails)
      adminsToEmail  = adminsAsUsers.filter(_.verified).map(_.email).toSet
      userId        <- developerConnector.getOrCreateUserId(newTeamMemberEmail) // TODO - if we adding an admin, check they are registered and verified.
      addCommand     = ApplicationCommands.AddCollaborator(Actors.AppCollaborator(requestingEmail), Collaborator.apply(newTeamMemberEmail, newTeamMemberRole, userId), instant())
      response      <- apmCmdModule.dispatch(app.id, addCommand, adminsToEmail)
    } yield response
  }

  def determineOtherAdmins(collaborators: Set[Collaborator], removeThese: Set[LaxEmailAddress]): Set[LaxEmailAddress] = {
    collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .--(removeThese)
  }

  def removeTeamMember(
      app: ApplicationWithCollaborators,
      teamMemberToRemove: LaxEmailAddress,
      requestingEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): AppCmdResult = {
    val otherAdminEmails = determineOtherAdmins(app.collaborators, Set(requestingEmail, teamMemberToRemove))

    val collaboratorToRemove = app.collaborators.filter(_.emailAddress == teamMemberToRemove).head
    for {
      otherAdmins  <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified).map(_.email).toSet
      removeCommand = ApplicationCommands.RemoveCollaborator(Actors.AppCollaborator(requestingEmail), collaboratorToRemove, instant())
      response     <- apmCmdModule.dispatch(app.id, removeCommand, adminsToEmail)
    } yield response
  }
}
