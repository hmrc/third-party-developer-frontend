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
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.metrics.common.API

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
    with ApmConnectorApplicationModule
    with ApmConnectorCombinedApisModule {

  val api = API("api-platform-microservice")

}
