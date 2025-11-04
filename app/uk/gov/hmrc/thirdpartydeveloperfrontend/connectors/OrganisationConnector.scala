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

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.Organisation
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

@Singleton
class OrganisationConnector @Inject() (http: HttpClientV2, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger {

  lazy val serviceBaseUrl: String = config.organisationUrl
  val api                         = API("organisation")

  def fetchOrganisationsByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[List[Organisation]] = {
    metrics.record(api) {
      // TODO: remove the swallowing of errors once api-platform-organisation is in Production
      http.get(requestUrl(s"/organisation/user/$userId"))
        .execute[List[Organisation]] recover {
        case _: Throwable => List.empty[Organisation]
      }
    }
  }

  override def toString = "OrganisationConnector()"

  private def requestUrl[B, A](path: String): URL = {
    val concat = s"${serviceBaseUrl}${path}"
    url"$concat"
  }
}
