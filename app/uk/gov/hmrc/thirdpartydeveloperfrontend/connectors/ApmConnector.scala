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

import play.api.libs.json.Writes
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ExtendedApiDefinition, MappedApiDefinitions, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldName
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.OpenAccessApiService.OpenAccessApisConnector

object ApmConnector {
  case class Config(serviceBaseUrl: String)

  case class RequestUpliftV1(subscriptions: Set[ApiIdentifier])

  case class RequestUpliftV2(upliftRequest: UpliftData)

  implicit val writesV1: Writes[RequestUpliftV1] = play.api.libs.json.Json.writes[RequestUpliftV1]
  implicit val writesV2: Writes[RequestUpliftV2] = play.api.libs.json.Json.writes[RequestUpliftV2]
}

@Singleton
class ApmConnector @Inject() (http: HttpClient, config: ApmConnector.Config, metrics: ConnectorMetrics)(implicit ec: ExecutionContext)
    extends OpenAccessApisConnector
    with CommonResponseHandlers {

  import ApmConnector._
  import ApmConnectorJsonFormatters._

  val api = API("api-platform-microservice")

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] =
    http.GET[Option[ApplicationWithSubscriptionData]](s"${config.serviceBaseUrl}/applications/${applicationId}")

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]]] = {

    http.GET[Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, SubscriptionFieldDefinition]]]](
      s"${config.serviceBaseUrl}/subscription-fields",
      Seq("environment" -> environment.toString)
    )
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    http.GET[MappedApiDefinitions](s"${config.serviceBaseUrl}/api-definitions", Seq("applicationId" -> applicationId.toString))
      .map(_.wrapped.values.toList)
  }

  def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    http.GET[MappedApiDefinitions](s"${config.serviceBaseUrl}/api-definitions/open", Seq("environment" -> environment.toString))
      .map(_.wrapped.values.toList)
  }

  def fetchExtendedApiDefinition(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, ExtendedApiDefinition]] =
    http.GET[ExtendedApiDefinition](s"${config.serviceBaseUrl}/combined-api-definitions/$serviceName")
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchCombinedApi(serviceName: ServiceName)(implicit hc: HeaderCarrier): Future[Either[Throwable, CombinedApi]] =
    http.GET[CombinedApi](s"${config.serviceBaseUrl}/combined-rest-xml-apis/$serviceName")
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchApiDefinitionsVisibleToUser(userId: Option[UserId])(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams: Seq[(String, String)] = userId.fold(Seq.empty[(String, String)])(id => Seq("developerId" -> id.toString()))

    http.GET[List[ApiDefinition]](s"${config.serviceBaseUrl}/combined-api-definitions", queryParams)
  }

  def fetchCombinedApisVisibleToUser(userId: UserId)(implicit hc: HeaderCarrier): Future[Either[Throwable, List[CombinedApi]]] =
    http.GET[List[CombinedApi]](s"${config.serviceBaseUrl}/combined-rest-xml-apis/developer", Seq("developerId" -> userId.toString()))
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchUpliftableApiIdentifiers(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.GET[Set[ApiIdentifier]](s"${config.serviceBaseUrl}/api-definitions/upliftable")
    }

  def fetchUpliftableSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.GET[Set[ApiIdentifier]](s"${config.serviceBaseUrl}/applications/${applicationId.toString()}/upliftableSubscriptions")
    }

  def fetchAllApis(environment: Environment)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] =
    metrics.record(api) {
      http.GET[MappedApiDefinitions](s"${config.serviceBaseUrl}/api-definitions/all", Seq("environment" -> environment.toString))
        .map(_.wrapped.values.toList)
    }

  def upliftApplicationV1(applicationId: ApplicationId, subs: Set[ApiIdentifier])(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.POST[RequestUpliftV1, ApplicationId](s"${config.serviceBaseUrl}/applications/${applicationId.toString()}/uplift", RequestUpliftV1(subs))
  }

  def upliftApplicationV2(applicationId: ApplicationId, upliftData: UpliftData)(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.POST[RequestUpliftV2, ApplicationId](s"${config.serviceBaseUrl}/applications/${applicationId.toString()}/uplift", RequestUpliftV2(upliftData))
  }
}
