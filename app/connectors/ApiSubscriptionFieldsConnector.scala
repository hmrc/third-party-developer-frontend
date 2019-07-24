/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URLEncoder.encode

import config.ApplicationConfig
import domain.ApiSubscriptionFields._
import domain.Environment
import javax.inject.{Inject, Singleton}
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

abstract class ApiSubscriptionFieldsConnector {
  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  implicit val ec: ExecutionContext
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val bearerToken: String

  val http: HttpClient = {
    if (useProxy) {
      proxiedHttpClient.wsProxyServer.map(
        proxyServer =>
          Logger.debug(s"Using Proxy Server with username '${proxyServer.principal.getOrElse("")}' and host ${proxyServer.host}")
      )
      proxiedHttpClient.withAuthorization(bearerToken)
    }
    else httpClient
  }

  def fetchFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Option[SubscriptionFields]] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    Logger.debug(s"fetchFieldValues() - About to call $url in ${environment.toString}")
    http.GET[SubscriptionFields](url).map(Some(_)) recover recovery(None)
  }

  def fetchFieldDefinitions(apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
    val url = urlSubscriptionFieldDefinition(apiContext, apiVersion)
    Logger.debug(s"fetchFieldDefinitions() - About to call $url in ${environment.toString}")
    http.GET[FieldDefinitionsResponse](url).map(response => response.fieldDefinitions) recover recovery(Seq.empty[SubscriptionField])
  }

  def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    http.PUT[SubscriptionFieldsPutRequest, HttpResponse](url, SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields))
  }

  def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    val eventualResponse = http.DELETE(url)
    eventualResponse map {
      _.status == NO_CONTENT
    } recover recovery(true)
  }

  private def recovery[T](value: T): PartialFunction[Throwable, T] = {
    case _: NotFoundException => value
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = {
    encode(str, encoding)
  }

  private def urlSubscriptionFieldValues(clientId: String, apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/field/application/${urlEncode(clientId)}/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"

  private def urlSubscriptionFieldDefinition(apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/definition/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"
}

@Singleton
class ApiSubscriptionFieldsSandboxConnector @Inject()(val httpClient: HttpClient,
                                                      val proxiedHttpClient: ProxiedHttpClient,
                                                      appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
  extends ApiSubscriptionFieldsConnector {

  val environment = Environment.SANDBOX
  val serviceBaseUrl = appConfig.apiSubscriptionFieldsSandboxUrl
  val useProxy = appConfig.apiSubscriptionFieldsSandboxUseProxy
  val bearerToken = appConfig.apiSubscriptionFieldsSandboxBearerToken
}

@Singleton
class ApiSubscriptionFieldsProductionConnector @Inject()(val httpClient: HttpClient,
                                                         val proxiedHttpClient: ProxiedHttpClient,
                                                         appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
  extends ApiSubscriptionFieldsConnector {

  val environment = Environment.PRODUCTION
  val serviceBaseUrl = appConfig.apiSubscriptionFieldsProductionUrl
  val useProxy = appConfig.apiSubscriptionFieldsProductionUseProxy
  val bearerToken = appConfig.apiSubscriptionFieldsProductionBearerToken
}

