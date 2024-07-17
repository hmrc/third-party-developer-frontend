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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.tpd.builder.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class SessionControllerSpec extends BaseControllerSpec with UserBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {

    val sessionController = new SessionController(
      mock[AuditService],
      sessionServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mock[ErrorHandler],
      mcc,
      cookieSigner
    )
  }

  "keepAlive" should {
    "reset the session if logged in" in new Setup {

      val developer                            = buildTrackedUser()
      val sessionId                            = UserSessionId.random
      val session                              = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)
      val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val loggedInRequest = FakeRequest()
        .withLoggedIn(sessionController, implicitly)(session.sessionId)
        .withSession(sessionParams: _*)

      val result = sessionController.keepAlive()(loggedInRequest)

      status(result) shouldBe NO_CONTENT
    }

    "return not authenticated if not logged in" in new Setup {
      val request = FakeRequest()

      val result = sessionController.keepAlive()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}
