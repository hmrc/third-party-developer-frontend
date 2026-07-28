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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ConnectorMetrics}

// Test-only subclass that overrides `fetchAllOpenAccessApis` to make it stubbable. On `ApmConnector` that
// method is declared in one trait and implemented in another, a form Mockito cannot replace, so stubs on it
// are silently ignored.
class TestApmConnector(http: HttpClientV2, config: ApmConnector.Config, metrics: ConnectorMetrics)(implicit ec: ExecutionContext)
    extends ApmConnector(http, config, metrics) {

  override def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] =
    super.fetchAllOpenAccessApis(environment)
}
