/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.ApmConnector
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import model.APICategoryDetails
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class APIService @Inject()(apmConnector: ApmConnector)(implicit val ec: ExecutionContext) {
  
    def fetchAllAPICategoryDetails()(implicit hc: HeaderCarrier): Future[Seq[APICategoryDetails]] = apmConnector.fetchAllAPICategories()

    def fetchAPIDetails(apiServiceNames: Set[String])(implicit hc: HeaderCarrier): Future[Seq[ExtendedApiDefinition]] =
        Future.sequence(
            apiServiceNames
              .map(apmConnector.fetchAPIDefinition(_))
              .toSeq)

}
