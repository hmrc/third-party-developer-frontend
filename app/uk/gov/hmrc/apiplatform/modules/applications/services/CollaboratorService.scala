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

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import cats.data.NonEmptyList

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

@Singleton
class CollaboratorService @Inject() (
    apmConnector: ApmConnector,
    applicationCommandConnector: BridgedConnector[ApplicationCommandConnector],
    developerConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext
  ) {

  def addTeamMember(
      app: Application,
      newTeamMemberEmail: LaxEmailAddress,
      newTeamMemberRole: Collaborator.Role,
      requestingEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[Either[NonEmptyList[CommandFailure], DispatchSuccessResult]] = {
    val setOfAdminEmails = app.collaborators.filter(_.isAdministrator).map(_.emailAddress)

    for {
      adminsAsUsers <- developerConnector.fetchByEmails(setOfAdminEmails)
      adminsToEmail  = adminsAsUsers.filter(_.verified.contains(true)).map(_.email).toSet
      userId        <- developerConnector.getOrCreateUserId(newTeamMemberEmail)
      addCommand     = AddCollaborator(Actors.AppCollaborator(requestingEmail), Collaborator.apply(newTeamMemberEmail, newTeamMemberRole, userId), LocalDateTime.now())
      response      <- applicationCommandConnector(app).dispatch(app.id, addCommand, adminsToEmail)
    } yield response
  }

  def determineOtherAdmins(collaborators: Set[Collaborator], removeThese: Set[LaxEmailAddress]): Set[LaxEmailAddress] = {
    collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .--(removeThese)
  }

  def removeTeamMember(
      app: Application,
      teamMemberToRemove: LaxEmailAddress,
      requestingEmail: LaxEmailAddress
    )(implicit hc: HeaderCarrier
    ): Future[Either[NonEmptyList[CommandFailure], DispatchSuccessResult]] = {
    val otherAdminEmails = determineOtherAdmins(app.collaborators, Set(requestingEmail, teamMemberToRemove))

    val collaboratorToRemove = app.collaborators.filter(_.emailAddress == teamMemberToRemove).head
    for {
      otherAdmins  <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email).toSet
      removeCommand = RemoveCollaborator(Actors.AppCollaborator(requestingEmail), collaboratorToRemove, LocalDateTime.now())
      response     <- applicationCommandConnector(app).dispatch(app.id, removeCommand, adminsToEmail)
    } yield response
  }

  trait ApplicationConnector {

    def removeTeamMember(
        applicationId: ApplicationId,
        teamMemberToDelete: LaxEmailAddress,
        requestingEmail: LaxEmailAddress,
        adminsToEmail: Set[LaxEmailAddress]
      )(implicit hc: HeaderCarrier
      ): Future[ApplicationUpdateSuccessful]

  }
}