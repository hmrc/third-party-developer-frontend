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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, SellResellOrDistribute}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.{ClockNow, EitherTHelper}
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.ApiSubscriptions
import uk.gov.hmrc.apiplatform.modules.uplift.domain.services._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ApplicationCommandConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

@Singleton
class UpliftJourneyService @Inject() (
    flowService: GetProductionCredentialsFlowService,
    apmConnector: ApmConnector,
    thirdPartyApplicationSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector,
    appCmdConnector: ApplicationCommandConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow with EitherTHelper[String] {
  import cats.instances.future.catsStdInstancesForFuture

  def confirmAndUplift(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[String, ApplicationId]] =
    confirmAndUpliftV2(sandboxAppId, developerSession)

  def confirmAndUpliftV1(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[String, ApplicationId]] =
    (
      for {
        flow             <- liftF(flowService.fetchFlow(developerSession))
        subscriptionFlow <- fromOption(flow.apiSubscriptions, "No subscriptions set")

        apiIdsToSubscribeTo <- liftF(apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map(_.filter(subscriptionFlow.isSelected)))
        _                   <- cond(apiIdsToSubscribeTo.nonEmpty, (), "No apis found to subscribe to")
        upliftedAppId       <- liftF(apmConnector.upliftApplicationV1(sandboxAppId, apiIdsToSubscribeTo))
      } yield upliftedAppId
    )
      .value

  def confirmAndUpliftV2(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[String, ApplicationId]] =
    (
      for {
        flow                   <- liftF(flowService.fetchFlow(developerSession))
        sellResellOrDistribute <- fromOption(flow.sellResellOrDistribute, "No sell or resell or distribute set")
        subscriptionFlow       <- fromOption(flow.apiSubscriptions, "No subscriptions set")

        apiIdsToSubscribeTo <- liftF(apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map(_.filter(subscriptionFlow.isSelected)))
        _                   <- cond(apiIdsToSubscribeTo.nonEmpty, (), "No apis found to subscribe to")
        upliftData           = UpliftData(sellResellOrDistribute, apiIdsToSubscribeTo, developerSession.developer.email)
        upliftedAppId       <- liftF(apmConnector.upliftApplicationV2(sandboxAppId, upliftData))
      } yield upliftedAppId
    )
      .value

  def changeApiSubscriptions(
      sandboxAppId: ApplicationId,
      developerSession: DeveloperSession,
      subscriptions: List[APISubscriptionStatus]
    )(implicit hc: HeaderCarrier
    ): Future[List[APISubscriptionStatus]] =
    (
      for {
        flow                         <- flowService.fetchFlow(developerSession)
        subscriptionFlow              = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
        upliftableApiIds             <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        subscriptionsWithFlowAdjusted = subscriptions.filter(SubscriptionsFilter(upliftableApiIds, subscriptionFlow))
      } yield subscriptionsWithFlowAdjusted
    )

  def storeDefaultSubscriptionsInFlow(sandboxAppId: ApplicationId, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[ApiSubscriptions] =
    for {
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      apiSubscriptions         = ApiSubscriptions(upliftableSubscriptions.map(id => (id, true)).toMap)
      _                       <- flowService.storeApiSubscriptions(apiSubscriptions, developerSession)
    } yield apiSubscriptions

  def apiSubscriptionData(
      sandboxAppId: ApplicationId,
      developerSession: DeveloperSession,
      subscriptions: List[APISubscriptionStatus]
    )(implicit hc: HeaderCarrier
    ): Future[(Set[String], Boolean)] = {
    def getApiNameForContext(apiContext: ApiContext) =
      subscriptions
        .find(_.context == apiContext)
        .map(_.name)

    for {
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      flow             <- flowService.fetchFlow(developerSession)
      subscriptionFlow  = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
    } yield {
      val data: Set[String] =
        (
          for {
            subscription <- upliftableApiIds.filter(subscriptionFlow.isSelected)
            name         <- getApiNameForContext(subscription.context)
          } yield {
            s"$name - ${subscription.versionNbr}"
          }
        )

      (data, upliftableApiIds.size > 1)
    }
  }

  def createNewSubmission(appId: ApplicationId, application: Application, developerSession: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[String, Submission]] = {
    (
      for {
        flow                   <- liftF(flowService.fetchFlow(developerSession))
        sellResellOrDistribute <- fromOption(flow.sellResellOrDistribute, "No sell or resell or distribute set")

        // Need to update the application with possible new value of sellResellOrDistribute,
        // but don't change app apart from that.
        _ <- liftF(updateSellResellOrDistributeIfNeeded(application, sellResellOrDistribute, developerSession))

        submission <- fromOptionF(thirdPartyApplicationSubmissionsConnector.createSubmission(appId, developerSession.email), "No submission returned")
      } yield submission
    )
      .value
  }

  private def updateSellResellOrDistributeIfNeeded(
      application: Application,
      sellResellOrDistribute: SellResellOrDistribute,
      developerSession: DeveloperSession
    )(implicit hc: HeaderCarrier
    ) = {
    def existingSellResellOrDistribute = application.access match {
      case Access.Standard(_, _, _, _, sellResellOrDistribute, _) => sellResellOrDistribute
      case _                                                      => None
    }

    // Only save if the value is different
    if (Some(sellResellOrDistribute) != existingSellResellOrDistribute) {
      val cmd = ApplicationCommands.ChangeApplicationSellResellOrDistribute(
        Actors.AppCollaborator(developerSession.email),
        instant(),
        sellResellOrDistribute
      )
      appCmdConnector.dispatch(application.id, cmd, Set.empty).map(_ => ApplicationUpdateSuccessful)
    } else {
      Future.successful(ApplicationUpdateSuccessful)
    }
  }
}
