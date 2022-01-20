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

package repositories

import akka.stream.Materializer
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import domain.models.flows.{Flow, FlowType}
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import repositories.IndexHelper.createAscendingIndex
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FlowRepository @Inject()(mongo: ReactiveMongoComponent, appConfig: ApplicationConfig)(implicit val mat: Materializer, val ec: ExecutionContext)
  extends ReactiveRepository[Flow, BSONObjectID]("flows", mongo.mongoConnector.db,
    ReactiveMongoFormatters.formatFlow, ReactiveMongoFormats.objectIdFormats) {

  private lazy val lastUpdatedIndexName = "last_updated_ttl_idx"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"

  override def indexes = Seq(
    createAscendingIndex(
      Some("session_flow_type_idx"),
      isUnique = true,
      isBackground = true,
      List("sessionId", "flowType"): _*
    ),
    Index(
      key = Seq("lastUpdated" -> IndexType.Ascending),
      name = Some(lastUpdatedIndexName),
      background = true,
      options = BSONDocument(OptExpireAfterSeconds -> BSONLong(appConfig.sessionTimeoutInSeconds))
    )
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    dropTTLIndexIfChanged
    ensureLocalIndexes()
  }

  private def dropTTLIndexIfChanged(implicit ec: ExecutionContext) = {
    val indexes = collection.indexesManager.list()

    def matchIndexName(index: Index) = {
      index.eventualName == lastUpdatedIndexName
    }

    def compareTTLValueWithConfig(index: Index) = {
      index.options.getAs[BSONLong](OptExpireAfterSeconds).fold(false)(_.as[Long] != appConfig.sessionTimeoutInSeconds)
    }

    def checkIfTTLChanged(index: Index): Boolean = {
      matchIndexName(index) && compareTTLValueWithConfig(index)
    }

    indexes.map(_.exists(checkIfTTLChanged))
      .map(hasTTLIndexChanged => if (hasTTLIndexChanged) {
        logger.info(s"Dropping time to live index for entries in ${collection.name}")
        Future.sequence(Seq(collection.indexesManager.drop(lastUpdatedIndexName).map(_ > 0),
          ensureLocalIndexes))
      })
  }

  private def ensureLocalIndexes()(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(indexes.map(index => {
      logger.info(s"ensuring index ${index.eventualName}")
      collection.indexesManager.ensure(index)
    }))
  }

  def saveFlow[A <: Flow](flow: A)(implicit format: OFormat[A]): Future[A] = {
   for {
     something <- findAndUpdate(Json.obj("sessionId" -> flow.sessionId, "flowType" -> flow.flowType),
       Json.toJson(flow.asInstanceOf[Flow]).as[JsObject], upsert = true, fetchNewObject = true)
     _ <- updateLastUpdated(flow.sessionId)
   } yield something.result[A].head
  }

  def deleteBySessionIdAndFlowType(sessionId: String, flowType: FlowType): Future[Boolean] = {
    remove("sessionId" -> sessionId, "flowType" -> flowType).map(_.ok)
  }

  def fetchBySessionIdAndFlowType[A <: Flow](sessionId: String, flowType: FlowType)(implicit formatter: OFormat[A]): Future[Option[A]] = {
    collection
      .find(Json.obj("sessionId" -> sessionId, "flowType" -> flowType), Option.empty[A])
      .cursor[A](ReadPreference.primaryPreferred)
      .collect(maxDocs = -1, FailOnError[List[A]]())
      .map(_.headOption)
  }

  def updateLastUpdated(sessionId: String): Future[Unit] = {
    val updateStatement: JsObject = Json.obj("$currentDate" -> Json.obj("lastUpdated" -> true))
    collection.update(false).one(Json.obj("sessionId" -> sessionId), updateStatement, upsert = false, multi = true).map(_ => ())
  }
}
