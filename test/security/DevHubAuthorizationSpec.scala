/*
 * Copyright 2021 HM Revenue & Customs
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

package security

import cats.implicits._
import config.{ApplicationConfig, ErrorHandler}
import controllers.{routes, BaseController, BaseControllerSpec}
import domain.models.developers.{DeveloperSession, LoggedInState}
import org.scalatest.Matchers
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Cookie, MessagesControllerComponents}
import play.api.mvc.Results.{EmptyContent, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DeveloperSession => DeveloperSessionBuilder}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class DevHubAuthorizationSpec extends BaseControllerSpec with Matchers {
  class TestDevHubAuthorization(mcc: MessagesControllerComponents)(implicit val appConfig: ApplicationConfig, val ec: ExecutionContext)
      extends BaseController(mcc)
      with ExtendedDevHubAuthorization {
    override val sessionService: SessionService = mock[SessionService]
    override val errorHandler: ErrorHandler = mock[ErrorHandler]
    override val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]
  }

  class Setup(developerSession: Option[DeveloperSession]) {
    when(appConfig.securedCookie).thenReturn(false)

    val underTest = new TestDevHubAuthorization(mcc)
    val sessionId = "sessionId"

    val loggedInAction = underTest.loggedInAction { _ => Future.successful(Ok(EmptyContent())) }

    val atLeastPartLoggedInAction = underTest.atLeastPartLoggedInEnablingMfaAction { _ => Future.successful(Ok(EmptyContent())) }

    val request = FakeRequest().withCookies(underTest.createCookie(sessionId))
    val requestWithNoCookie = FakeRequest()
    val requestWithInvalidCookie = FakeRequest().withCookies(Cookie("PLAY2AUTH_SESS_ID", "InvalidCookieValue"))
    val requestWithNoRealSession = FakeRequest().withCookies(underTest.createCookie(sessionId))

    when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).thenReturn(successful(developerSession.map(ds => ds.session)))
    when(underTest.sessionService.updateUserFlowSessions(*)).thenReturn(successful(()))
  }

  val loggedInDeveloperSession = DeveloperSessionBuilder("Email", "firstName", "lastName", loggedInState = LoggedInState.LOGGED_IN)
  val partLoggedInDeveloperSession = DeveloperSessionBuilder("Email", "firstName", "lastName", loggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  "DebHubAuthWrapper" when {
    "the user is logged in and" when {
      "controller action is decorated with loggedInAction" should {
        "successfully execute action" in new Setup(loggedInDeveloperSession.some) {
          val result = loggedInAction()(request)

          status(result) shouldBe OK
          verify(underTest.sessionService).updateUserFlowSessions(loggedInDeveloperSession.session.sessionId)
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in new Setup(loggedInDeveloperSession.some) {
          val result = atLeastPartLoggedInAction()(request)

          status(result) shouldBe OK
          verify(underTest.sessionService).updateUserFlowSessions(loggedInDeveloperSession.session.sessionId)
        }
      }
    }

    "the user is part logged in and" when {
      "controller action is decorated with loggedInAction" should {
        "redirect to login page" in new Setup(partLoggedInDeveloperSession.some) {
          val result = loggedInAction()(request)

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
          verify(underTest.sessionService, times(0)).updateUserFlowSessions(partLoggedInDeveloperSession.session.sessionId)
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in new Setup(partLoggedInDeveloperSession.some) {
          val result = atLeastPartLoggedInAction()(request)

          status(result) shouldBe OK
          verify(underTest.sessionService).updateUserFlowSessions(partLoggedInDeveloperSession.session.sessionId)
        }
      }
    }

    "the user is not logged in" when {
      "they have no cookie" in new Setup(None) {
        val result = atLeastPartLoggedInAction()(requestWithNoCookie)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(underTest.sessionService, times(0)).updateUserFlowSessions(partLoggedInDeveloperSession.session.sessionId)
      }

      "they have a cookie but it is invalid" in new Setup(None) {
        val result = atLeastPartLoggedInAction()(requestWithInvalidCookie)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(underTest.sessionService, times(0)).updateUserFlowSessions(partLoggedInDeveloperSession.session.sessionId)
      }

      "they have a valid cookie but it does not exist in the session service" in new Setup(None) {
        when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
          .thenReturn(successful(None))

        val result = atLeastPartLoggedInAction()(requestWithNoRealSession)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(underTest.sessionService, times(0)).updateUserFlowSessions(partLoggedInDeveloperSession.session.sessionId)
      }
    }
  }
}
