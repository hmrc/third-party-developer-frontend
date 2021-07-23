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

package controllers

import builder.DeveloperBuilder
import config.ErrorHandler
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import domain.models.views.NavLink
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import play.api.http.Status.OK
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import utils.LocalUserIdTracker

class NavigationSpec extends BaseControllerSpec with DeveloperBuilder with LocalUserIdTracker {
  class Setup(loggedInState: Option[LoggedInState]) extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock {
    val underTest = new Navigation(
      sessionServiceMock,
      mock[ErrorHandler],
      applicationServiceMock,
      applicationActionServiceMock,
      mcc,
      cookieSigner
    )

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInUser = DeveloperSession(session)

    var userPassword = "Password1!"

    loggedInState.map(loggedInState => {
      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, loggedInState))
    })

    private val request =
      if (loggedInState.isDefined) {
        FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
      }
      else {
        FakeRequest()
      }

    val result = underTest.navLinks()(request)
    val links: Seq[NavLink] = contentAsJson(result).as[Seq[NavLink]]
  }

  "navigation" when {
    "user is not logged in" should {
      "be successful" in new Setup(loggedInState = None) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return a register link" in new Setup(loggedInState = None) {
        links.head shouldBe NavLink("Register", routes.Registration.register().url)
      }

      "return a sign in link" in new Setup(loggedInState = None) {
        links(1) shouldBe NavLink("Sign in", routes.UserLoginAccount.login().url)
      }
    }

    "user is logged in" should {
      "be successful" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return the user's profile link" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        links.head shouldBe NavLink("John Doe", controllers.profile.routes.Profile.showProfile().url)
      }

      "return a sign-out link" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        links(1) shouldBe NavLink("Sign out", routes.UserLogoutAccount.logoutSurvey().url)
      }
    }

    "user is part logged in enabling MFA in" should {
      "be successful" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return the user's profile link" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        links.head shouldBe NavLink("Register", routes.Registration.register().url)
      }

      "return a sign-out link" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        links(1) shouldBe NavLink("Sign in", routes.UserLoginAccount.login().url)
      }
    }
  }
}
