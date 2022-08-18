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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, ApplicationWithSubscriptionIds, ClientId, Collaborator, CollaboratorRole, Environment, IpAllowlist, Standard}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

import java.time.{LocalDateTime, Period}
import scala.concurrent.Future

trait UserIsAdminOnApplicationTeam extends MockConnectors with UserIsAuthenticated {
  val access = Standard()
  val collaborators = Set(Collaborator(user.email, CollaboratorRole.ADMINISTRATOR, user.userId))
  val application = Application(
    ApplicationId.random, ClientId.random, "my app", LocalDateTime.now, None, None, Period.ofYears(1), Environment.PRODUCTION, None, collaborators, access,
    ApplicationState.production("mr requester", "code123"), None, IpAllowlist(false, Set.empty)
  )
  val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
}
