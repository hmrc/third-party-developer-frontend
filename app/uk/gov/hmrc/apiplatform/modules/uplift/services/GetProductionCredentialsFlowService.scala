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

import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.SellResellOrDistribute
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger

import play.api.libs.json._

@Singleton
class GetProductionCredentialsFlowService @Inject() (
    val flowRepository: FlowRepository
  )(implicit val ec: ExecutionContext
  ) extends ApplicationLogger {

  def fetchFlow(developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] =
    flowRepository.fetchBySessionIdAndFlowType[GetProductionCredentialsFlow](developerSession.session.sessionId) flatMap {
      case Some(flow) => flow.pure[Future]
      case None       =>
        val newFlowObject = GetProductionCredentialsFlow.create(developerSession.session.sessionId)
        flowRepository.saveFlow[GetProductionCredentialsFlow](newFlowObject)
    }

  def storeSellResellOrDistribute(sellResellOrDistribute: SellResellOrDistribute, developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] = {

logger.warn("****** Fetching flow...")

    for {
      existingFlow <- fetchFlow(developerSession)
      _ = logger.warn(s"****** Saving flow...$existingFlow")

      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](existingFlow.copy(sellResellOrDistribute = Some(sellResellOrDistribute)))
      _ = logger.warn("****** Done saving flow...")
    } yield savedFlow


  }

  def findSellResellOrDistribute(developerSession: DeveloperSession): Future[Option[SellResellOrDistribute]] =
    fetchFlow(developerSession).map(_.sellResellOrDistribute)

  def storeApiSubscriptions(apiSubscriptions: ApiSubscriptions, developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] = {

logger.warn("****** Storing flow...")

    for {
      existingFlow <- fetchFlow(developerSession)

      newFlow = existingFlow.copy(apiSubscriptions = Some(apiSubscriptions))

      _ = logger.warn(s"****** Existing flow...$existingFlow")
      _ = logger.warn(s"****** New flow...$newFlow")
      _ = logger.warn(s"****** New flow...${Json.toJson(newFlow)}")

      savedFlow    <- flowRepository.saveFlow[GetProductionCredentialsFlow](newFlow)
      _ = logger.warn("****** Done saving flow...")
    } yield savedFlow
  }

  def resetFlow(developerSession: DeveloperSession): Future[GetProductionCredentialsFlow] =
    flowRepository.deleteBySessionIdAndFlowType(developerSession.session.sessionId, FlowType.GET_PRODUCTION_CREDENTIALS)
      .flatMap(_ => fetchFlow(developerSession))
}
