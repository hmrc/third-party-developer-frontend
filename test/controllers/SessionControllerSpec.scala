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

package controllers

import java.util.UUID

import config.ErrorHandler
import connectors.ThirdPartyDeveloperConnector
import domain.{Developer, LoggedInState, Session}
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import play.api.http.Status._
import play.api.i18n.MessagesApi
import play.api.test.Helpers.redirectLocation
import play.api.test.{DefaultAwaitTimeout, FakeRequest}
import play.filters.csrf.CSRF.TokenProvider
import service.{AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._

import scala.concurrent.Future

class SessionControllerSpec extends BaseControllerSpec with DefaultAwaitTimeout{

  val mockSessionService: SessionService = mock[SessionService]
  val sessionController = new SessionController(
    mock[AuditService],
    mockSessionService,
    mock[ThirdPartyDeveloperConnector],
    mock[ErrorHandler],
    mock[MessagesApi]
  )

  "keepAlive" should {

    "reset the session if logged in" in {

      val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
      val session = Session(UUID.randomUUID().toString, developer, LoggedInState.LOGGED_IN)
      val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

      given(mockSessionService.fetch(any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Some(session)))

      val loggedInRequest = FakeRequest()
        .withLoggedIn(sessionController,implicitly)(session.sessionId)
        .withSession(sessionParams: _*)

      val result = await(sessionController.keepAlive()(loggedInRequest))

      status(result) shouldBe NO_CONTENT
    }

    "return not authenticated if not logged in" in {
      val request = FakeRequest()

      val result = await(sessionController.keepAlive()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}
