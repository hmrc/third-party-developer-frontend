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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, ApplicationWithSubscriptionData, ApplicationWithSubscriptionIds, ClientId, Collaborator, CollaboratorRole, Environment, IpAllowlist, Standard}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

import java.time.{LocalDateTime, Period}
import scala.concurrent.Future

trait UserIsAdminOnApplicationTeam extends MockConnectors with UserIsAuthenticated with HasApplicationId {
  this: ApplicationHasState =>

  val access = Standard()
  val collaborators = Set(Collaborator(user.email, CollaboratorRole.ADMINISTRATOR, user.userId))
  val application = Application(
    applicationId, ClientId.random, "my app", LocalDateTime.now, None, None, Period.ofYears(1), Environment.PRODUCTION, None, collaborators, access,
    applicationState, None, IpAllowlist(false, Set.empty)
  )
  val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
  val appWithSubsData = ApplicationWithSubscriptionData(application, Set.empty, Map.empty)

  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(apmConnector.fetchApplicationById(*[ApplicationId])(*)).thenReturn(Future.successful(Some(appWithSubsData)))
  when(apmConnector.getAllFieldDefinitions(*[Environment])(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchAllOpenAccessApis(*[Environment])(*)).thenReturn(Future.successful(Map.empty))
  when(apmConnector.fetchAllPossibleSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Map.empty))
}
