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

package connectors

import config.ApplicationConfig
import domain._
import domain.models.connectors._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.HttpReads.Implicits._

@Singleton
class DeskproConnector @Inject()(http: HttpClient, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext) 
extends CommonResponseHandlers {

  lazy val serviceBaseUrl: String = config.deskproUrl
  val api = API("deskpro")

  def createTicket(deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {

    http.POST[DeskproTicket,ErrorOrUnit](requestUrl("/deskpro/ticket"), deskproTicket)
    .map(throwOr(TicketCreated))
    .recover {
      case NonFatal(e) =>
        Logger.error(s"Deskpro ticket creation failed for: $deskproTicket", e)
        throw new DeskproTicketCreationFailed(e.getMessage)
    }
  }

  def createFeedback(feedback: Feedback)(implicit hc: HeaderCarrier): Future[TicketId] = metrics.record(api) {
    http.POST[Feedback, Option[TicketId]](requestUrl("/deskpro/feedback"), feedback)
    .map(_.fold(throw UpstreamErrorResponse("Create Feedback failed", 404, 500))(x => x))
  }

  override def toString = "DeskproConnector()"

  private def requestUrl[B, A](uri: String): String = s"$serviceBaseUrl$uri"
}
