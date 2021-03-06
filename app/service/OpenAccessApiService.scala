/*
 * Copyright 2021 HM Revenue & Customs
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

package service

import domain.models.apidefinitions._
import domain.models.applications._
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.subscriptions.ApiData
import connectors.ApmConnector

@Singleton
class OpenAccessApiService @Inject() (openAccessApisConnector: ApmConnector)(implicit val ec: ExecutionContext) {
  def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] = 
    openAccessApisConnector.fetchAllOpenAccessApis(environment)
}

object OpenAccessApiService {
  trait OpenAccessApisConnector {
    def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]]
  }
}
