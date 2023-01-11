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

import java.util.UUID

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import play.api.http.HeaderNames
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.Configuration

class ProxiedHttpClientSpec extends AsyncHmrcSpec with GuiceOneServerPerSuite {

  val stubConfig = Configuration(
    "metrics.enabled" -> false,
    "auditing.enabled" -> false,
    "proxy.proxyRequiredForThisEnvironment" -> false
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .build()

  trait Setup {
    val apiKey: String = UUID.randomUUID().toString
    val url = "http://example.com"

    val underTest: ProxiedHttpClient = app.injector.instanceOf[ProxiedHttpClient]
  }

  "withHeaders" should {

    "creates a ProxiedHttpClient with passed in key" in new Setup {

      private val result = underTest.withHeaders(apiKey)

      result.apiKeyHeader shouldBe Some(apiKey)
    }

    "when apiKey is empty String, apiKey header is None" in new Setup {

      private val result = underTest.withHeaders()

      result.apiKeyHeader shouldBe None
    }
  }

  "buildRequest" should {
    "when building a request the api key is added" in new Setup {
      private val proxy = underTest.withHeaders(apiKey)

      val request = proxy.buildRequest("http://localhost:12345", Seq.empty)

      request.headers.get(ProxiedHttpClient.API_KEY_HEADER_NAME) shouldBe Some(Seq(apiKey))
    }

    "when building a request the accept header is added" in new Setup {
      private val proxy = underTest.withHeaders(apiKey)

      val request = proxy.buildRequest("http://localhost:12345", Seq.empty)

      request.headers.get(HeaderNames.ACCEPT) shouldBe Some(Seq(ProxiedHttpClient.ACCEPT_HMRC_JSON_HEADER._2))
    }

    "when building a request any additionaly headers are  added" in new Setup {
      private val proxy = underTest.withHeaders(apiKey)

      val request = proxy.buildRequest("http://localhost:12345", Seq(HeaderNames.USER_AGENT -> "ThisTest"))

      request.headers.get(HeaderNames.USER_AGENT) shouldBe Some(Seq("ThisTest"))
    }
  }
}
