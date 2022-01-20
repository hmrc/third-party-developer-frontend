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

package uk.gov.hmrc.modules.uplift.services

import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AppsByTeamMemberService
import domain.models.applications.ApplicationId
import domain.models.apidefinitions.ApiContext
import domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationSandboxConnector
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext
import domain.models.apidefinitions.ApiContext
import domain.models.subscriptions.ApiData
import domain.models.subscriptions.ApiCategory
import domain.models.apidefinitions.ApiIdentifier
import domain.models.applications.Environment
import domain.models.controllers.ApplicationSummary
import domain.models.apidefinitions.APIStatus

@Singleton
class UpliftLogic @Inject()(
  apmConnector: ApmConnector,
  sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
  appsByTeamMember: AppsByTeamMemberService
)(implicit ec: ExecutionContext) {

  import UpliftLogic._

  private def getSubscriptionsByApp(summaries: Seq[ApplicationSummary]): Map[ApplicationId, Set[ApiIdentifier]] = {
    summaries.map(s => s.id -> s.subscriptionIds).toMap
  }

  def aUsersSandboxAdminSummariesAndUpliftIds(userId: UserId)(implicit hc: HeaderCarrier): Future[UpliftLogic.Data] = {
    // Concurrent requests
    val fApisAvailableInProd = apmConnector.fetchUpliftableApiIdentifiers
    val fAllSandboxApiDetails = apmConnector.fetchAllApis(Environment.SANDBOX)
    val fAllSummaries = appsByTeamMember.fetchSandboxSummariesByTeamMember(userId)
    
    for {
      apisAvailableInProd     <- fApisAvailableInProd
      sandboxApis             <- fAllSandboxApiDetails
      allSummaries            <- fAllSummaries
      possibleUpliftSummaries = allSummaries.filter(s => s.role.isAdministrator && s.accessType.isStandard)
      
      subscriptionsForApps = getSubscriptionsByApp(possibleUpliftSummaries)

      upliftableAppIds = filterAppsHavingRealAndAvailableSubscriptions(sandboxApis, apisAvailableInProd, subscriptionsForApps).keySet
      invalidAppIds = possibleUpliftSummaries.map(_.id).toSet -- upliftableAppIds
    } yield UpliftLogic.Data(allSummaries.toList, upliftableAppIds, invalidAppIds)
  }
}

object UpliftLogic {
  case class Data(
    sandboxApplicationSummaries: List[ApplicationSummary],
    upliftableApplicationIds: Set[ApplicationId],
    notUpliftableApplicationIds: Set[ApplicationId]
  ) {
    lazy val upliftableSummaries = sandboxApplicationSummaries.filter(s => upliftableApplicationIds.contains(s.id))
    lazy val hasAppsThatCannotBeUplifted = notUpliftableApplicationIds.nonEmpty
  }

  def contextsOfTestSupportAndExampleApis(apis: Map[ApiContext, ApiData]): Set[ApiContext] = {
    ApiData.filterApis(d => d.isTestSupport || d.categories.contains(ApiCategory.EXAMPLE))(apis)
    .keySet
  }

  def apiIdentifiersOfRetiredApis(apis: Map[ApiContext, ApiData]): Set[ApiIdentifier] = {
    (ApiData.filterApis(_ => true, v => v.status == APIStatus.RETIRED) _ andThen ApiData.toApiIdentifiers)(apis)
  }

  def filterAppsHavingRealAndAvailableSubscriptions(
    sandboxApis: Map[ApiContext, ApiData],
    apisAvailableInProd: Set[ApiIdentifier], 
    subscriptionsByApplication: Map[ApplicationId, Set[ApiIdentifier]]
  ): Map[ApplicationId, Set[ApiIdentifier]] = {
  
    val excludedContexts = contextsOfTestSupportAndExampleApis(sandboxApis)
    val retiredVersions = apiIdentifiersOfRetiredApis(sandboxApis)
    
    subscriptionsByApplication.flatMap { 
      case (id, subs) =>
        val realApis = subs.filterNot(id => excludedContexts.contains(id.context) || retiredVersions.contains(id))
        
        if(realApis.nonEmpty && realApis.subsetOf(apisAvailableInProd) )
          Map(id -> realApis) 
        else
          Map.empty[ApplicationId, Set[ApiIdentifier]] 
    }
  }
}
