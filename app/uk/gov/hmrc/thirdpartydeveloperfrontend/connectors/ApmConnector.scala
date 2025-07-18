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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ExtendedApiDefinition, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.OpenAccessApiService.OpenAccessApisConnector

object ApmConnector {
  case class Config(serviceBaseUrl: String)
}

trait ApmConnectorModule {
  def http: HttpClientV2
  def config: ApmConnector.Config
  def metrics: ConnectorMetrics
  def api: API
  implicit def ec: ExecutionContext
}

@Singleton
class ApmConnector @Inject() (val http: HttpClientV2, val config: ApmConnector.Config, val metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends OpenAccessApisConnector
    with CommonResponseHandlers
    with ApmConnectorSubscriptionFieldsModule
    with ApmConnectorApiDefinitionModule
    with ApmConnectorApplicationModule {

  val api = API("api-platform-microservice")

  def fetchExtendedApiDefinition(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, ExtendedApiDefinition]] =
    http.get(url"${config.serviceBaseUrl}/combined-api-definitions/$serviceName")
      .execute[ExtendedApiDefinition]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchCombinedApi(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, CombinedApi]] =
    http.get(url"${config.serviceBaseUrl}/combined-rest-xml-apis/$serviceName")
      .execute[CombinedApi]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchApiDefinitionsVisibleToUser(userId: Option[UserId])(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams: Seq[(String, String)] = userId.fold(Seq.empty[(String, String)])(id => Seq("developerId" -> id.toString()))

    http.get(url"${config.serviceBaseUrl}/combined-api-definitions?$queryParams")
      .execute[List[ApiDefinition]]
  }

  def fetchCombinedApisVisibleToUser(userId: UserId)(implicit hc: HeaderCarrier): Future[Either[Throwable, List[CombinedApi]]] =
    http.get(url"${config.serviceBaseUrl}/combined-rest-xml-apis/developer?developerId=$userId")
      .execute[List[CombinedApi]]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }
}
