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
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HeaderNames.ACCEPT
import play.api.libs.ws.{WSClient, WSProxyServer, WSRequest => PlayWSRequest}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.config.RunMode
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.http.ws.{WSProxy, WSProxyConfiguration}

@Singleton
class ProxiedHttpClient @Inject()(config: Configuration,
                                  httpAuditing: HttpAuditing,
                                  wsClient: WSClient,
                                  environment: play.api.Environment,
                                  actorSystem: ActorSystem,
                                  runMode: RunMode)
  extends DefaultHttpClient(config, httpAuditing, wsClient, actorSystem) with WSProxy {

  val apiKeyHeader: Option[(String, String)] = None
  private val env = runMode.env

  def withHeaders(apiKey: String = ""): ProxiedHttpClient = {
    new ProxiedHttpClient(config, httpAuditing, wsClient, environment, actorSystem, runMode) {
      override val apiKeyHeader: Option[(String, String)] = if ("" == apiKey) None else Some(ProxiedHttpClient.API_KEY_HEADER_NAME -> apiKey)
    }
  }

  override def wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration(s"$env.proxy", config)

  override def buildRequest[A](url: String, headers: Seq[(String, String)]): PlayWSRequest = {
    val extraHeaders = Seq((ACCEPT -> "application/hmrc.vnd.1.0+json"))
    val extraHeadersWithMaybeApiKeyHeader =
      if (apiKeyHeader.isDefined) extraHeaders :+ apiKeyHeader.get
      else extraHeaders

    super.buildRequest(url, headers ++ extraHeadersWithMaybeApiKeyHeader)
  }
}

object ProxiedHttpClient {
  val API_KEY_HEADER_NAME = "x-api-key"
}
