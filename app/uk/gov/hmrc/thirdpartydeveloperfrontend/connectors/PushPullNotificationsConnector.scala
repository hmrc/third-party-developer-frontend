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

import cats.data.OptionT
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.FutureTimeoutSupport

import play.api.http.HeaderNames
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.EbridgeConfigurator
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.PushPullNotificationsConnector.readsPushSecret
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.Retries
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector

abstract class AbstractPushPullNotificationsConnector(implicit ec: ExecutionContext) extends PushPullNotificationsConnector with Retries {
  val http: HttpClientV2
  val environment: Environment
  val serviceBaseUrl: String
  val authorizationKey: String

  val api: API = API("push-pull-notifications-api")

  def configureEbridgeIfRequired: RequestBuilder => RequestBuilder

  def fetchPushSecrets(clientId: ClientId)(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    import cats.implicits._
    OptionT(getWithAuthorization[Option[Seq[PushSecret]]](s"$serviceBaseUrl/client/${clientId.value}/secrets"))
      .map(ps => ps.map(_.value))
      .getOrElse(Seq.empty)
  }

  private def getWithAuthorization[A](aUrl: String)(implicit rd: HttpReads[A], hc: HeaderCarrier): Future[A] = {
    retry {
      configureEbridgeIfRequired(
        http.get(url"$aUrl")
          .setHeader(HeaderNames.AUTHORIZATION -> authorizationKey)
      )
        .execute[A]
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
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractPushPullNotificationsConnector {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.ppnsSandboxUrl
  val useProxy: Boolean        = appConfig.ppnsSandboxUseProxy
  val apiKey: String           = appConfig.ppnsSandboxApiKey
  val authorizationKey: String = appConfig.ppnsSandboxAuthorizationKey

  lazy val configureEbridgeIfRequired: RequestBuilder => RequestBuilder =
    EbridgeConfigurator.configure(useProxy, apiKey)
}

@Singleton
class ProductionPushPullNotificationsConnector @Inject() (
    val appConfig: ApplicationConfig,
    val http: HttpClientV2,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext
  ) extends AbstractPushPullNotificationsConnector {

  val configureEbridgeIfRequired: RequestBuilder => RequestBuilder = identity

  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.ppnsProductionUrl
  val authorizationKey: String = appConfig.ppnsProductionAuthorizationKey
}
