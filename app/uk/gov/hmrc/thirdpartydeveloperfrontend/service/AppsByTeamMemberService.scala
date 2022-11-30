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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment._

import scala.util.control.NonFatal
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionIds
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.ADMINISTRATOR

@Singleton
class AppsByTeamMemberService @Inject() (
    connectorWrapper: ConnectorsWrapper
)(implicit val ec: ExecutionContext) {

  def fetchAppsByTeamMember(environment: Environment)(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]] = {
    connectorWrapper.forEnvironment(environment).thirdPartyApplicationConnector.fetchByTeamMember(userId).map(_.sorted)
  }

  def fetchByTeamMemberWithRole(environment: Environment)(requiredRole: CollaboratorRole)(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]] = 
    fetchAppsByTeamMember(environment)(userId).map { apps =>
      apps.filter(_.isUserACollaboratorOfRole(userId, requiredRole))
    }

  def fetchProductionSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationSummary]] =
    fetchAppsByTeamMember(PRODUCTION)(userId).map(_.sorted.map(ApplicationSummary.from(_, userId)))

  def fetchProductionSummariesByAdmin(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]] =
    fetchByTeamMemberWithRole(PRODUCTION)(ADMINISTRATOR)(userId: UserId)

  def fetchSandboxAppsByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]] =
    fetchAppsByTeamMember(SANDBOX)(userId) recover { case NonFatal(_) => Seq.empty }

  def fetchSandboxSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationSummary]] =
    fetchSandboxAppsByTeamMember(userId).map(_.sorted.map(ApplicationSummary.from(_, userId)))

  def fetchSandboxSummariesByAdmin(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationSummary]] = {
    fetchSandboxSummariesByTeamMember(userId).map(_.filter(_.role.isAdministrator))
  }

  def fetchAllSummariesByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[(Seq[ApplicationSummary], Seq[ApplicationSummary])] = {
    for {
      productionSummaries <- fetchProductionSummariesByTeamMember(userId)
      sandboxSummaries <- fetchSandboxSummariesByTeamMember(userId)
    } yield (sandboxSummaries, productionSummaries)
  }
}
