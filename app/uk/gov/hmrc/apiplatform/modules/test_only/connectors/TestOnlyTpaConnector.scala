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

package uk.gov.hmrc.apiplatform.modules.test_only.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.EbridgeConfigurator
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

abstract class TestOnlyTpaConnector @Inject() (
    http: HttpClientV2,
    config: ApplicationConfig
  )(implicit val ec: ExecutionContext
  ) {

  val environment: Environment
  val serviceBaseUrl: String
  def isEnabled: Boolean

  def configureEbridgeIfRequired: RequestBuilder => RequestBuilder

  def clone(environment: Environment)(appId: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationWithCollaborators] = {
    if (isEnabled) {
      val url = s"$serviceBaseUrl/test-only/application"

      configureEbridgeIfRequired(
        http.post(url"$url/$appId/clone")
      )
        .execute[ApplicationWithCollaborators]
    } else {
      throw new IllegalArgumentException("Cannot clone sandbox apps when sandbox is disabled")
    }
  }
}

@Singleton
class TestOnlyTpaSandboxConnector @Inject() (
    val http: HttpClientV2,
    val appConfig: ApplicationConfig
  )(implicit ec: ExecutionContext
  ) extends TestOnlyTpaConnector(http, appConfig)(ec) {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.thirdPartyApplicationSandboxUrl
  val useProxy: Boolean        = appConfig.thirdPartyApplicationSandboxUseProxy
  val apiKey: String           = appConfig.thirdPartyApplicationSandboxApiKey

  lazy val configureEbridgeIfRequired: RequestBuilder => RequestBuilder =
    EbridgeConfigurator.configure(useProxy, apiKey)

  override val isEnabled: Boolean = appConfig.hasSandbox;
}

@Singleton
class TestOnlyTpaProductionConnector @Inject() (
    val http: HttpClientV2,
    val appConfig: ApplicationConfig
  )(implicit ec: ExecutionContext
  ) extends TestOnlyTpaConnector(http, appConfig)(ec) {
  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.thirdPartyApplicationProductionUrl

  val configureEbridgeIfRequired: RequestBuilder => RequestBuilder = identity

  override val isEnabled = true
}
