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
import javax.inject.{Inject, Singleton}
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class ApiSubscriptionFieldsConnector {
  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  val serviceBaseUrl: String
  val useProxy: Boolean
  val bearerToken: String

  def http: HttpClient = if (useProxy) proxiedHttpClient.withAuthorization(bearerToken) else httpClient

  def fetchFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Option[SubscriptionFields]] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    http.GET[SubscriptionFields](url).map(Some(_)) recover recovery(None)
  }

  def fetchFieldDefinitions(apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
    val url = urlSubscriptionFieldDefinition(apiContext, apiVersion)
    http.GET[FieldDefinitionsResponse](url).map(response => response.fieldDefinitions) recover recovery(Seq.empty[SubscriptionField])
  }

  def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    http.PUT[SubscriptionFieldsPutRequest, HttpResponse](url, SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields))
  }

  def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    val eventualResponse = http.DELETE(url)
    eventualResponse map { _.status == NO_CONTENT } recover recovery(true)
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
                                                      appConfig: ApplicationConfig)
  extends ApiSubscriptionFieldsConnector {

  val serviceBaseUrl = appConfig.apiSubscriptionFieldsSandboxUrl
  val useProxy = appConfig.apiSubscriptionFieldsSandboxUseProxy
  val bearerToken = appConfig.apiSubscriptionFieldsSandboxBearerToken
}

@Singleton
class ApiSubscriptionFieldsProductionConnector @Inject()(val httpClient: HttpClient,
                                                         val proxiedHttpClient: ProxiedHttpClient,
                                                         appConfig: ApplicationConfig)
  extends ApiSubscriptionFieldsConnector {

  val serviceBaseUrl = appConfig.apiSubscriptionFieldsProductionUrl
  val useProxy = appConfig.apiSubscriptionFieldsProductionUseProxy
  val bearerToken = appConfig.apiSubscriptionFieldsProductionBearerToken
}

