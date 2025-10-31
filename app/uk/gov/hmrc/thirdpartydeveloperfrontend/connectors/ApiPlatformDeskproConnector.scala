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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CreateTicketRequest

@Singleton
class ApiPlatformDeskproConnector @Inject() (
    config: ApiPlatformDeskproConnector.Config,
    http: HttpClientV2,
    metrics: ConnectorMetrics
  )(implicit ec: ExecutionContext
  ) extends CommonResponseHandlers with ApplicationLogger {

  import ApiPlatformDeskproConnector._

  val api = API("api-platform-deskpro")

  def createTicket(createRequest: CreateTicketRequest, hc: HeaderCarrier): Future[Option[String]] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    http.post(url"${config.serviceBaseUrl}/ticket")
      .withBody(Json.toJson(createRequest))
      .execute[CreateTicketResponse]
      .map(_.ref)
  }

  def updatePersonName(userEmailAddress: LaxEmailAddress, name: String, hc: HeaderCarrier): Future[UpdateProfileResult] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    http.put(url"${config.serviceBaseUrl}/person")
      .withBody(Json.toJson(UpdatePersonRequest(userEmailAddress, name)))
      .execute[ErrorOrUnit]
      .map(throwOr(UpdateProfileSuccess))
      .recover(handleUpstreamErrors[UpdateProfileResult](UpdateProfileFailed))
  }

  private def handleUpstreamErrors[A](returnIfError: A): PartialFunction[Throwable, A] = (err: Throwable) => {
    logger.warn("Exception occurred when calling Deskpro", err)
    err match {
      case e: HttpException         => returnIfError
      case e: UpstreamErrorResponse => returnIfError
      case e: Throwable             => throw e
    }
  }
}

object ApiPlatformDeskproConnector {
  case class Config(serviceBaseUrl: String, authToken: String)

  case class UpdatePersonRequest(email: LaxEmailAddress, name: String)

  object UpdatePersonRequest {
    implicit val format: OFormat[UpdatePersonRequest] = Json.format[UpdatePersonRequest]
  }

  case class CreateTicketResponse(ref: Option[String])

  object CreateTicketResponse {
    implicit val createTicketResponseFormat: Format[CreateTicketResponse] = Json.format[CreateTicketResponse]
  }

  sealed trait UpdateProfileResult

  case object UpdateProfileSuccess extends UpdateProfileResult
  case object UpdateProfileFailed  extends UpdateProfileResult
}
