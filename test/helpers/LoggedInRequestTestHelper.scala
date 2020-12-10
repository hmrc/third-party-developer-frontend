/*
 * Copyright 2020 HM Revenue & Customs
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

package helpers

import builder.DeveloperBuilder
import controllers.BaseControllerSpec
import domain.models.developers.{LoggedInState, Session}
import mocks.service.SessionServiceMock
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.filters.csrf.CSRF.TokenProvider
import security.CookieEncoding
import service.SessionService
import utils.WithLoggedInSession._

trait LoggedInRequestTestHelper extends SessionServiceMock with CookieEncoding with DeveloperBuilder {
  this: BaseControllerSpec =>
    val sessionService = mock[SessionService]

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
   
    private val sessionParams = Seq(
      "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
    )

    lazy val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(this, implicitly)(sessionId)
      .withSession(sessionParams: _*)
}
