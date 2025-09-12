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
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._

@Singleton
class DeskproConnector @Inject() (http: HttpClientV2, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger {

  lazy val serviceBaseUrl: String = config.deskproUrl
  val api                         = API("deskpro")

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
