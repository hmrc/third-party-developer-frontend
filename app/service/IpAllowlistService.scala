/*
 * Copyright 2020 HM Revenue & Customs
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

import domain.ApplicationUpdateSuccessful
import domain.models.applications.Application
import domain.models.flows.IpAllowlistFlow
import javax.inject.{Inject, Singleton}
import repositories.FlowRepository
import repositories.ReactiveMongoFormatters.formatIpAllowlistFlow
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IpAllowlistService @Inject()(flowRepository: FlowRepository, connectorWrapper: ConnectorsWrapper)(implicit val ec: ExecutionContext) {

  def getIpAllowlistFlow(app: Application, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      optionalFlow <- flowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)
      flow = optionalFlow.getOrElse(IpAllowlistFlow(sessionId, app.ipWhitelist))
      savedFlow <- flowRepository.saveFlow(flow)
    } yield savedFlow
  }

  def discardIpAllowlistFlow(sessionId: String): Future[Boolean] = {
    flowRepository.deleteBySessionId(sessionId)
  }

  def addCidrBlock(cidrBlock: String, app: Application, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      optionalFlow <- flowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)
      updatedFlow = optionalFlow.fold(IpAllowlistFlow(sessionId, app.ipWhitelist + cidrBlock))(flow => flow.copy(allowlist = flow.allowlist + cidrBlock))
      savedFlow <- flowRepository.saveFlow(updatedFlow)
    } yield savedFlow
  }

  def removeCidrBlock(cidrBlock: String, sessionId: String): Future[IpAllowlistFlow] = {
    for {
      optionalFlow <- flowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)
      flow = optionalFlow.getOrElse(throw new IllegalStateException("Can't remove CIDR block"))
      savedFlow <- flowRepository.saveFlow(flow.copy(allowlist = flow.allowlist - cidrBlock))
    } yield savedFlow
  }

  def activateIpAllowlist(app: Application, sessionId: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    for {
      optionalFlow <- flowRepository.fetchBySessionId[IpAllowlistFlow](sessionId)
      flow = optionalFlow.getOrElse(throw new IllegalStateException("Can't activate IP allowlist"))
      result <- connectorWrapper.forEnvironment(app.deployedTo).thirdPartyApplicationConnector.updateIpAllowlist(app.id, flow.allowlist)
      _ <- flowRepository.deleteBySessionId(sessionId)
    } yield result
  }
}
