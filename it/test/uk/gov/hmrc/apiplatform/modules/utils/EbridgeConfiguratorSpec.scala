/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.utils

import uk.gov.hmrc.http.client.RequestBuilder

import uk.gov.hmrc.apiplatform.modules.common.utils.EbridgeConfigurator
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class EbridgeConfiguratorSpec extends AsyncHmrcSpec {

  trait Setup {
    val apiKey         = "api-key"
    val requestBuilder = mock[RequestBuilder]

    when(requestBuilder.withProxy).thenReturn(requestBuilder)
    when(requestBuilder.setHeader(*[(String, String)])).thenReturn(requestBuilder)
  }

  "EbridgeConfigurator" when {
    "using configure" should {
      "add a proxy and api key header when useProxy is true" in new Setup {
        EbridgeConfigurator.configure(useProxy = true, apiKey)(requestBuilder)

        verify(requestBuilder, times(1)).withProxy
        verify(requestBuilder, times(1)).setHeader(
          eqTo(("x-api-key", apiKey))
        )
      }

      "add a proxy when useProxy is true and apiKey is not supplied" in new Setup {
        EbridgeConfigurator.configure(useProxy = true, "")(requestBuilder)

        verify(requestBuilder, times(1)).withProxy
        verify(requestBuilder, never).setHeader(
          eqTo(("x-api-key", ""))
        )
        verify(requestBuilder, times(1)).setHeader(*)
      }

      "not add a proxy or header to RequestBuilder when useProxy is false" in new Setup {
        EbridgeConfigurator.configure(useProxy = false, apiKey)(requestBuilder)

        verify(requestBuilder, never).withProxy
        verify(requestBuilder, never).setHeader(*)
      }
    }
  }
}
