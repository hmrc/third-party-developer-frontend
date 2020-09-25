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

package repositories

import akka.stream.Materializer
import domain.models.flows.Flow
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.json.{JsObject, Json, OWrites, Reads}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{FindAndModifyCommand, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import repositories.ReactiveMongoFormatters.dateFormat
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{ClassTag, classTag}

@Singleton
class FlowRepository @Inject()(mongo: ReactiveMongoComponent)(implicit val mat: Materializer, val ec: ExecutionContext)
  extends ReactiveRepository[Flow, BSONObjectID]("flows", mongo.mongoConnector.db,
    ReactiveMongoFormatters.formatFlow, ReactiveMongoFormats.objectIdFormats) {

  val documentTtlInSeconds = 900

  override def indexes = Seq(
    Index(
      key = Seq("lastUpdated" -> IndexType.Ascending),
      name = Some("last_updated_ttl_idx"),
      background = true,
      options = BSONDocument("expireAfterSeconds" -> BSONLong(documentTtlInSeconds))
    )
  )

  def save[A <: Flow](flow: A)(implicit ct: ClassTag[A], reads: Reads[A], writes: OWrites[A]): Future[A] = {
    val updateStatement: JsObject = Json.toJson(flow).as[JsObject] ++ Json.obj("lastUpdated" -> DateTime.now(UTC))
    val updateStatementAlt: JsObject = Json.toJson(flow).as[JsObject] ++ Json.obj("$currentDate" -> Json.obj("lastUpdated" -> true))
//    for {
//      something <- findAndUpdate(Json.obj("sessionId" -> flow.sessionId, "flowType" -> classTag[A].runtimeClass.getSimpleName), updateStatement, upsert = true, fetchNewObject = true)
//      _ <- updateLastUpdated(flow.sessionId)
//    } something.result[A].head

    findAndUpdate(Json.obj("sessionId" -> flow.sessionId, "flowType" -> classTag[A].runtimeClass.getSimpleName),
      updateStatement, upsert = true, fetchNewObject = true)
      .map(_.result[A].head)
  }

  def deleteBySessionId[A <: Flow](sessionId: String)(implicit ct: ClassTag[A], reads: Reads[A], writes: OWrites[A]): Future[Boolean] = {
    remove("sessionId" -> sessionId, "flowType" -> classTag[A].runtimeClass.getSimpleName).map(_.ok)
  }

  def fetchBySessionId[A <: Flow](sessionId: String)(implicit ct: ClassTag[A], reads: Reads[A], writes: OWrites[A]): Future[Option[A]] = {
    collection
      .find(Json.obj("sessionId" -> sessionId, "flowType" -> classTag[A].runtimeClass.getSimpleName), Option.empty[A])
      .cursor[A](ReadPreference.primaryPreferred)
      .collect(maxDocs = -1, FailOnError[List[A]]())
      .map(_.headOption)
  }

  def updateLastUpdated(sessionId: String): Future[Unit] = {
    val updateStatement: JsObject = Json.obj("$currentDate" -> Json.obj("lastUpdated" -> true))
    findAndUpdate(Json.obj("sessionId" -> sessionId), updateStatement, fetchNewObject = false).map(_ => ())
  }
}
