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

import java.net.URLEncoder.encode
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.FutureTimeoutSupport

import play.api.http.Status.{BAD_REQUEST, CREATED, OK}
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.common.utils.EbridgeConfigurator
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorDomain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.Retries
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector

private[connectors] object SubscriptionFieldsConnectorDomain {

  case class SubscriptionFieldsPutRequest(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fields: Fields
    )

  object SubscriptionFieldsPutRequest {
    import play.api.libs.json.Format
    implicit val formatSubscriptionFieldsPutRequest: Format[SubscriptionFieldsPutRequest] = Json.format[SubscriptionFieldsPutRequest]
  }
}

abstract class AbstractSubscriptionFieldsConnector(implicit ec: ExecutionContext) extends SubscriptionFieldsConnector with Retries with ApplicationLogger {
  val http: HttpClientV2
  val environment: Environment
  val serviceBaseUrl: String

  def configureEbridgeIfRequired: RequestBuilder => RequestBuilder

  import uk.gov.hmrc.http.HttpReads.Implicits._

  def saveFieldValues(
      clientId: ClientId,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      fields: Fields
    )(implicit hc: HeaderCarrier
    ): Future[ConnectorSaveSubscriptionFieldsResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)

    configureEbridgeIfRequired(
      http
        .put(url"$url")
        .withBody(Json.toJson(SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields)))
    )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case BAD_REQUEST  =>
            Json.parse(response.body).validate[Map[String, String]] match {
              case s: JsSuccess[Map[String, String]] => SaveSubscriptionFieldsFailureResponse(s.get)
              case _                                 => SaveSubscriptionFieldsFailureResponse(Map.empty)
            }
          case OK | CREATED => SaveSubscriptionFieldsSuccessResponse
          case statusCode   => throw UpstreamErrorResponse("Failed to put subscription fields", statusCode)
        }
      }
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = encode(str, encoding)

  private def urlSubscriptionFieldValues(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersionNbr) =
    s"$serviceBaseUrl/field/application/${urlEncode(clientId.value)}/context/${urlEncode(apiContext.value)}/version/${urlEncode(apiVersion.value)}"
}

@Singleton
class SandboxSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.apiSubscriptionFieldsSandboxUrl
  val useProxy: Boolean        = appConfig.apiSubscriptionFieldsSandboxUseProxy
  val apiKey: String           = appConfig.apiSubscriptionFieldsSandboxApiKey

  lazy val configureEbridgeIfRequired: RequestBuilder => RequestBuilder =
    EbridgeConfigurator.configure(useProxy, apiKey)

}

@Singleton
class ProductionSubscriptionFieldsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractSubscriptionFieldsConnector {

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.apiSubscriptionFieldsProductionUrl

  val configureEbridgeIfRequired: RequestBuilder => RequestBuilder = identity
}
