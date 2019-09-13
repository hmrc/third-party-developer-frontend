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

package unit.controllers

import config.{ApplicationConfig, ErrorHandler}
import controllers.Navigation
import domain.{DeveloperSession, Developer, LoggedInState, NavLink, Session}
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.test.FakeRequest
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithLoggedInSession._

import scala.concurrent.Future._

class NavigationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val materializer = fakeApplication.materializer
  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
  val loggedInUser = DeveloperSession.apply(session)

  var userPassword = "Password1!"

  class Setup(loggedIn: Boolean) {
    val underTest = new Navigation(
      mock[SessionService],
      mock[ErrorHandler],
      mock[ApplicationConfig]
    )

    given(underTest.sessionService.fetch(ArgumentMatchers.eq(sessionId))(any[HeaderCarrier]))
      .willReturn(successful(Some(Session(sessionId, developer, LoggedInState.LOGGED_IN))))

    val request = if (loggedIn) FakeRequest().withLoggedIn(underTest)(sessionId) else FakeRequest()
    val result = await(underTest.navLinks()(request))
    val links = jsonBodyOf(result).as[Seq[NavLink]]
  }

  "navigation" when {
    "user is not logged in" should {
      "be successful" in new Setup(loggedIn = false) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return a register link" in new Setup(loggedIn = false) {
        links(0) shouldBe NavLink("Register", controllers.routes.Registration.register().url)
      }

      "return a sign in link" in new Setup(loggedIn = false) {
        links(1) shouldBe NavLink("Sign in", controllers.routes.UserLoginAccount.login().url)
      }
    }

    "user is logged in" should {
      "be successful" in new Setup(loggedIn = true) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return the user's profile link" in new Setup(loggedIn = true) {
        links(0) shouldBe NavLink("John Doe", controllers.routes.Profile.showProfile().url)
      }

      "return a signout link" in new Setup(loggedIn = true) {
        links(1) shouldBe NavLink("Sign out", controllers.routes.UserLogoutAccount.logoutSurvey().url)
      }
    }
  }
}
