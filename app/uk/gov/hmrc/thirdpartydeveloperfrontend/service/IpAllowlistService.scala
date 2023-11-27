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

import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.CidrBlock
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApplicationCommandConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType.IP_ALLOW_LIST
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.IpAllowlistFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

@Singleton
class IpAllowlistService @Inject() (
    flowRepository: FlowRepository,
    connectorWrapper: ConnectorsWrapper,
    applicationCommandConnector: ApplicationCommandConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends CommandHandlerTypes[DispatchSuccessResult]
    with ClockNow {

  private def fetchIpAllowListFlow(sessionId: String, app: Option[Application], createIfNotFound: Boolean = true): Future[IpAllowlistFlow] = {
    flowRepository.fetchBySessionIdAndFlowType[IpAllowlistFlow](sessionId) map { maybeFlow =>
      (maybeFlow, app, createIfNotFound) match {
        case (Some(flow: IpAllowlistFlow), _, _)  => flow
        case (None, Some(app: Application), true) => IpAllowlistFlow(sessionId, app.ipAllowlist.allowlist)
        case _                                    => throw new IllegalStateException(s"No IP allowlist flow exists for session ID $sessionId")
      }
    }

  }

  def getIpAllowlistFlow(app: Application, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      flow      <- fetchIpAllowListFlow(sessionId, Some(app))
      savedFlow <- flowRepository.saveFlow(flow)
    } yield savedFlow
  }

  def discardIpAllowlistFlow(sessionId: String): Future[Boolean] = {
    flowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
  }

  def addCidrBlock(cidrBlock: String, app: Application, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      flow                        <- fetchIpAllowListFlow(sessionId, Some(app))
      updatedFlow: IpAllowlistFlow = flow.copy(allowlist = flow.allowlist + cidrBlock)
      savedFlow                   <- flowRepository.saveFlow(updatedFlow)
    } yield savedFlow
  }

  def removeCidrBlock(cidrBlock: String, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      flow      <- fetchIpAllowListFlow(sessionId, None, createIfNotFound = false)
      savedFlow <- flowRepository.saveFlow(flow.copy(allowlist = flow.allowlist - cidrBlock))
    } yield savedFlow
  }

  def activateIpAllowlist(app: Application, sessionId: String, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    for {
      flow     <- fetchIpAllowListFlow(sessionId, None, createIfNotFound = false)
      _         = if (flow.allowlist.isEmpty) throw new ForbiddenException(s"IP allowlist for session ID $sessionId cannot be activated because it is empty")
      command   = ApplicationCommands.ChangeIpAllowlist(
                    Actors.AppCollaborator(requestingEmail),
                    now(),
                    app.ipAllowlist.required,
                    app.ipAllowlist.allowlist.map(CidrBlock(_)).toList,
                    flow.allowlist.map(CidrBlock(_)).toList
                  )
      response <- applicationCommandConnector.dispatch(app.id, command, Set.empty).map(_ => ApplicationUpdateSuccessful)
      _        <- flowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
    } yield response
  }

  def deactivateIpAllowlist(app: Application, sessionId: String, requestingEmail: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    if (app.ipAllowlist.required) {
      Future.failed(new ForbiddenException(s"IP allowlist for session ID $sessionId cannot be deactivated because it is required"))
    } else {
      val command = ApplicationCommands.ChangeIpAllowlist(
        Actors.AppCollaborator(requestingEmail),
        now(),
        app.ipAllowlist.required,
        app.ipAllowlist.allowlist.map(CidrBlock(_)).toList,
        List.empty
      )

      for {
        response <- applicationCommandConnector.dispatch(app.id, command, Set.empty).map(_ => ApplicationUpdateSuccessful)
        _        <- flowRepository.deleteBySessionIdAndFlowType(sessionId, IP_ALLOW_LIST)
      } yield response
    }
  }
}
