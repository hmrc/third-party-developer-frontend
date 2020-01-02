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

package unit.controllers

import akka.stream.Materializer
import config.{ApplicationConfig, ErrorHandler}
import controllers.Navigation
import domain._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.mvc.Result
import play.api.test.FakeRequest
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithLoggedInSession._

import scala.concurrent.Future._

class NavigationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val materializer: Materializer = fakeApplication.materializer
  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
  val loggedInUser = DeveloperSession(session)

  var userPassword = "Password1!"

  class Setup(loggedInState: Option[LoggedInState]) {
    val underTest = new Navigation(
      mock[SessionService],
      mock[ErrorHandler],
      mock[ApplicationConfig]
    )

    loggedInState.map(loggedInState => {
      given(underTest.sessionService.fetch(ArgumentMatchers.eq(sessionId))(any[HeaderCarrier]))
        .willReturn(successful(Some(Session(sessionId, developer, loggedInState))))
    })

    private val request =
      if (loggedInState.isDefined) {
        FakeRequest().withLoggedIn(underTest)(sessionId)
      }
      else {
        FakeRequest()
      }

    val result: Result = await(underTest.navLinks()(request))
    val links: Seq[NavLink] = jsonBodyOf(result).as[Seq[NavLink]]
  }

  "navigation" when {
    "user is not logged in" should {
      "be successful" in new Setup(loggedInState = None) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return a register link" in new Setup(loggedInState = None) {
        links.head shouldBe NavLink("Register", controllers.routes.Registration.register().url)
      }

      "return a sign in link" in new Setup(loggedInState = None) {
        links(1) shouldBe NavLink("Sign in", controllers.routes.UserLoginAccount.login().url)
      }
    }

    "user is logged in" should {
      "be successful" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return the user's profile link" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        links.head shouldBe NavLink("John Doe", controllers.routes.Profile.showProfile().url)
      }

      "return a sign-out link" in new Setup(loggedInState = Some(LoggedInState.LOGGED_IN)) {
        links(1) shouldBe NavLink("Sign out", controllers.routes.UserLogoutAccount.logoutSurvey().url)
      }
    }

    "user is part logged in enabling MFA in" should {
      "be successful" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        status(result) shouldBe OK
        links.size shouldBe 2
      }

      "return the user's profile link" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        links.head shouldBe NavLink("Register", controllers.routes.Registration.register().url)
      }

      "return a sign-out link" in new Setup(loggedInState = Some(LoggedInState.PART_LOGGED_IN_ENABLING_MFA)) {
        links(1) shouldBe NavLink("Sign in", controllers.routes.UserLoginAccount.login().url)
      }
    }
  }
}
