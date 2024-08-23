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

package uk.gov.hmrc.apiplatformmicroservice.common.utils

import play.api.http.HeaderNames
import uk.gov.hmrc.http.client.RequestBuilder

object EbridgeConfigurator {

  def configure(useProxy: Boolean, bearerToken: String, apiKey: String): RequestBuilder => RequestBuilder = (requestBuilder) =>
    if (useProxy)
      requestBuilder
        .withProxy
        .setHeader(buildHeaders(bearerToken, apiKey): _*)
    else
      requestBuilder

  private def buildHeaders(bearerToken: String, apiKey: String): Seq[(String, String)] = {
    val conditionalHeader = if (apiKey.isEmpty) Seq.empty[(String, String)] else Seq("x-api-key" -> apiKey)

    Seq(
      HeaderNames.AUTHORIZATION -> s"Bearer $bearerToken"
    ) ++ conditionalHeader
  }
}
