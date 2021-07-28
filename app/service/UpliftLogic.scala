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

package services

import service.AppsByTeamMemberService
import domain.models.applications.ApplicationId
import domain.models.apidefinitions.ApiContext
import domain.models.developers.UserId
import connectors.ApmConnector
import javax.inject.{Inject, Singleton}
import connectors.ThirdPartyApplicationSandboxConnector
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext
import domain.models.apidefinitions.ApiContext
import domain.models.subscriptions.ApiData
import domain.models.subscriptions.ApiCategory
import domain.models.apidefinitions.ApiIdentifier
import domain.models.applications.Environment
import domain.models.controllers.ApplicationSummary
import scala.collection.parallel.ForkJoinTaskSupport

@Singleton
class UpliftLogic @Inject()(
  apmConnector: ApmConnector,
  sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
  appsByTeamMember: AppsByTeamMemberService
)( implicit ec: ExecutionContext ) {

  private val forkJoinPool = new java.util.concurrent.ForkJoinPool(5)

  private val taskSupport = new ForkJoinTaskSupport(forkJoinPool)
  
  import UpliftLogic._

  private def fetchSubscriptionsForApps(appIds: Seq[ApplicationId])(implicit hc: HeaderCarrier): Future[Map[ApplicationId, Set[ApiIdentifier]]] = {
    val parallelAppIds = appIds.par
    parallelAppIds.tasksupport = taskSupport

    Future.sequence(
      parallelAppIds.map( appId => 
        sandboxApplicationConnector.fetchSubscription(appId).map(
            subs => (appId,subs)
        )
      ).seq
    ).map( _.toMap )
  }

  def aUsersSandboxAdminSummariesAndUpliftIds(userId: UserId)(implicit hc: HeaderCarrier): Future[(List[ApplicationSummary], Set[ApplicationId])] = {
    // Concurrent requests
    val fApisAvailableInProd = apmConnector.fetchUpliftableApiIdentifiers
    val fAllSandboxApiDetails = apmConnector.fetchAllApis(Environment.SANDBOX)
    val fAllSummaries = appsByTeamMember.fetchSandboxSummariesByAdmin(userId)

    for {
      apisAvailableInProd <- fApisAvailableInProd
      excludedContexts <- fAllSandboxApiDetails.map(contextsOfTestSupportAndExampleApis)
      allSummaries <- fAllSummaries

      subscriptionsForApps <- fetchSubscriptionsForApps(allSummaries.map(_.id))

      upliftableAppIds = filterAppsHavingRealAndAvailableSubscriptions(apisAvailableInProd, excludedContexts, subscriptionsForApps).keySet
    } yield (allSummaries.toList, upliftableAppIds)
  }
}

object UpliftLogic {
  def contextsOfTestSupportAndExampleApis(apis: Map[ApiContext, ApiData]): Set[ApiContext] = {
    apis.toSet[(ApiContext,ApiData)].flatMap {
      case (k: ApiContext, d: ApiData) =>
        if(d.isTestSupport || d.categories.contains(ApiCategory.EXAMPLE)) 
          Set(k)
        else
          Set.empty[ApiContext]
    }
  }

  def filterAppsHavingRealAndAvailableSubscriptions(apisAvailableInProd: Set[ApiIdentifier], excludedContexts: Set[ApiContext], subscriptionsByApplication: Map[ApplicationId, Set[ApiIdentifier]]): Map[ApplicationId, Set[ApiIdentifier]] =
    subscriptionsByApplication.flatMap { 
      case (id, subs) =>
        val realApis = subs.filterNot(id => excludedContexts.contains(id.context))
        
        if(realApis.nonEmpty && realApis.subsetOf(apisAvailableInProd) )
          Map(id -> realApis) 
        else
          Map.empty[ApplicationId, Set[ApiIdentifier]] 
    }
}