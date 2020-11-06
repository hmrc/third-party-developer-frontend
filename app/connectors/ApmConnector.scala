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

package connectors

import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications._
import domain.models.subscriptions.{ApiData, FieldName}
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import javax.inject.{Inject, Singleton}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import domain.models.emailpreferences.APICategoryDetails

import scala.concurrent.{ExecutionContext, Future}
import domain.models.apidefinitions.ApiIdentifier
import domain.ApplicationUpdateSuccessful
import service.SubscriptionsService.SubscriptionsConnector
import uk.gov.hmrc.play.http.metrics.API
import uk.gov.hmrc.http.NotFoundException
import domain.ApplicationNotFound
import play.api.Logger
import domain.models.connectors.AddTeamMemberRequest
import uk.gov.hmrc.http.Upstream4xxResponse
import domain.TeamMemberAlreadyExists
import uk.gov.hmrc.http.HttpResponse

@Singleton
class ApmConnector @Inject() (http: HttpClient, config: ApmConnector.Config, metrics: ConnectorMetrics)(implicit ec: ExecutionContext) extends SubscriptionsConnector {
  import ApmConnectorJsonFormatters._

  val api = API("api-platform-microservice")

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] =
    http.GET[Option[ApplicationWithSubscriptionData]](s"${config.serviceBaseUrl}/applications/${applicationId.value}")

  def getAllFieldDefinitions(environment: Environment)
                            (implicit hc: HeaderCarrier): Future[Map[ApiContext,Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]] = {
    import domain.services.ApplicationsJsonFormatters._
    import domain.services.SubscriptionsJsonFormatters._

    http.GET[Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]](s"${config.serviceBaseUrl}/subscription-fields?environment=$environment")
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] = {
    http.GET[Map[ApiContext, ApiData]](s"${config.serviceBaseUrl}/api-definitions?applicationId=${applicationId.value}")
  }

  def fetchAllAPICategories()(implicit  hc: HeaderCarrier): Future[Seq[APICategoryDetails]] =
    http.GET[Seq[APICategoryDetails]](s"${config.serviceBaseUrl}/api-categories")

  def fetchAPIDefinition(serviceName: String)(implicit hc: HeaderCarrier): Future[ExtendedApiDefinition] =
    http.GET[ExtendedApiDefinition](s"${config.serviceBaseUrl}/combined-api-definitions/$serviceName")

  def fetchApiDefinitionsVisibleToUser(userEmail: String)(implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] =
    http.GET[Seq[ApiDefinition]](s"${config.serviceBaseUrl}/combined-api-definitions", Seq("collaboratorEmail" -> userEmail))

  def subscribeToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"${config.serviceBaseUrl}/applications/${applicationId.value}/subscriptions", apiIdentifier, Seq(CONTENT_TYPE -> JSON)) map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def addTeamMember(applicationId: ApplicationId, addTeamMember: AddTeamMemberRequest)(implicit hc: HeaderCarrier): Future[Unit] = metrics.record(api) {
    val handleTeamMemberAlreadyExists: PartialFunction[Throwable, Unit] = {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => throw new TeamMemberAlreadyExists
    }

    http.POST[AddTeamMemberRequest, HttpResponse](s"${config.serviceBaseUrl}/applications/${applicationId.value}/collaborators", addTeamMember)
    .map(_ => ())
    .recover(handleTeamMemberAlreadyExists orElse recovery)
  }


  private def recovery: PartialFunction[Throwable, Nothing] = {
    case e: NotFoundException => {
      Logger.warn(e.message)
      throw new ApplicationNotFound
    }
  }


}

object ApmConnector {
  case class Config(
      serviceBaseUrl: String
  )
}
