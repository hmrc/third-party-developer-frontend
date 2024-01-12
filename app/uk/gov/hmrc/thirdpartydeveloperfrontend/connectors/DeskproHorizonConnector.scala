/*
 * Copyright 2024 HM Revenue & Customs
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

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.{CREATED, UNAUTHORIZED}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._

class DeskproHorizonConnector @Inject() (http: HttpClient, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger {

  lazy val serviceBaseUrl: String = config.deskproHorizonUrl
  val api                         = API("deskpro-horizon")

  def createTicket(deskproTicket: DeskproTicket)(implicit hc: HeaderCarrier): Future[TicketResult] = metrics.record(api) {
    http.POST[DeskproHorizonTicket, HttpResponse](
      requestUrl("/api/v2/tickets"),
      DeskproHorizonTicket.fromDeskproTicket(deskproTicket),
      Seq((AUTHORIZATION, config.deskproHorizonApiKey))
    )
      .map(response =>
        response.status match {
          case CREATED      =>
            logger.info(s"Deskpro horizon ticket '${deskproTicket.subject}' created successfully")
            TicketCreated
          case UNAUTHORIZED =>
            logger.error(s"Deskpro horizon ticket creation failed for: ${deskproTicket.subject}")
            logger.error(response.body)
            throw new DeskproTicketCreationFailed("Missing authorization")
          case status       =>
            logger.error(s"Deskpro horizon ticket creation failed for: ${deskproTicket.subject}")
            logger.error(response.body)
            throw new DeskproTicketCreationFailed("Unknown reason")
        }
      )
  }

  override def toString = "DeskproHorizonConnector()"

  private def requestUrl[B, A](uri: String): String = s"$serviceBaseUrl$uri"
}
