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

package config

import controllers.{DevHubAuthWrapper, routes}
import domain.LoggedInState
import org.scalatest.mockito.MockitoSugar
import org.scalatest.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.libs.crypto.CookieSigner
import play.api.mvc.Results.EmptyContent
import play.api.test.FakeRequest
import service.SessionService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.BDDMockito.given
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import utils.{DeveloperSession, SharedMetricsClearDown}
import play.api.mvc.Results._
import play.api.http.Status._
import play.api.mvc.Cookie
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DevHubAuthWrapperSpec extends UnitSpec with MockitoSugar with Matchers with GuiceOneAppPerTest with SharedMetricsClearDown {
  "DebHubAuthWrapper" when {
    implicit val applicationConfig: ApplicationConfig = mock[ApplicationConfig]
    given(applicationConfig.securedCookie).willReturn(false)

    class TestDevHubAuthWrapper(implicit val appConfig: ApplicationConfig) extends DevHubAuthWrapper {
      override val sessionService: SessionService = mock[SessionService]
      override val cookieSigner: CookieSigner = fakeApplication.injector.instanceOf[CookieSigner]
    }

    val underTest = new TestDevHubAuthWrapper()
    val sessionId = "sessionId"

    "the user is logged in and" when {
      val developerSession = DeveloperSession("Email", "firstName", "lastName", loggedInState = LoggedInState.LOGGED_IN)

      implicit val request = FakeRequest().withCookies(underTest.createCookie(sessionId))
      given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).willReturn(Some(developerSession.session))

      "controller action is decorated with loggedInAction" should {
        "successfully execute action" in {
          val action = underTest.loggedInAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in {
          val action = underTest.atLeastPartLoggedInEnablingMfaAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }
    }

    "the user is part logged in and" when {
      val developerSession = utils.DeveloperSession("Email", "firstName", "lastName", loggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

      implicit val request = FakeRequest().withCookies(underTest.createCookie(sessionId))
      given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).willReturn(Some(developerSession.session))

      "controller action is decorated with loggedInAction" should {
        "redirect to login page" in {
          val action = underTest.loggedInAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in {
          val action = underTest.atLeastPartLoggedInEnablingMfaAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }
    }

    "the user is not logged in" when {
      val action = underTest.atLeastPartLoggedInEnablingMfaAction { implicit request =>
        Future.successful(Ok(EmptyContent()))
      }

      "they have no cookie" in {
        implicit val requestWithNoCookie = FakeRequest()

        val result = await(action()(requestWithNoCookie))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      }

      "they have a cookie but it is invalid" in {
        implicit val requestWithInvalidCookie = FakeRequest().withCookies(Cookie("PLAY2AUTH_SESS_ID","InvalidCookieValue"))

        val result = await(action()(requestWithInvalidCookie))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      }

      "they have a valid cookie but it does not exist in the session service" in {
        implicit val requestWithNoRealSession = FakeRequest().withCookies(underTest.createCookie(sessionId))

        given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
          .willReturn(None)

        val result = await(action()(requestWithNoRealSession))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      }
    }
  }
}
