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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiVersion, _}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.common.domain.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ApplicationCommandConnector, DeskproConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldName

@Singleton
class SubscriptionsService @Inject() (
    deskproConnector: DeskproConnector,
    apmConnector: ApmConnector,
    applicationCommandConnector: ApplicationCommandConnector,
    val clock: Clock
  )(implicit ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  private def doRequest(
      requester: DeveloperSession,
      application: Application,
      apiName: String,
      apiVersion: ApiVersion
    )(
      f: (String, LaxEmailAddress, String, ApplicationId, String, ApiVersion) => DeskproTicket
    ) = {
    f(requester.displayedName, requester.email, application.name, application.id, apiName, apiVersion)
  }

  def requestApiSubscription(requester: DeveloperSession, application: Application, apiName: String, apiVersion: ApiVersion)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(Some(requester.developer.userId), doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiSubscribe))
  }

  def requestApiUnsubscribe(requester: DeveloperSession, application: Application, apiName: String, apiVersion: ApiVersion)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(Some(requester.developer.userId), doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiUnsubscribe))
  }

  type ApiMap[V]   = Map[ApiContext, Map[ApiVersion, V]]
  type FieldMap[V] = ApiMap[Map[FieldName, V]]

  def isSubscribedToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      app <- apmConnector.fetchApplicationById(applicationId)
      subs = app.map(_.subscriptions).getOrElse(Set.empty)
    } yield subs.contains(apiIdentifier)
  }

  def subscribeToApi(application: Application, apiIdentifier: ApiIdentifier, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): Result = {
    val cmd = ApplicationCommands.SubscribeToApi(Actors.AppCollaborator(requestingEmail), apiIdentifier, true, now)
    applicationCommandConnector.dispatch(application.id, cmd, Set.empty)
  }

  def unsubscribeFromApi(application: Application, apiIdentifier: ApiIdentifier, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): Result = {
    val cmd = ApplicationCommands.UnsubscribeFromApi(Actors.AppCollaborator(requestingEmail), apiIdentifier, now)
    applicationCommandConnector.dispatch(application.id, cmd, Set.empty)
  }
}
