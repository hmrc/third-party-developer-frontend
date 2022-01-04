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

package qr

import java.net.URI

import javax.inject.Inject

class OtpAuthUri @Inject()() {
  def apply(secret: String, issuer: String, user: String) = {
    val params = Map("secret" -> secret, "issuer" -> issuer)
    val query = params.map(pair => pair._1 + "=" + pair._2).mkString("&")
    new URI("otpauth", "totp", s"/$issuer:$user", query, null)
  }
}

