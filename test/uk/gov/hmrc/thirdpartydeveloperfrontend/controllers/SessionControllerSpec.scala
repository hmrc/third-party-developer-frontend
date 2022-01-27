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

import java.util.UUID

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker

class SessionControllerSpec extends BaseControllerSpec with DeveloperBuilder with LocalUserIdTracker {

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

      val developer = buildDeveloper()
      val sessionId = UUID.randomUUID().toString
      val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
      val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val loggedInRequest = FakeRequest()
        .withLoggedIn(sessionController,implicitly)(session.sessionId)
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