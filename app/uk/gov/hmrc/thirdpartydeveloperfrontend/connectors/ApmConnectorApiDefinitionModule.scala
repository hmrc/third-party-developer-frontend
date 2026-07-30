/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.concurrent.Future
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.{HeaderCarrier, *}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ExtendedApiDefinition, MappedApiDefinitions, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.domain.services.EnumJsonHelper.asScreamingSnakeCase

object ApmConnectorApiDefinitionModule {
  val ApplicationIdQueryParam = "applicationId"
  val EnvironmentQueryParam   = "environment"
}

trait ApmConnectorApiDefinitionModule extends ApmConnectorModule {
  import ApmConnectorApiDefinitionModule._

  private val baseUrl = s"${config.serviceBaseUrl}/api-definitions"

  def fetchAllPossibleSubscriptions(applicationId: ApplicationId)(using HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams = Seq(
      ApplicationIdQueryParam -> applicationId.toString()
    )
    http.get(url"${baseUrl}?$queryParams")
      .execute[MappedApiDefinitions]
      .map(_.values.toList)
  }

  def fetchAllOpenAccessApis(environment: Environment)(using HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams = Seq(
      EnvironmentQueryParam -> environment.asScreamingSnakeCase
    )

    http.get(url"${baseUrl}/open?$queryParams")
      .execute[MappedApiDefinitions]
      .map(_.values.toList)
  }

  def fetchUpliftableApiIdentifiers(using HeaderCarrier): Future[Set[ApiIdentifier]] =
    metrics.record(api) {
      http.get(url"${baseUrl}/upliftable")
        .execute[Set[ApiIdentifier]]
    }

  def fetchAllApis(environment: Environment)(using HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams = Seq(
      EnvironmentQueryParam -> environment.asScreamingSnakeCase
    )
    http.get(url"${baseUrl}/all?$queryParams")
      .execute[MappedApiDefinitions]
      .map(_.values.toList)
  }

  def fetchExtendedApiDefinition(serviceName: ServiceName)(using HeaderCarrier): Future[Either[Throwable, ExtendedApiDefinition]] =
    http.get(url"${config.serviceBaseUrl}/combined-api-definitions/$serviceName")
      .execute[ExtendedApiDefinition]
      .map(Right(_))
      .recover {
        case NonFatal(e) => Left(e)
      }

  def fetchApiDefinitionsVisibleToUser(userId: Option[UserId])(using HeaderCarrier): Future[List[ApiDefinition]] = {
    val queryParams: Seq[(String, String)] = userId.fold(Seq.empty[(String, String)])(id => Seq("developerId" -> id.toString()))

    http.get(url"${config.serviceBaseUrl}/combined-api-definitions?$queryParams")
      .execute[List[ApiDefinition]]
  }

}
