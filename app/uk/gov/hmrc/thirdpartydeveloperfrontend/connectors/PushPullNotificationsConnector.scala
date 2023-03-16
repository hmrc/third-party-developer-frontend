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

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import cats.data.OptionT

import play.api.http.HeaderNames
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.PushPullNotificationsConnector.readsPushSecret
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.Retries
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector

abstract class AbstractPushPullNotificationsConnector(implicit ec: ExecutionContext) extends PushPullNotificationsConnector with Retries {
  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val apiKey: String
  val authorizationKey: String

  val api: API = API("push-pull-notifications-api")

  def http: HttpClient =
    if (useProxy) proxiedHttpClient.withHeaders(apiKey) else httpClient

  def fetchPushSecrets(clientId: ClientId)(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    import cats.implicits._
    OptionT(getWithAuthorization[Option[Seq[PushSecret]]](s"$serviceBaseUrl/client/${clientId.value}/secrets"))
      .map(ps => ps.map(_.value))
      .getOrElse(Seq.empty)
  }

  private def getWithAuthorization[A](url: String)(implicit rd: HttpReads[A], hc: HeaderCarrier): Future[A] = {
    retry {
      http.GET[A](url, Seq.empty, Seq(HeaderNames.AUTHORIZATION -> authorizationKey))
    }
  }
}

object PushPullNotificationsConnector {
  implicit val readsPushSecret: Reads[PushSecret] = Json.reads[PushSecret]
}

private[connectors] case class PushSecret(value: String)

@Singleton
class SandboxPushPullNotificationsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractPushPullNotificationsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.ppnsSandboxUrl
  val useProxy: Boolean        = appConfig.ppnsSandboxUseProxy
  val apiKey: String           = appConfig.ppnsSandboxApiKey
  val authorizationKey: String = appConfig.ppnsSandboxAuthorizationKey
}

@Singleton
class ProductionPushPullNotificationsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractPushPullNotificationsConnector {

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.ppnsProductionUrl
  val useProxy: Boolean        = appConfig.ppnsProductionUseProxy
  val apiKey: String           = appConfig.ppnsProductionApiKey
  val authorizationKey: String = appConfig.ppnsProductionAuthorizationKey
}
