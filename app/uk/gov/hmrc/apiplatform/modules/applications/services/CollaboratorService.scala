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

import uk.gov.hmrc.apiplatform.modules.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.AddTeamMemberRequest

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class CollaboratorService @Inject()(apmConnector: ApmConnector, productionApplicationConnector: ThirdPartyApplicationProductionConnector,
sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector, developerConnector: ThirdPartyDeveloperConnector)
                                   (implicit val ec: ExecutionContext) {

    val bridgedConnector  = new BridgedConnector[ThirdPartyApplicationConnector](sandboxApplicationConnector, productionApplicationConnector)  
                                
  def addTeamMember(app: Application, requestingEmail: LaxEmailAddress, teamMember: AddCollaborator)(implicit hc: HeaderCarrier): Future[Unit] = {
    val request = AddTeamMemberRequest(teamMember.emailAddress, teamMember.role, Some(requestingEmail))
    apmConnector.addTeamMember(app.id, request)
  }

  def removeTeamMember(app: Application, teamMemberToRemove: LaxEmailAddress, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    val otherAdminEmails = app.collaborators
      .filter(_.role.isAdministrator)
      .map(_.emailAddress)
      .filterNot(_ == requestingEmail)
      .filterNot(_ == teamMemberToRemove)

    for {
      otherAdmins  <- developerConnector.fetchByEmails(otherAdminEmails)
      adminsToEmail = otherAdmins.filter(_.verified.contains(true)).map(_.email).toSet
      connector     = bridgedConnector.forEnvironment(app.deployedTo)
      response     <- connector.removeTeamMember(app.id, teamMemberToRemove, requestingEmail, adminsToEmail)
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
