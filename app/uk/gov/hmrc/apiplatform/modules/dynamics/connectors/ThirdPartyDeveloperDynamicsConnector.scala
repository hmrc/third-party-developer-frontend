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

package uk.gov.hmrc.apiplatform.modules.dynamics.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.CommonResponseHandlers

case class Ticket(ticketNumber: String, title: String, description: Option[String], statusCode: Int, id: String)

object Ticket {
  implicit val formatTickets: OFormat[Ticket] = Json.format[Ticket]
}

case class CreateIncidentRequest(customerId: String, title: String, description: String)

object CreateIncidentRequest {
  implicit val formatCreateIncidentRequest: OFormat[CreateIncidentRequest] = Json.format[CreateIncidentRequest]
}

@Singleton
class ThirdPartyDeveloperDynamicsConnector @Inject() (
    http: HttpClient,
    configuration: ApplicationConfig
  )(implicit ec: ExecutionContext
  ) extends CommonResponseHandlers {

  def getTickets()(implicit hc: HeaderCarrier): Future[List[Ticket]] = {
    http.GET[List[Ticket]](s"${configuration.thirdPartyDeveloperUrl}/incidents")
  }

  def createTicket(customerId: String, title: String, description: String)(implicit hc: HeaderCarrier): Future[Either[String, Unit]] = {
    http.POST[CreateIncidentRequest, ErrorOrUnit](
      s"${configuration.thirdPartyDeveloperUrl}/incidents",
      CreateIncidentRequest(s"/accounts($customerId)", title, description)
    )
      .map {
        case Right(x)                                      => Right(x)
        case Left(UpstreamErrorResponse(message, _, _, _)) => Left(message)
      }
  }
}
