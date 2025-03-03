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

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.CreateApplicationRequest
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationVerificationFailed, ApplicationVerificationResponse, ApplicationVerificationSuccessful}

@Singleton
class ThirdPartyOrchestratorConnector @Inject() (http: HttpClientV2, config: ApplicationConfig, metrics: ConnectorMetrics)(implicit ec: ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger with HttpErrorFunctions {

  val serviceBaseUrl: String = config.thirdPartyOrchestratorUrl

  val api: API = API("third-party-orchestrator")

  def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/application")
        .withBody(Json.toJson(request))
        .execute[ApplicationWithCollaborators]
        .map(a => ApplicationCreatedResponse(a.id))
    }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    http.post(url"$serviceBaseUrl/verify-uplift/$verificationCode")
      .execute[ErrorOrUnit]
      .map {
        case Right(_)                                          => ApplicationVerificationSuccessful
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => ApplicationVerificationFailed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))   => throw new ApplicationNotFound
        case Left(err)                                         => throw err
      }
  }
}
