/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import connectors.PushPullNotificationsConnector.readsPushSecret
import domain.models.applications.{ClientId, Environment}
import helpers.Retries
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Reads}
import service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}
import cats.data.OptionT
import uk.gov.hmrc.http.HttpReads.Implicits._

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
    OptionT(getWithAuthorization[Option[Seq[PushSecret]]](s"$serviceBaseUrl/client/${clientId.value}/secrets", hc))
    .map(ps => ps.map(_.value))
    .getOrElse(Seq.empty)
  }

  private def getWithAuthorization[A](url:String, hc: HeaderCarrier)(implicit rd: HttpReads[A]): Future[A] = {
    implicit val modifiedHeaderCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(authorizationKey)))
    retry {
      http.GET[A](url)
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
                                                   )(implicit val ec: ExecutionContext)
  extends AbstractPushPullNotificationsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String = appConfig.ppnsSandboxUrl
  val useProxy: Boolean = appConfig.ppnsSandboxUseProxy
  val apiKey: String = appConfig.ppnsSandboxApiKey
  val authorizationKey: String = appConfig.ppnsSandboxAuthorizationKey
}

@Singleton
class ProductionPushPullNotificationsConnector @Inject() (
                                                        val appConfig: ApplicationConfig,
                                                        val httpClient: HttpClient,
                                                        val proxiedHttpClient: ProxiedHttpClient,
                                                        val actorSystem: ActorSystem,
                                                        val futureTimeout: FutureTimeoutSupport
                                                      )(implicit val ec: ExecutionContext)
  extends AbstractPushPullNotificationsConnector {

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String = appConfig.ppnsProductionUrl
  val useProxy: Boolean = appConfig.ppnsProductionUseProxy
  val apiKey: String = appConfig.ppnsProductionApiKey
  val authorizationKey: String = appConfig.ppnsProductionAuthorizationKey
}
