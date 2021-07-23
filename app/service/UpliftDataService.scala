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

import uk.gov.hmrc.http.HeaderCarrier
import javax.inject.{Inject, Singleton}
import domain.models.controllers.SandboxApplicationSummary
import domain.models.applications.ApplicationId
import connectors.ApmConnector
import domain.models.applications.Environment
import connectors.ThirdPartyApplicationSandboxConnector
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import domain.models.developers.UserId

@Singleton
class UpliftDataService @Inject() (
    apmConnector: ApmConnector,
    sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    appsByTeamMember: AppsByTeamMemberService
)(implicit ec: ExecutionContext) {
  
  def getUpliftData(userId: UserId)(implicit hc: HeaderCarrier): Future[(Seq[SandboxApplicationSummary], Boolean)] =
    appsByTeamMember.fetchSandboxSummariesByAdmin(userId)
    .flatMap { 
      case Nil              => throw new IllegalStateException("Should not be requesting with this data")
      case sandboxSummaries => 
        val appIds = sandboxSummaries.map(_.id)
        identifyUpliftableSandboxAppIds(appIds).map { upliftableApplicationIds =>
          val countOfUpliftable = upliftableApplicationIds.size
          val upliftableSummaries = sandboxSummaries.filter(s => upliftableApplicationIds.contains(s.id))
          val haveAppsThatCannotBeUplifted = countOfUpliftable < sandboxSummaries.size

          (upliftableSummaries, haveAppsThatCannotBeUplifted)
        }
  }

  def identifyUpliftableSandboxAppIds(sandboxApplicationIds: Seq[ApplicationId])(implicit hc: HeaderCarrier): Future[Set[ApplicationId]] = {
    val fUpliftableApiIdentifiers = apmConnector.fetchUpliftableApiIdentifiers
    val fApis = apmConnector.fetchAllApis(Environment.SANDBOX)
    val fMapOfAppIdsToApiIds = Future.sequence(
        sandboxApplicationIds.map( id => 
          sandboxApplicationConnector.fetchSubscription(id)
          .map(
            subs => (id,subs)
          )
        )
      ).map(_.toMap)

    for {
      upliftableApiIdentifiers    <- fUpliftableApiIdentifiers
      apis                        <- fApis
      mapOfAppIdsToApiIds         <- fMapOfAppIdsToApiIds
      filteredSubs1 = ApplicationService.filterSubscriptionsToRemoveTestAndExampleApis(apis)(mapOfAppIdsToApiIds)
      filteredSubs2 = ApplicationService.filterSubscriptionsForUplift(upliftableApiIdentifiers)(filteredSubs1)
    } yield filteredSubs2
  }
 
}
