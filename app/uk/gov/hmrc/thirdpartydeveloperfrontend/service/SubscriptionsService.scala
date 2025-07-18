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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaborators}
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models.FieldName
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnectorApplicationModule, ApmConnectorCommandModule, DeskproConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}

@Singleton
class SubscriptionsService @Inject() (
    deskproConnector: DeskproConnector,
    apmApplicationModule: ApmConnectorApplicationModule,
    apmCmdModule: ApmConnectorCommandModule,
    val clock: Clock
  )(implicit ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  private def doRequest(
      requester: UserSession,
      application: ApplicationWithCollaborators,
      apiName: String,
      apiVersion: ApiVersionNbr
    )(
      f: (String, LaxEmailAddress, ApplicationName, ApplicationId, String, ApiVersionNbr) => DeskproTicket
    ) = {
    f(requester.developer.displayedName, requester.developer.email, application.name, application.id, apiName, apiVersion)
  }

  def requestApiSubscription(
      requester: UserSession,
      application: ApplicationWithCollaborators,
      apiName: String,
      apiVersion: ApiVersionNbr
    )(implicit hc: HeaderCarrier
    ): Future[TicketResult] = {
    deskproConnector.createTicket(Some(requester.developer.userId), doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiSubscribe))
  }

  def requestApiUnsubscribe(requester: UserSession, application: ApplicationWithCollaborators, apiName: String, apiVersion: ApiVersionNbr)(implicit hc: HeaderCarrier)
      : Future[TicketResult] = {
    deskproConnector.createTicket(Some(requester.developer.userId), doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiUnsubscribe))
  }

  type ApiMap[V]   = Map[ApiContext, Map[ApiVersionNbr, V]]
  type FieldMap[V] = ApiMap[Map[FieldName, V]]

  def isSubscribedToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      app <- apmApplicationModule.fetchApplicationById(applicationId)
      subs = app.map(_.subscriptions).getOrElse(Set.empty)
    } yield subs.contains(apiIdentifier)
  }

  def subscribeToApi(application: ApplicationWithCollaborators, apiIdentifier: ApiIdentifier, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): AppCmdResult = {
    val cmd = ApplicationCommands.SubscribeToApi(Actors.AppCollaborator(requestingEmail), apiIdentifier, instant())
    apmCmdModule.dispatch(application.id, cmd, Set.empty)
  }

  def unsubscribeFromApi(application: ApplicationWithCollaborators, apiIdentifier: ApiIdentifier, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): AppCmdResult = {
    val cmd = ApplicationCommands.UnsubscribeFromApi(Actors.AppCollaborator(requestingEmail), apiIdentifier, instant())
    apmCmdModule.dispatch(application.id, cmd, Set.empty)
  }
}
