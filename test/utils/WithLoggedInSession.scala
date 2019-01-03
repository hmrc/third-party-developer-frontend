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

package utils

import jp.t2v.lab.play2.auth.AuthConfig
import play.api.libs.Crypto
import play.api.mvc.Cookie
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

object WithLoggedInSession {

  implicit class AuthFakeRequest[A](fakeRequest: FakeRequest[A]) {

    // A replacement for the t2v `withLoggedIn` helper.
    // The t2v helper swaps the cookie for a header, which it uses in some special cases
    // but not others - including token extraction (which we need to get the session id
    // to log out).
    //
    // This helper faithfully reproduces the cookie-based session that we actually use.
    def withLoggedIn(implicit config: AuthConfig): config.Id => FakeRequest[A] = { id =>
      val token = Await.result(config.idContainer.startNewSession(id, Int.MaxValue)(fakeRequest, global), 10.seconds)
      def sign(token: String) = Crypto.sign(token) + token

      val cookie = Cookie("PLAY2AUTH_SESS_ID", sign(token))
      fakeRequest.withCookies(cookie)
    }
  }
}
