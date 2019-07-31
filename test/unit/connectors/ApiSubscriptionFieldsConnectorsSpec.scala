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

package unit.connectors

import akka.actor.ActorSystem
import config.ApplicationConfig
import connectors.{ApiSubscriptionFieldsProductionConnector, ApiSubscriptionFieldsSandboxConnector, ProxiedHttpClient}
import domain.ApiSubscriptionFields.{FieldDefinitionsResponse, SubscriptionField}
import org.mockito.Mockito.{verify, when}
import org.mockito.Matchers.{any, eq => meq}
import play.api.libs.ws.WSProxyServer
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApiSubscriptionFieldsConnectorsSpec extends BaseConnectorSpec {

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val actorSystem = ActorSystem("test-actor-system")

  trait Setup {
    val sandboxBearerToken = "sandbox-bearer-token"

    val fields = List(
      SubscriptionField("field1", "desc1", "hint1", "some type"),
      SubscriptionField("field2", "desc2", "hint2", "some other type"))

    val validResponse = FieldDefinitionsResponse(fields)

    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockApplicationConfig: ApplicationConfig = mock[ApplicationConfig]
    private val mockWSProxyServer = mock[WSProxyServer]

    when(mockWSProxyServer.principal).thenReturn(None)
    when(mockWSProxyServer.host).thenReturn("")

    when(mockApplicationConfig.apiSubscriptionFieldsSandboxUrl).thenReturn("https://api-subs-sandbox")
    when(mockApplicationConfig.apiSubscriptionFieldsSandboxUseProxy).thenReturn(true)
    when(mockApplicationConfig.apiSubscriptionFieldsSandboxBearerToken).thenReturn(sandboxBearerToken)

    when(mockApplicationConfig.apiSubscriptionFieldsProductionUrl).thenReturn("https://api-subs-production")
    when(mockApplicationConfig.apiSubscriptionFieldsProductionUseProxy).thenReturn(false)
    when(mockApplicationConfig.apiSubscriptionFieldsProductionBearerToken).thenReturn("")

    when(mockProxiedHttpClient.wsProxyServer).thenReturn(Some(mockWSProxyServer))
    when(mockProxiedHttpClient.withAuthorization(any())).thenReturn(mockProxiedHttpClient)
  }

  "SandboxApiSubscriptionsFieldConnector" should {

    "use proxied http client" in new Setup {
      val connector = new ApiSubscriptionFieldsSandboxConnector(
        mockHttpClient, mockProxiedHttpClient, actorSystem, mockApplicationConfig)

      when(mockProxiedHttpClient.GET[FieldDefinitionsResponse](any())(any(), any(), any())).thenReturn(Future.successful(validResponse))

      await(connector.fetchFieldDefinitions("my-context", "my-version"))

      verify(mockProxiedHttpClient).withAuthorization(sandboxBearerToken)
      verify(mockProxiedHttpClient).GET[FieldDefinitionsResponse](
        meq("https://api-subs-sandbox/definition/context/my-context/version/my-version"))(any(), any(), any())
    }
  }

  "ProductionApiSubscriptionsFieldConnector" should {
    "use non-proxied http client" in new Setup {
      val connector = new ApiSubscriptionFieldsProductionConnector(
        mockHttpClient, mockProxiedHttpClient, actorSystem, mockApplicationConfig)

      when(mockHttpClient.GET[FieldDefinitionsResponse](any())(any(), any(), any())).thenReturn(Future.successful(validResponse))

      await(connector.fetchFieldDefinitions("my-context", "my-version"))

      verify(mockHttpClient).GET[FieldDefinitionsResponse](
        meq("https://api-subs-production/definition/context/my-context/version/my-version"))(any(), any(), any())
    }
  }
}
