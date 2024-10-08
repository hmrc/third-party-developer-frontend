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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import java.net.URL
import java.{util => ju}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NonFatal

import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualVerificationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._

@Singleton
class DeskproConnector @Inject() (http: HttpClientV2, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger {

  lazy val serviceBaseUrl: String = config.deskproUrl
  val api                         = API("deskpro")

  def unknownUserId: UserId = UserId(ju.UUID.fromString("00000000-0000-0000-0000-000000000000"))

  def createTicket(userId: Option[UserId], deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    createTicket(userId.getOrElse(unknownUserId), deskproTicket)
  }

  private def createTicket(userId: UserId, deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    createTicket(userId.toString(), "userId", deskproTicket)
  }

  def createTicket(id: ResponsibleIndividualVerificationId, deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    createTicket(id.value, "ResponsibleIndividualVerification", deskproTicket)
  }

  private def createTicket(id: String, idType: String, deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    http.post(requestUrl("/deskpro/ticket"))
      .withBody(Json.toJson(deskproTicket))
      .execute[ErrorOrUnit]
      .map(throwOr(TicketCreated))
      .andThen {
        case Success(_) => logger.info(s"Deskpro ticket '${deskproTicket.subject}' created successfully")
      }
      .recover {
        case NonFatal(e) =>
          logger.error(s"Deskpro ticket creation failed for $idType: $id", e)
          throw new DeskproTicketCreationFailed(e.getMessage)
      }
  }

  def createFeedback(feedback: Feedback)(implicit hc: HeaderCarrier): Future[TicketId] = metrics.record(api) {
    http.post(requestUrl("/deskpro/feedback"))
      .withBody(Json.toJson(feedback))
      .execute[Option[TicketId]]
      .map(_.fold(throw UpstreamErrorResponse("Create Feedback failed", 404, 500))(x => x))
  }

  override def toString = "DeskproConnector()"

  private def requestUrl[B, A](path: String): URL = {
    val concat = s"${serviceBaseUrl}${path}"
    url"$concat"
  }
}
