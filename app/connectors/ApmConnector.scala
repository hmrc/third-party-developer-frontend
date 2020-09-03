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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApmConnector @Inject() (http: HttpClient, config: ApmConnector.Config)(implicit ec: ExecutionContext) {
  import ApmConnectorJsonFormatters._

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] =
    http.GET[Option[ApplicationWithSubscriptionData]](s"${config.serviceBaseUrl}/applications/${applicationId.value}")

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[ApiContext,Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]] = {
    import domain.services.ApplicationsJsonFormatters._
    import domain.services.SubscriptionsJsonFormatters._

    http.GET[Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]](s"${config.serviceBaseUrl}/subscription-fields?environment=$environment")
  }

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Map[ApiContext, ApiData]] = {
    http.GET[Map[ApiContext, ApiData]](s"${config.serviceBaseUrl}/api-definitions?applicationId=${applicationId.value}")
  }
}

object ApmConnector {
  case class Config(
      serviceBaseUrl: String
  )
}
