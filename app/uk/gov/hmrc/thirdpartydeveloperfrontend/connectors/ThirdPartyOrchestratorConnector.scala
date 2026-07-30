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

import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.*
import uk.gov.hmrc.apiplatform.modules.applications.query.domain.models.ApplicationQuery
import uk.gov.hmrc.apiplatform.modules.applications.query.domain.services.QueryParamsToQueryStringMap
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.domain.services.EnumJsonHelper.asScreamingSnakeCase
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.*
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.*

@Singleton
class ThirdPartyOrchestratorConnector @Inject() (http: HttpClientV2, config: ApplicationConfig, metrics: ConnectorMetrics)(using ExecutionContext)
    extends CommonResponseHandlers with ApplicationLogger with HttpErrorFunctions {

  val serviceBaseUrl: String = config.thirdPartyOrchestratorUrl

  val api: API = API("third-party-orchestrator")

  def create(request: CreateApplicationRequest)(using HeaderCarrier): Future[ApplicationCreatedResponse] =
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/application")
        .withBody(Json.toJson(request))
        .execute[ApplicationWithCollaborators]
        .map(a => ApplicationCreatedResponse(a.id))
    }

  def verify(verificationCode: String)(using HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    http.post(url"$serviceBaseUrl/verify-uplift/$verificationCode")
      .execute[ErrorOrUnit]
      .map {
        case Right(_)                                          => ApplicationVerificationSuccessful
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => ApplicationVerificationFailed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))   => throw new ApplicationNotFound
        case Left(err)                                         => throw err
      }
  }

  def validateName(name: String, selfApplicationId: Option[ApplicationId], environment: Environment)(using HeaderCarrier): Future[ApplicationNameValidationResult] = {

    val body = selfApplicationId.fold[ApplicationNameValidationRequest](NewApplicationNameValidationRequest(name))(appId => ChangeApplicationNameValidationRequest(name, appId))

    http.post(url"$serviceBaseUrl/environment/${environment.asScreamingSnakeCase}/application/name/validate")
      .withBody(Json.toJson[ApplicationNameValidationRequest](body))
      .execute[Option[ApplicationNameValidationResult]]
      .map {
        case Some(x) => x
        case None    => throw new ApplicationNotFound
      }
  }

  def query[T](environment: Environment)(qry: ApplicationQuery)(using hc: HeaderCarrier, rds: HttpReads[T]): Future[T] = {
    val qryStringMap = QueryParamsToQueryStringMap.toQuery(qry).map {
      case (k, vs) => k.text -> vs.mkString
    }

    http
      .get(url"${serviceBaseUrl}/environment/${environment.asScreamingSnakeCase}/query?$qryStringMap")
      .execute[T]
  }
}
