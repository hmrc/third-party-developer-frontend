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

import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

@Singleton
class GetProductionCredentialsFlowService @Inject() (
    val flowRepository: FlowRepository
  )(implicit val ec: ExecutionContext
  ) {

  def fetchFlow(session: UserSession): Future[GetProductionCredentialsFlow] =
    flowRepository.fetchBySessionIdAndFlowType[GetProductionCredentialsFlow](session.sessionId) flatMap {
      case Some(flow) => flow.pure[Future]
      case None       =>
        val newFlowObject = GetProductionCredentialsFlow.create(session.sessionId)
        flowRepository.saveFlow[GetProductionCredentialsFlow](newFlowObject)
    }

  def storeSellResellOrDistribute(sellResellOrDistribute: SellResellOrDistribute, session: UserSession): Future[GetProductionCredentialsFlow] = {
    for {
      existingFlow <- fetchFlow(session)
      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(sellResellOrDistribute = Some(sellResellOrDistribute)))
    } yield savedFlow
  }

  def findSellResellOrDistribute(session: UserSession): Future[Option[SellResellOrDistribute]] =
    fetchFlow(session).map(_.sellResellOrDistribute)

  def storeApiSubscriptions(apiSubscriptions: ApiSubscriptions, session: UserSession): Future[GetProductionCredentialsFlow] = {
    for {
      existingFlow <- fetchFlow(session)
      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(apiSubscriptions = Some(apiSubscriptions)))
    } yield savedFlow
  }

  def resetFlow(session: UserSession): Future[GetProductionCredentialsFlow] =
    flowRepository.deleteBySessionIdAndFlowType(session.sessionId, FlowType.GET_PRODUCTION_CREDENTIALS)
      .flatMap(_ => fetchFlow(session))
}
