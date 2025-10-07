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

import org.apache.pekko.pattern.FutureTimeoutSupport

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics)
    extends CommonResponseHandlers with ApplicationLogger with HttpErrorFunctions {

  protected val http: HttpClientV2
  implicit val ec: ExecutionContext
  val serviceBaseUrl: String

  val api: API = API("third-party-application")

  def fetchTermsOfUseInvitation(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[TermsOfUseInvitation]] = {
    metrics.record(api) {
      http.get(url"$serviceBaseUrl/terms-of-use/application/${applicationId}")
        .execute[Option[TermsOfUseInvitation]]
    }
  }
}

@Singleton
class ThirdPartyApplicationProductionConnector @Inject() (
    val http: HttpClientV2,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends ThirdPartyApplicationConnector(appConfig, metrics) {
  val serviceBaseUrl: String = appConfig.thirdPartyApplicationProductionUrl
}
