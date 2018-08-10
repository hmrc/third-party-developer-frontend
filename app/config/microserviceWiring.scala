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

package config

import play.api.http.HeaderNames.ACCEPT
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}
import uk.gov.hmrc.play.http.ws._

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: AuditConnector = ApplicationAuditConnector
}

trait WSHttp extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with Hooks with AppName
object WSHttp extends WSHttp

trait ProxiedApiPlatformWSHttp extends WSHttp with WSProxy with AppName with ServicesConfig {
  override lazy val wsProxyServer = WSProxyConfiguration(s"$env.proxy")

  val authorization: Authorization

  override def buildRequest[A](url: String)(implicit hc: HeaderCarrier) = {
    val hcWithBearerAndAccept = hc.copy(authorization = Some(authorization),
      extraHeaders = hc.extraHeaders :+  (ACCEPT -> "application/hmrc.vnd.1.0+json"))

    super.buildRequest(url)(hcWithBearerAndAccept)
  }
}

object ProxiedApiPlatformWSHttp {
  def apply(bearerToken: String) = new ProxiedApiPlatformWSHttp {
    override val authorization = Authorization(s"Bearer $bearerToken")
  }
}
