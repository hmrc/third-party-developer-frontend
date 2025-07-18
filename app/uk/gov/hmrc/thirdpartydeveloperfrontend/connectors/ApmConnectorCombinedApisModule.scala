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
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, _}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi

object ApmConnectorCombinedApisModule {
  val ApplicationIdQueryParam = "applicationId"
  val EnvironmentQueryParam   = "environment"
}

trait ApmConnectorCombinedApisModule extends ApmConnectorModule {

  private[this] val baseUrl = s"${config.serviceBaseUrl}/combined-rest-xml-apis"

  def fetchCombinedApi(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, CombinedApi]] =
    http.get(url"${baseUrl}/$serviceName")
      .execute[CombinedApi]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchCombinedApisVisibleToUser(userId: UserId)(implicit hc: HeaderCarrier): Future[Either[Throwable, List[CombinedApi]]] =
    http.get(url"${baseUrl}/developer?developerId=$userId")
      .execute[List[CombinedApi]]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

}
