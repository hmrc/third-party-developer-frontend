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

import java.util.UUID

import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.config.RunMode
import utils.AsyncHmrcSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ProxiedHttpClientSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  trait Setup {
    val apiKey: String = UUID.randomUUID().toString
    val url = "http://example.com"
    val mockConfig: Configuration = mock[Configuration]
    val mockHttpAuditing: HttpAuditing = mock[HttpAuditing]
    val mockWsClient: WSClient = mock[WSClient]
    val mockEnvironment: Environment = mock[Environment]
    val mockRunMode: RunMode = mock[RunMode]

    when(mockEnvironment.mode).thenReturn(Mode.Test)
    when(mockConfig.getString(*, *)).thenReturn(Some(*))
    when(mockConfig.getOptional[Int](*)).thenReturn(Some(0))
    when(mockConfig.getOptional[Boolean]("Test.proxy.proxyRequiredForThisEnvironment")).thenReturn(Some(true))
    when(mockWsClient.url(url)).thenReturn(mock[WSRequest])

    val underTest = new ProxiedHttpClient(mockConfig, mockHttpAuditing, mockWsClient, mockEnvironment, app.actorSystem, mockRunMode)
  }

  "withHeaders" should {

    "creates a ProxiedHttpClient with passed in headers" in new Setup {

      private val result = underTest.withHeaders(apiKey)

      result.apiKeyHeader shouldBe Some("x-api-key" -> apiKey)
    }

    "when apiKey is empty String, apiKey header is None" in new Setup {

      private val result = underTest.withHeaders("")

      result.apiKeyHeader shouldBe None
    }

    "when apiKey isn't provided, apiKey header is None" in new Setup {

      private val result = underTest.withHeaders()

      result.apiKeyHeader shouldBe None
    }
  }
}
