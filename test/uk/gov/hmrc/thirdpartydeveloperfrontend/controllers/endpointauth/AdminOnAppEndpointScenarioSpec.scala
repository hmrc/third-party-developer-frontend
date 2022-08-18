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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Cookie, Request}
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.{UserIsAdminOnApplicationTeam, UserIsAuthenticated}

class AdminOnAppEndpointScenarioSpec extends EndpointScenarioSpec with UserIsAuthenticated with UserIsAdminOnApplicationTeam {
  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = { //TODO this belongs inside the UserIsAuthenticated trait
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    )
  }

  override def describeScenario(): String = "User is authenticated as an Admin on the application team"

  override def expectedResponses(): ExpectedResponses = ExpectedResponses(
    Redirect("/developer/login")
  )
}
