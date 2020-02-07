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

import java.net.URLEncoder.encode

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import domain.ApiSubscriptionFields._
import domain.Environment
import helpers.Retries
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

abstract class ApiSubscriptionFieldsConnector(private val environment: Environment,
                                              private val serviceBaseUrl: String,
                                              private val useProxy: Boolean,
                                              private val bearerToken: String,
                                              private val apiKey: String,
                                              private val httpClient: HttpClient,
                                              private val proxiedHttpClient: ProxiedHttpClient) extends Retries {

  implicit val ec: ExecutionContext

  val http: HttpClient = {
    if (useProxy) {
      Logger.debug(s"Using Proxy Server ($environment)")
      proxiedHttpClient.withHeaders(bearerToken, apiKey)
    } else {
      Logger.debug(s"Not using Proxy Server ($environment)")
      httpClient
    }
  }

  def fetchFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Option[SubscriptionFields]] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    Logger.debug(s"fetchFieldValues() - About to call $url in ${environment.toString}")
    retry {
      http.GET[SubscriptionFields](url).map(Some(_))
    } recover recovery(None)

  }

  def fetchFieldDefinitions(apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
    val url = urlSubscriptionFieldDefinition(apiContext, apiVersion)
    Logger.debug(s"fetchFieldDefinitions() - About to call $url in ${environment.toString}")
    retry {
      http.GET[FieldDefinitions](url).map(response => response.fieldDefinitions)
    } recover recovery(Seq.empty[SubscriptionField])
  }

  // TODO: Test AllFieldDefinitionsResponse
  def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[Seq[FieldDefinitions]] = {
    val url = urlSubscriptionFieldDefinitionForAll()
    Logger.debug(s"fetchAllFieldDefinitions() - About to call $url in ${environment.toString}")
    retry {
      http.GET[AllFieldDefinitionsResponse](url).map(response => response.apis)
    } recover recovery(Seq.empty[FieldDefinitions])
  }

  private def urlSubscriptionFieldDefinition(apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/definition/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"

  private def urlSubscriptionFieldDefinitionForAll() = s"$serviceBaseUrl/definition"


  private def urlEncode(str: String, encoding: String = "UTF-8") = {
    encode(str, encoding)
  }

  private def recovery[T](value: T): PartialFunction[Throwable, T] = {
    case _: NotFoundException => value
  }

  def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    http.PUT[SubscriptionFieldsPutRequest, HttpResponse](url, SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fields))
  }

  private def urlSubscriptionFieldValues(clientId: String, apiContext: String, apiVersion: String) =
    s"$serviceBaseUrl/field/application/${urlEncode(clientId)}/context/${urlEncode(apiContext)}/version/${urlEncode(apiVersion)}"

  def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = urlSubscriptionFieldValues(clientId, apiContext, apiVersion)
    val eventualResponse = http.DELETE(url)
    eventualResponse map {
      _.status == NO_CONTENT
    } recover recovery(true)
  }
}

@Singleton
class ApiSubscriptionFieldsSandboxConnector @Inject()(val httpClient: HttpClient,
                                                      val proxiedHttpClient: ProxiedHttpClient,
                                                      val actorSystem: ActorSystem,
                                                      val futureTimeout: FutureTimeoutSupport,
                                                      val appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
  extends ApiSubscriptionFieldsConnector(
    Environment.SANDBOX,
    appConfig.apiSubscriptionFieldsSandboxUrl,
    appConfig.apiSubscriptionFieldsSandboxUseProxy,
    appConfig.apiSubscriptionFieldsSandboxBearerToken,
    appConfig.apiSubscriptionFieldsSandboxApiKey,
    httpClient,
    proxiedHttpClient) {
}

@Singleton
class ApiSubscriptionFieldsProductionConnector @Inject()(val httpClient: HttpClient,
                                                         val proxiedHttpClient: ProxiedHttpClient,
                                                         val actorSystem: ActorSystem,
                                                         val futureTimeout: FutureTimeoutSupport,
                                                         val appConfig: ApplicationConfig)(implicit val ec: ExecutionContext)
  extends ApiSubscriptionFieldsConnector(
    Environment.PRODUCTION,
    appConfig.apiSubscriptionFieldsProductionUrl,
    appConfig.apiSubscriptionFieldsProductionUseProxy,
    appConfig.apiSubscriptionFieldsProductionBearerToken,
    appConfig.apiSubscriptionFieldsProductionApiKey,
    httpClient,
    proxiedHttpClient) {
}

