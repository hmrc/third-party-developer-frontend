/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.Configuration
import play.api.http.HeaderNames.ACCEPT
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.http.ws.{WSProxy, WSProxyConfiguration}

@Singleton
class ProxiedHttpClient @Inject()(config: Configuration,
                                  auditConnector: AuditConnector,
                                  wsClient: WSClient,
                                  environment: play.api.Environment)
  extends DefaultHttpClient(config, auditConnector, wsClient) with WSProxy {

  val authorization: Option[Authorization] = None
  private val env = RunMode(environment.mode, config).env

  def withAuthorization(bearerToken: String) = new ProxiedHttpClient(config, auditConnector, wsClient, environment) {
    override val authorization = Some(Authorization(s"Bearer $bearerToken"))
  }

  override def wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration(s"$env.proxy")

  override def buildRequest[A](url: String)(implicit hc: HeaderCarrier) = {
    val hcWithBearerAndAccept = hc.copy(authorization = authorization,
      extraHeaders = hc.extraHeaders :+ (ACCEPT -> "application/hmrc.vnd.1.0+json"))

    super.buildRequest(url)(hcWithBearerAndAccept)
  }
}
