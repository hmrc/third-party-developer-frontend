/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import play.api.mvc.{Call, RequestHeader}
import play.filters.csrf.CSRF._

object SpliceCSRFToken {
  // Not materially different from play's play.filters.csrf.CSRF helper, but rather than
  // appending the token to the end of the query args, inserts it as the first query arg.
  def apply(call: Call)(implicit rh: RequestHeader): Call = {
    val token = getToken(rh).getOrElse { sys.error("No CSRF token present!") }
    val url = if (call.url.contains("?")) call.url.replace("?", s"?${token.name}=${token.value}&")
              else s"${call.url}?${token.name}=${token.value}"

    call.copy(url = url)
  }
}
