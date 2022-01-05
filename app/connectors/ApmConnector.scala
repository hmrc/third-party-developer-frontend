/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import domain.models.applications._
import domain.models.connectors.{AddTeamMemberRequest, ApiDefinition, CombinedApi, ExtendedApiDefinition}
import domain.models.developers.UserId
import domain.models.emailpreferences.APICategoryDisplayDetails
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import domain.models.subscriptions.{ApiData, FieldName}
import domain.{ApplicationNotFound, ApplicationUpdateSuccessful, TeamMemberAlreadyExists}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{CONFLICT, NOT_FOUND}
import service.OpenAccessApiService.OpenAccessApisConnector
import service.SubscriptionsService.SubscriptionsConnector
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.metrics.common.API

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object ApmConnector {
  case class Config(serviceBaseUrl: String)

  case class RequestUpliftV1(subscriptions: Set[ApiIdentifier])

  case class RequestUpliftV2(upliftRequest: UpliftData)

  import domain.services.ApiDefinitionsJsonFormatters._

  implicit val writesV1 = play.api.libs.json.Json.writes[RequestUpliftV1]
  implicit val writesV2 = play.api.libs.json.Json.writes[RequestUpliftV2]
}

@Singleton
class ApmConnector @Inject()(http: HttpClient, config: ApmConnector.Config, metrics: ConnectorMetrics)(implicit ec: ExecutionContext)
  extends SubscriptionsConnector
    with OpenAccessApisConnector
    with CommonResponseHandlers {

  import ApmConnector._
  import ApmConnectorJsonFormatters._

  val api = API("api-platform-microservice")

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] =
    http.GET[Option[ApplicationWithSubscriptionData]](s"${config.serviceBaseUrl}/applications/${applicationId.value}")

  def getAllFieldDefinitions(environment: Environment)
                            (implicit hc: HeaderCarrier): Future[Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]] = {

    http.GET[Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]](s"${config.serviceBaseUrl}/subscription-fields", Seq("environment" -> environment.toString))
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] = {
    http.GET[Map[ApiContext, ApiData]](s"${config.serviceBaseUrl}/api-definitions", Seq("applicationId" -> applicationId.value))
  }

  def fetchAllOpenAccessApis(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] = {
    http.GET[Map[ApiContext, ApiData]](s"${config.serviceBaseUrl}/api-definitions/open", Seq("environment" -> environment.toString))
  }

  @deprecated("This is no longer used, please use fetchAllCombinedAPICategories")
  def fetchAllAPICategories()(implicit hc: HeaderCarrier): Future[List[APICategoryDisplayDetails]] =
    http.GET[List[APICategoryDisplayDetails]](s"${config.serviceBaseUrl}/api-categories")


  def fetchAllCombinedAPICategories()(implicit hc: HeaderCarrier): Future[Either[Throwable, List[APICategoryDisplayDetails]]] =
    http.GET[List[APICategoryDisplayDetails]](s"${config.serviceBaseUrl}/api-categories/combined")
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchAPIDefinition(serviceName: String)(implicit hc: HeaderCarrier): Future[ExtendedApiDefinition] =
    http.GET[ExtendedApiDefinition](s"${config.serviceBaseUrl}/combined-api-definitions/$serviceName")

  def fetchCombinedApi(serviceName: String)(implicit hc: HeaderCarrier): Future[Either[Throwable, CombinedApi]] =
    http.GET[CombinedApi](s"${config.serviceBaseUrl}/combined-apis/$serviceName")
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchApiDefinitionsVisibleToUser(userId: UserId)(implicit hc: HeaderCarrier): Future[List[ApiDefinition]] =
    http.GET[List[ApiDefinition]](s"${config.serviceBaseUrl}/combined-api-definitions", Seq("developerId" -> userId.asText))

  def fetchCombinedApisVisibleToUser(userId: UserId)(implicit hc: HeaderCarrier): Future[Either[Throwable, List[CombinedApi]]] =
    http.GET[List[CombinedApi]](s"${config.serviceBaseUrl}/combined-apis", Seq("developerId" -> userId.asText))
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def subscribeToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)
                    (implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST[ApiIdentifier, ErrorOrUnit](s"${config.serviceBaseUrl}/applications/${applicationId.value}/subscriptions", apiIdentifier, Seq(CONTENT_TYPE -> JSON))
      .map(throwOrOptionOf)
      .map {
        case Some(_) => ApplicationUpdateSuccessful
        case None => throw new ApplicationNotFound
      }
  }

  def addTeamMember(applicationId: ApplicationId, addTeamMember: AddTeamMemberRequest)(implicit hc: HeaderCarrier): Future[Unit] = metrics.record(api) {
    http.POST[AddTeamMemberRequest, ErrorOrUnit](s"${config.serviceBaseUrl}/applications/${applicationId.value}/collaborators", addTeamMember)
      .map {
        case Left(UpstreamErrorResponse(_, CONFLICT, _, _)) => throw new TeamMemberAlreadyExists
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new ApplicationNotFound
        case Left(err) => throw err
        case Right(_) => ()
      }
  }

  def fetchUpliftableApiIdentifiers(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.GET[Set[ApiIdentifier]](s"${config.serviceBaseUrl}/api-definitions/upliftable")
    }

  def fetchUpliftableSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.GET[Set[ApiIdentifier]](s"${config.serviceBaseUrl}/applications/${applicationId.value}/upliftableSubscriptions")
    }

  def fetchAllApis(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] =
    metrics.record(api) {
      http.GET[Map[ApiContext, ApiData]](s"${config.serviceBaseUrl}/api-definitions/all", Seq("environment" -> environment.toString()))
    }

  def upliftApplicationV1(applicationId: ApplicationId, subs: Set[ApiIdentifier])(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.POST[RequestUpliftV1, ApplicationId](s"${config.serviceBaseUrl}/applications/${applicationId.value}/uplift", RequestUpliftV1(subs))
  }

  def upliftApplicationV2(applicationId: ApplicationId, upliftData: UpliftData)(implicit hc: HeaderCarrier): Future[ApplicationId] = metrics.record(api) {
    http.POST[RequestUpliftV2, ApplicationId](s"${config.serviceBaseUrl}/applications/${applicationId.value}/uplift", RequestUpliftV2(upliftData))
  }
}
