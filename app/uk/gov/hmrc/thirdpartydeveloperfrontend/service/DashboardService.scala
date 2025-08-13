/*
 * Copyright 2025 HM Revenue & Customs
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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary

@Singleton
class DashboardService @Inject() (
    connectorWrapper: ConnectorsWrapper,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow {

  def fetchApplicationList(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationSummary]] = {

    def createDashboardAppList(prodAppList: Seq[ApplicationWithSubscriptions], sandboxAppList: Seq[ApplicationWithSubscriptions]): Seq[ApplicationSummary] = {
      val combinedApps = prodAppList ++ sandboxAppList;
      // Filter out any apps that don't have a state of production, sort by created date (descending) and take the first 5
      combinedApps.filter(app => app.details.isInProduction).map(app => ApplicationSummary.from(app, userId)).sorted.take(5)
    }

    for {
      productionApps <- connectorWrapper.forEnvironment(Environment.PRODUCTION).thirdPartyApplicationConnector.fetchByTeamMember(userId)
      sandboxApps    <- connectorWrapper.forEnvironment(Environment.SANDBOX).thirdPartyApplicationConnector.fetchByTeamMember(userId)
      combinedAppList = createDashboardAppList(productionApps, sandboxApps)
    } yield combinedAppList
  }

}
