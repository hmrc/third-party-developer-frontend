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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnectorApiDefinitionModule

@Singleton
class OpenAccessApiService @Inject() (openAccessApisConnector: ApmConnectorApiDefinitionModule)(implicit val ec: ExecutionContext) {

  def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] =
    openAccessApisConnector.fetchAllOpenAccessApis(environment)
}

object OpenAccessApiService {

  trait OpenAccessApisConnector {
    def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]]
  }
}
