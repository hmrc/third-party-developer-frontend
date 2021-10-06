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

package modules.uplift.services

import domain.models.developers.DeveloperSession
import scala.concurrent.Future
import domain.models.applications.ApplicationId
import modules.uplift.domain.models.ApiSubscriptions
import connectors.ApmConnector
import javax.inject.{Inject, Singleton}
import helpers.EitherTHelper
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext
import domain.models.apidefinitions.APISubscriptionStatus
import modules.uplift.domain.models._
import modules.uplift.domain.services._
import domain.models.apidefinitions.ApiContext
import domain.models.applications.UpliftData

@Singleton
class UpliftJourneyService @Inject()(
    flowService: GetProductionCredentialsFlowService,
    apmConnector: ApmConnector
)( implicit val ec: ExecutionContext ) extends EitherTHelper[String] {
  import cats.instances.future.catsStdInstancesForFuture

  def confirmAndUplift(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[String, ApplicationId]] =
    (
      for {
        flow                    <- liftF(flowService.fetchFlow(developerSession))
        responsibleIndividual   <- fromOption(flow.responsibleIndividual, "No responsible individual set")
        sellResellOrDistribute  <- fromOption(flow.sellResellOrDistribute, "No sell or resell or distribute set")
        subscriptionFlow        <- fromOption(flow.apiSubscriptions, "No subscriptions set")

        apiIdsToSubscribeTo     <- liftF(apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map(_.filter(subscriptionFlow.isSelected)))
        _                       <- cond(apiIdsToSubscribeTo.nonEmpty, (), "No apis found to subscribe to")
        upliftData               = UpliftData(responsibleIndividual, sellResellOrDistribute, apiIdsToSubscribeTo)
        upliftedAppId           <- liftF(apmConnector.upliftApplication(sandboxAppId, upliftData))
      } yield upliftedAppId
    )
    .value

  def changeApiSubscriptions(sandboxAppId: ApplicationId, developerSession: DeveloperSession, subscriptions: List[APISubscriptionStatus])(implicit hc: HeaderCarrier): Future[List[APISubscriptionStatus]] = 
    (
      for {
        flow                          <- flowService.fetchFlow(developerSession)
        subscriptionFlow               = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
        upliftableApiIds              <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        subscriptionsWithFlowAdjusted  = subscriptions.filter(SubscriptionsFilter(upliftableApiIds, subscriptionFlow))
      } yield subscriptionsWithFlowAdjusted
    )

  def responsibleIndividual(developerSession: DeveloperSession): Future[Option[ResponsibleIndividual]] = 
    flowService.fetchFlow(developerSession)
    .map(_.responsibleIndividual)

  def sellResellOrDistribute(developerSession: DeveloperSession): Future[Option[SellResellOrDistribute]] = 
    flowService.fetchFlow(developerSession)
    .map(_.sellResellOrDistribute)

  def storeDefaultSubscriptionsInFlow(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[ApiSubscriptions] = 
    for {
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      apiSubscriptions         = ApiSubscriptions(upliftableSubscriptions.map(id => (id, true)).toMap)
      _                       <- flowService.storeApiSubscriptions(apiSubscriptions, developerSession)
    } yield apiSubscriptions

  def apiSubscriptionData(sandboxAppId: ApplicationId, developerSession: DeveloperSession, subscriptions: List[APISubscriptionStatus])(implicit hc: HeaderCarrier): Future[(Set[String], Boolean)] = {
    def getApiNameForContext(apiContext: ApiContext) =
      subscriptions
      .find(_.context == apiContext )
      .map(_.name)

    for {
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      flow             <- flowService.fetchFlow(developerSession)
      subscriptionFlow  = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
    } yield {
      val data: Set[String] = (
        for {
          subscription <- upliftableApiIds.filter(subscriptionFlow.isSelected)
          name         <- getApiNameForContext(subscription.context)
        }
        yield {
          s"$name - ${subscription.version.value}"
        }
      )

      (data, upliftableApiIds.size > 1)
    }
  }
}
