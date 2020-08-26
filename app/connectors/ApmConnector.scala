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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import domain.models.applications.ApplicationId
import scala.concurrent.ExecutionContext
import domain.models.applications.ApplicationWithSubscriptionData
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future
import domain.models.applications.Environment
import domain.models.apidefinitions.{ApiContext,ApiVersion}
import domain.models.subscriptions.FieldName
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import play.api.libs.json.Reads

@Singleton
class ApmConnector @Inject() (http: HttpClient, config: ApmConnector.Config)(implicit ec: ExecutionContext) {
  import domain.services.ApplicationJsonFormatters._

  def fetchApplicationById(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithSubscriptionData]] =
    http.GET[Option[ApplicationWithSubscriptionData]](s"${config.serviceBaseUrl}/applications/${applicationId.value}")

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier) = {
    import domain.services.FieldsJsonFormatters._
    import domain.services.ApplicationJsonFormatters._

    // implicit val keyReadsFieldName: KeyReads[FieldName] = key => JsSuccess(FieldName(key))

    implicitly[Reads[Map[FieldName,String]]]
    implicitly[Reads[Map[ApiContext,String]]]
    implicitly[Reads[Map[ApiVersion,String]]]
    implicitly[Reads[Map[ApiContext, Map[ApiVersion, Map[FieldName, String]]]]]
    implicitly[Reads[SubscriptionFieldDefinition]]
    
    http.GET[Map[ApiContext, Map[ApiVersion, Map[FieldName, SubscriptionFieldDefinition]]]](s"${config.serviceBaseUrl}/subscription-fields?environment=$environment")
  }
    
}

object ApmConnector {
  case class Config(
      val serviceBaseUrl: String
  )
}
