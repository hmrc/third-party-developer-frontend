/*
 * Copyright 2019 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeskproConnector @Inject()(http: HttpClient, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext) {

  lazy val serviceBaseUrl: String = config.deskproUrl
  val api = API("deskpro")

  def createTicket(deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    http.POST(requestUrl("/deskpro/ticket"), deskproTicket) map (_ => TicketCreated) recover {
      case e: Exception =>
        Logger.error(s"Deskpro ticket creation failed for: $deskproTicket", e)
        throw new DeskproTicketCreationFailed(e.getMessage)
    }
  }

  def createFeedback(feedback: Feedback)(implicit hc: HeaderCarrier): Future[TicketId] = metrics.record(api) {
    http.POST[Feedback, TicketId](requestUrl("/deskpro/feedback"), feedback) recover {
      case nf: NotFoundException => throw Upstream5xxResponse(nf.getMessage, 404, 500)
    }
  }

  override def toString = s"DeskproConnector()"

  private def requestUrl[B, A](uri: String): String = s"$serviceBaseUrl$uri"
}
