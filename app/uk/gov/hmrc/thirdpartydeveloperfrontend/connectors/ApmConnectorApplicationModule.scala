/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.concurrent.Future

import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, _}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptionFields
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.UpliftRequest
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

object ApmConnectorApplicationModule {
  import play.api.libs.json._

  case class RequestUpliftV1(subscriptions: Set[ApiIdentifier])

  case class RequestUpliftV2(upliftRequest: UpliftRequest)

  implicit val writesV1: Writes[RequestUpliftV1] = Json.writes[RequestUpliftV1]
  implicit val writesV2: Writes[RequestUpliftV2] = Json.writes[RequestUpliftV2]
}

trait ApmConnectorApplicationModule extends ApmConnectorModule {
  import ApmConnectorApplicationModule._

  private[this] val baseUrl = s"${config.serviceBaseUrl}/applications"

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionFields]] =
    http.get(url"${baseUrl}/${applicationId}")
      .execute[Option[ApplicationWithSubscriptionFields]]

  def fetchUpliftableSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.get(url"${baseUrl}/$applicationId/upliftableSubscriptions")
        .execute[Set[ApiIdentifier]]
    }

  def upliftApplicationV2(applicationId: ApplicationId, upliftData: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.post(url"${baseUrl}/${applicationId}/uplift")
      .withBody(Json.toJson(RequestUpliftV2(upliftData)))
      .execute[ApplicationId]
  }
}
