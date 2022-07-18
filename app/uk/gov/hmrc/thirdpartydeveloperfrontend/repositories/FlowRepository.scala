/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.repositories

import akka.stream.Materializer
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Updates}
import play.api.libs.json._
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, Flow, FlowType, IpAllowlistFlow, NewApplicationEmailPreferencesFlowV2}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.MongoFormatters.formatFlow

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowRepository @Inject() (mongo: MongoComponent, appConfig: ApplicationConfig)(implicit val mat: Materializer, val ec: ExecutionContext)
    extends PlayMongoRepository[Flow](
      collectionName = "flows",
      mongoComponent = mongo,
      domainFormat = formatFlow,
      indexes = Seq(
        IndexModel(
          ascending("sessionId", "flowType"),
          IndexOptions().name("session_flow_type_idx")
            .unique(true)
            .background(true)
        ),
        IndexModel(
          ascending("lastUpdated"),
          IndexOptions().name("last_updated_ttl_idx")
            .background(true)
            .expireAfter(appConfig.sessionTimeoutInSeconds, TimeUnit.SECONDS)
        )
      ),
      extraCodecs = Codecs.playFormatCodecsBuilder(formatFlow)
        .forType[IpAllowlistFlow]
        .forType[EmailPreferencesFlowV2]
        .forType[NewApplicationEmailPreferencesFlowV2]
        .forType[GetProductionCredentialsFlow]
        .build
    ) {

  def saveFlow[A <: Flow](flow: A)(implicit format: OFormat[A]): Future[A] = {
    val findAndReplaceFlow = collection
      .findOneAndReplace(
      filter = and(equal("sessionId", flow.sessionId), equal("flowType", Codecs.toBson(flow.flowType))),
      replacement = flow
    ).toFuture()
      .map(_ => flow)

    for {
      updatedFlow <- findAndReplaceFlow
      _ <- updateLastUpdated(flow.sessionId)
    } yield updatedFlow
  }

  def deleteBySessionIdAndFlowType(sessionId: String, flowType: FlowType): Future[Boolean] = {
    collection.deleteOne(and(equal("sessionId", sessionId), equal("flowType", Codecs.toBson(flowType))))
      .toFuture()
      .map(_.wasAcknowledged())
  }

  def fetchBySessionIdAndFlowType[A <: Flow](sessionId: String, flowType: FlowType)(implicit formatter: OFormat[A]): Future[Option[Flow]] = {
    collection.find(and(equal("sessionId", sessionId), equal("flowType", Codecs.toBson(flowType))))
      .headOption()
  }

  def updateLastUpdated(sessionId: String): Future[Unit] = {
    collection.findOneAndUpdate(
      filter = equal("sessionId", sessionId),
      update = Updates.currentDate("lastUpdated"),
      options = FindOneAndUpdateOptions().upsert(false)
    ).toFuture()
      .map(_ => ())
  }
}