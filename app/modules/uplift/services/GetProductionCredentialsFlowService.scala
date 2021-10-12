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
import javax.inject.{Inject, Singleton}
import repositories.FlowRepository

import scala.concurrent.{ExecutionContext, Future}
import modules.uplift.domain.models._
import cats.implicits._
import domain.models.flows.FlowType


@Singleton
class GetProductionCredentialsFlowService @Inject()(
  val flowRepository: FlowRepository
)(implicit val ec: ExecutionContext) {

  def fetchFlow(developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] =
    flowRepository.fetchBySessionIdAndFlowType[GetProductionCredentialsFlow](developerSession.session.sessionId, FlowType.GET_PRODUCTION_CREDENTIALS) flatMap {
      case Some(flow) => flow.pure[Future]
      case None       => val newFlowObject = GetProductionCredentialsFlow.create(developerSession.session.sessionId)
                         flowRepository.saveFlow[GetProductionCredentialsFlow](newFlowObject)
    }

  def storeResponsibleIndividual(newResponsibleIndividual: ResponsibleIndividual, developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] = {
    for {
      existingFlow <- fetchFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(responsibleIndividual = Some(newResponsibleIndividual)))
    } yield savedFlow
  }

  def storeSellResellOrDistribute(sellResellOrDistribute: SellResellOrDistribute, developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] = {
    for {
      existingFlow <- fetchFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(sellResellOrDistribute = Some(sellResellOrDistribute)))
    } yield savedFlow
  }

  def storeApiSubscriptions(apiSubscriptions: ApiSubscriptions, developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] = {
    for {
      existingFlow <- fetchFlow(developerSession)
      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(apiSubscriptions = Some(apiSubscriptions)))
    } yield savedFlow
  }
}
