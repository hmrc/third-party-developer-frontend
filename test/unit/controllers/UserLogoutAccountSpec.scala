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

import java.util.UUID

import config.ApplicationConfig
import controllers._
import domain.{Developer, Session}
import org.mockito.BDDMockito.given
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF._
import service.{DeskproService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class UserLogoutAccountSpec extends UnitSpec with MockitoSugar with WithFakeApplication with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val user = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = UUID.randomUUID().toString
  val session = Session(sessionId, user)

  trait Setup {
    val underTest = new UserLogoutAccount(
      mock[DeskproService],
      mock[SessionService],
      mock[config.ErrorHandler],
      mock[ApplicationConfig])

    given(underTest.sessionService.destroy(Matchers.eq(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(204))

    def givenUserLoggedIn() =
      given(underTest.sessionService.fetch(Matchers.eq(session.sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(session)))

    val sessionParams = Seq("csrfToken" ->  fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val requestWithCsrfToken = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
  }



  "logging out" should {

    "display the logout confirmation page when the user calls logout" in new Setup {
      val request = requestWithCsrfToken
      val result = await(underTest.logout()(request))

      status(result) shouldBe 200

      bodyOf(result) should include("You are now signed out")
    }

    "display the logout confirmation page when a user that is not signed in attempts to log out" in new Setup {
      val request = FakeRequest()
      val result = await(underTest.logout()(request))

      status(result) shouldBe 200
      bodyOf(result) should include("You are now signed out")
    }

    "destroy session on logout" in new Setup {
      implicit val request = requestWithCsrfToken.withSession("access_uri" -> "https://www.example.com")
      val result = await(underTest.logout()(request))

      verify(underTest.sessionService, atLeastOnce()).destroy(Matchers.eq(session.sessionId))(Matchers.any[HeaderCarrier])
      result.session.data shouldBe Map.empty
    }
  }

  "logoutSurvey" should {

    "redirect to the logauthConfigin page if the user is not logged in" in new Setup {
      val request = requestWithCsrfToken
      val result = await(underTest.logoutSurvey()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "display the survey page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      val request = requestWithCsrfToken
      val result = await(addToken(underTest.logoutSurvey())(request))

      status(result) shouldBe 200

      bodyOf(result) should include("Are you sure you want to sign out?")
    }
  }

  "logoutSurveyAction" should {
    "redirect to the login page if the user is not logged in" in new Setup {
      val request = requestWithCsrfToken.withFormUrlEncodedBody(
        ("blah" -> "thing")
      )
      val result = await(underTest.logoutSurveyAction()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "submit the survey and redirect to the logout confirmation page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      val form = SignOutSurveyForm(Some(2), "no suggestions", s"${user.firstName} ${user.lastName}", user.email, true)
      val request = requestWithCsrfToken.withFormUrlEncodedBody(
        ("rating" -> form.rating.get.toString), ("email" -> form.email), ("name" -> form.name),
        ("isJavascript" -> form.isJavascript.toString), ("improvementSuggestions" -> form.improvementSuggestions)
      )

      val result = await(underTest.logoutSurveyAction()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/logout")

      verify(underTest.deskproService).submitSurvey(mockEq(form))(any[Request[AnyRef]], any[HeaderCarrier])
    }
  }
}
