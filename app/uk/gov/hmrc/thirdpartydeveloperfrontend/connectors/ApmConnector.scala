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

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ExtendedApiDefinition, MappedApiDefinitions, ServiceName}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithSubscriptionFields
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.UpliftRequest
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.FieldName
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.OpenAccessApiService.OpenAccessApisConnector

object ApmConnector {
  case class Config(serviceBaseUrl: String)

  case class RequestUpliftV1(subscriptions: Set[ApiIdentifier])

  case class RequestUpliftV2(upliftRequest: UpliftRequest)

  implicit val writesV1: Writes[RequestUpliftV1] = play.api.libs.json.Json.writes[RequestUpliftV1]
  implicit val writesV2: Writes[RequestUpliftV2] = play.api.libs.json.Json.writes[RequestUpliftV2]
}

@Singleton
class ApmConnector @Inject() (http: HttpClientV2, config: ApmConnector.Config, metrics: ConnectorMetrics)(implicit ec: ExecutionContext)
    extends OpenAccessApisConnector
    with CommonResponseHandlers {

  import ApmConnector._
  import ApmConnectorJsonFormatters._

  val api = API("api-platform-microservice")

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionFields]] =
    http.get(url"${config.serviceBaseUrl}/applications/${applicationId}")
      .execute[Option[ApplicationWithSubscriptionFields]]

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]]] = {

    http.get(url"${config.serviceBaseUrl}/subscription-fields?environment=$environment")
      .execute[Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]]]
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    http.get(url"${config.serviceBaseUrl}/api-definitions?applicationId=${applicationId}")
      .execute[MappedApiDefinitions]
      .map(_.wrapped.values.toList)
  }

  def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    http.get(url"${config.serviceBaseUrl}/api-definitions/open?environment=$environment")
      .execute[MappedApiDefinitions]
      .map(_.wrapped.values.toList)
  }

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

  def fetchUpliftableApiIdentifiers(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/api-definitions/upliftable")
        .execute[Set[ApiIdentifier]]
    }

  def fetchUpliftableSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/applications/$applicationId/upliftableSubscriptions")
        .execute[Set[ApiIdentifier]]
    }

  def fetchAllApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] =
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/api-definitions/all?environment=$environment")
        .execute[MappedApiDefinitions]
        .map(_.wrapped.values.toList)
    }

  def upliftApplicationV1(applicationId: ApplicationId, subs: Set[ApiIdentifier])(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.post(url"${config.serviceBaseUrl}/applications/${applicationId}/uplift")
      .withBody(Json.toJson(RequestUpliftV1(subs)))
      .execute[ApplicationId]
  }

  def upliftApplicationV2(applicationId: ApplicationId, upliftData: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.post(url"${config.serviceBaseUrl}/applications/${applicationId}/uplift")
      .withBody(Json.toJson(RequestUpliftV2(upliftData)))
      .execute[ApplicationId]
  }
}
