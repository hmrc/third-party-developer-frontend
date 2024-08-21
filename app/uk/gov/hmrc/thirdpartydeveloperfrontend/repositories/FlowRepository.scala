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

package uk.gov.hmrc.thirdpartydeveloperfrontend.repositories

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions, Updates}

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows._
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.MongoFormatters.formatFlow

@Singleton
class FlowRepository @Inject() (mongo: MongoComponent, appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
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

  def saveFlow[A <: Flow](flow: A): Future[A] = {
    val query = and(equal("sessionId", flow.sessionId.toString), equal("flowType", Codecs.toBson(flow.flowType)))

    collection.find(query).headOption() flatMap {
      case Some(_: Flow) =>
        for {
          updatedFlow <- collection.replaceOne(
                           filter = query,
                           replacement = flow
                         ).toFuture().map(_ => flow)

          _ <- updateLastUpdated(flow.sessionId)
        } yield updatedFlow

      case None =>
        for {
          newFlow <- collection.insertOne(flow).toFuture().map(_ => flow)
          _       <- updateLastUpdated(flow.sessionId)
        } yield newFlow
    }
  }

  def deleteBySessionIdAndFlowType(sessionId: SessionId, flowType: FlowType): Future[Boolean] = {
    collection.deleteOne(and(equal("sessionId", sessionId.toString), equal("flowType", Codecs.toBson(flowType))))
      .toFuture()
      .map(_.wasAcknowledged())
  }

  def fetchBySessionIdAndFlowType[A <: Flow](sessionId: A#Type)(implicit tt: TypeTag[A], ct: ClassTag[A]): Future[Option[A]] = {
    val flowType = FlowType.from[A]
    collection.find[A](and(equal("sessionId", sessionId.toString), equal("flowType", Codecs.toBson(flowType)))).headOption()
  }

  def updateLastUpdated(sessionId: SessionId): Future[Unit] = {
    collection.updateMany(
      filter = equal("sessionId", sessionId.toString),
      update = Updates.currentDate("lastUpdated"),
      options = new UpdateOptions().upsert(false)
    ).toFuture()
      .map(_ => ())
  }
}
