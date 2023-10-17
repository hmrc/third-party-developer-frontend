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

package uk.gov.hmrc.apiplatform.modules.uplift.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyApplicationSandboxConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AppsByTeamMemberService

@Singleton
class UpliftLogic @Inject() (
    apmConnector: ApmConnector,
    sandboxApplicationConnector: ThirdPartyApplicationSandboxConnector,
    appsByTeamMember: AppsByTeamMemberService
  )(implicit ec: ExecutionContext
  ) {

  import UpliftLogic._

  private def getSubscriptionsByApp(summaries: Seq[ApplicationSummary]): Map[ApplicationId, Set[ApiIdentifier]] = {
    summaries.map(s => s.id -> s.subscriptionIds).toMap
  }

  def aUsersSandboxAdminSummariesAndUpliftIds(userId: UserId)(implicit hc: HeaderCarrier): Future[UpliftLogic.Data] = {
    // Concurrent requests
    val fApisAvailableInProd  = apmConnector.fetchUpliftableApiIdentifiers
    val fAllSandboxApiDetails = apmConnector.fetchAllApis(Environment.SANDBOX)
    val fAllSummaries         = appsByTeamMember.fetchSandboxSummariesByTeamMember(userId)

    for {
      apisAvailableInProd    <- fApisAvailableInProd
      sandboxApis            <- fAllSandboxApiDetails
      allSummaries           <- fAllSummaries
      possibleUpliftSummaries = allSummaries.filter(s => s.role.isAdministrator && s.accessType.isStandard)

      subscriptionsForApps = getSubscriptionsByApp(possibleUpliftSummaries)

      upliftableAppIds = filterAppsHavingRealAndAvailableSubscriptions(sandboxApis, apisAvailableInProd, subscriptionsForApps).keySet
      invalidAppIds    = possibleUpliftSummaries.map(_.id).toSet -- upliftableAppIds
    } yield UpliftLogic.Data(allSummaries.toList, upliftableAppIds, invalidAppIds)
  }
}

object UpliftLogic {

  case class Data(
      sandboxApplicationSummaries: List[ApplicationSummary],
      upliftableApplicationIds: Set[ApplicationId],
      notUpliftableApplicationIds: Set[ApplicationId]
    ) {
    lazy val upliftableSummaries         = sandboxApplicationSummaries.filter(s => upliftableApplicationIds.contains(s.id))
    lazy val hasAppsThatCannotBeUplifted = notUpliftableApplicationIds.nonEmpty
  }

  def contextsOfTestSupportAndExampleApis(apis: Map[ApiContext, ApiData]): Set[ApiContext] = {
    filterApis(d => d.isTestSupport || d.categories.contains(ApiCategory.EXAMPLE))(apis)
      .keySet
  }

  def apiIdentifiersOfRetiredApis(apis: Map[ApiContext, ApiData]): Set[ApiIdentifier] = {
    (filterApis(_ => true, v => v.status == ApiStatus.RETIRED) _ andThen toApiIdentifiers)(apis)
  }

  def filterAppsHavingRealAndAvailableSubscriptions(
      sandboxApis: Map[ApiContext, ApiData],
      apisAvailableInProd: Set[ApiIdentifier],
      subscriptionsByApplication: Map[ApplicationId, Set[ApiIdentifier]]
    ): Map[ApplicationId, Set[ApiIdentifier]] = {

    val excludedContexts = contextsOfTestSupportAndExampleApis(sandboxApis)
    val retiredVersions  = apiIdentifiersOfRetiredApis(sandboxApis)

    subscriptionsByApplication.flatMap {
      case (id, subs) =>
        val realApis = subs.filterNot(id => excludedContexts.contains(id.context) || retiredVersions.contains(id))

        if (realApis.nonEmpty && realApis.subsetOf(apisAvailableInProd)) {
          Map(id -> realApis)
        } else {
          Map.empty[ApplicationId, Set[ApiIdentifier]]
        }
    }
  }

  private def filterApis(contextFilter: ApiData => Boolean)(in: Map[ApiContext, ApiData]): Map[ApiContext, ApiData] = {
    in.filter {
      case (c, d) => contextFilter(d)
    }
  }

  private def filterApis(contextFilter: ApiData => Boolean, versionFilter: ApiVersion => Boolean)(in: Map[ApiContext, ApiData]): Map[ApiContext, ApiData] = {
    def filterVersions(in: Map[ApiVersionNbr, ApiVersion]): Map[ApiVersionNbr, ApiVersion] = {
      in.filter {
        case (v, d) => versionFilter(d)
      }
    }

    val empty = Map.empty[ApiContext, ApiData]

    in.flatMap {
      case (c, d) =>
        val filteredVersions = filterVersions(d.versions)
        if (contextFilter(d) && filteredVersions.nonEmpty) {
          Map(c -> d.copy(versions = filteredVersions))
        } else {
          empty
        }
    }
  }

  private def toApiIdentifiers(in: Map[ApiContext, ApiData]): Set[ApiIdentifier] = {
    in.flatMap {
      case (c, d) => d.versions.keySet.map(v => ApiIdentifier(c, v))
    }.toSet
  }
}
