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

package service

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import domain.models.applications.Environment
import domain.models.developers.UserId
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import domain.models.applications.Application
import domain.models.applications.CollaboratorRole
import domain.models.controllers.ProductionApplicationSummary
import domain.models.controllers.SandboxApplicationSummary
import domain.models.applications.Environment._
import scala.util.control.NonFatal

@Singleton
class AppsByTeamMemberService @Inject() (
    connectorWrapper: ConnectorsWrapper
)(implicit val ec: ExecutionContext) {

  def fetchAppsByTeamMember(environment: Environment)(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[Application]] = {
    connectorWrapper.forEnvironment(environment).thirdPartyApplicationConnector.fetchByTeamMember(userId)
  }

  def fetchByTeamMemberWithRole(environment: Environment)(requiredRole: CollaboratorRole)(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[Application]] = 
    fetchAppsByTeamMember(environment)(userId).map { apps =>
      apps.filter(_.isUserACollaboratorOfRole(userId, requiredRole))
    }

  def fetchProductionSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ProductionApplicationSummary]] =
    fetchAppsByTeamMember(PRODUCTION)(userId).map(_.sorted.map(ProductionApplicationSummary.from(_, userId)))
  
  def fetchSandboxAppsByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[Application]] = fetchAppsByTeamMember(SANDBOX)(userId) recover { case NonFatal(_) => Seq.empty }

  def fetchSandboxSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[SandboxApplicationSummary]] =
    fetchSandboxAppsByTeamMember(userId).map(_.sorted.map(SandboxApplicationSummary.from(_, userId)))

  def fetchSandboxSummariesByAdmin(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[SandboxApplicationSummary]] = {
    fetchSandboxSummariesByTeamMember(userId).map(_.filter(_.role.isAdministrator))
  }

  def fetchAllSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[(Seq[SandboxApplicationSummary], Seq[ProductionApplicationSummary])] = {
    for {
      productionSummaries <- fetchProductionSummariesByTeamMember(userId)
      sandboxSummaries <- fetchSandboxSummariesByTeamMember(userId)
    } yield (sandboxSummaries, productionSummaries)
  }
}
