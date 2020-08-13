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

import domain.models.connectors.TicketId
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import mocks.service.SessionServiceMock
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF._
import service.{ApplicationService, DeskproService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{LogoutConfirmationView, SignoutSurveyView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserLogoutAccountSpec extends BaseControllerSpec with WithCSRFAddToken {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = UUID.randomUUID().toString
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val developerSession: DeveloperSession = DeveloperSession(session)

  trait Setup extends SessionServiceMock {
    val signoutSurveyView = app.injector.instanceOf[SignoutSurveyView]
    val logoutConfirmationView = app.injector.instanceOf[LogoutConfirmationView]

    val underTest = new UserLogoutAccount(
      mock[DeskproService],
      sessionServiceMock,
      mock[ApplicationService],
      mock[config.ErrorHandler],
      mcc,
      cookieSigner,
      signoutSurveyView,
      logoutConfirmationView
    )

    given(underTest.sessionService.destroy(eqTo(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(NO_CONTENT))

    def givenUserLoggedIn() =
      given(underTest.sessionService
        .fetch(eqTo(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(Some(session)))

    val sessionParams = Seq("csrfToken" ->  app.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequestWithCsrfToken = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val notLoggedInRequestWithCsrfToken = FakeRequest()
      .withSession(sessionParams: _*)
  }

  "logging out" should {

    "display the logout confirmation page when the user calls logout" in new Setup {
      val request = loggedInRequestWithCsrfToken
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
      implicit val request = loggedInRequestWithCsrfToken.withSession("access_uri" -> "https://www.example.com")
      val result = await(underTest.logout()(request))

      verify(underTest.sessionService, atLeastOnce()).destroy(eqTo(session.sessionId))(any[HeaderCarrier])
      result.session.data shouldBe Map.empty
    }
  }

  "logoutSurvey" should {

    "redirect to the log authConfig in page if the user is not logged in" in new Setup {
      val request = notLoggedInRequestWithCsrfToken

      val result = await(underTest.logoutSurvey()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "display the survey page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      val request = loggedInRequestWithCsrfToken
      val result = await(addToken(underTest.logoutSurvey())(request))

      status(result) shouldBe 200

      bodyOf(result) should include("Are you sure you want to sign out?")
    }
  }

  "logoutSurveyAction" should {
    "redirect to the login page if the user is not logged in" in new Setup {
      val request = notLoggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "blah" -> "thing"
      )
      val result = await(underTest.logoutSurveyAction()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "submit the survey and redirect to the logout confirmation page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      given(underTest.deskproService.submitSurvey(any())(any[Request[AnyRef]], any[HeaderCarrier]))
        .willReturn(Future.successful(TicketId(123)))

      given(underTest.applicationService.userLogoutSurveyCompleted(any(), any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Success))


      val form = SignOutSurveyForm(Some(2), "no suggestions", s"${developerSession.developer.firstName} ${developerSession.developer.lastName}", developerSession.email, isJavascript = true)
      val request = loggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "rating" -> form.rating.get.toString, "email" -> form.email, "name" -> form.name,
        "isJavascript" -> form.isJavascript.toString, "improvementSuggestions" -> form.improvementSuggestions
      )

      val result = await(underTest.logoutSurveyAction()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/logout")

      verify(underTest.deskproService).submitSurvey(eqTo(form))(any[Request[AnyRef]], any[HeaderCarrier])
      verify(underTest.applicationService).userLogoutSurveyCompleted(eqTo(developerSession.developer.email), eqTo("John Doe"), eqTo("2"), eqTo("no suggestions"))(any[HeaderCarrier])
    }

    "submit the survey and redirect to logout confirmation page if the user is logged in and has not given a satisfaction rating" in new Setup {
      givenUserLoggedIn()

      given(underTest.deskproService.submitSurvey(any())(any[Request[AnyRef]], any[HeaderCarrier]))
        .willReturn(Future.successful(TicketId(123)))

      given(underTest.applicationService.userLogoutSurveyCompleted(any(), any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Success))


      val form = SignOutSurveyForm(None, "no suggestions", s"${developerSession.developer.firstName} ${developerSession.developer.lastName}", developerSession.developer.email, isJavascript = true)
      val request = loggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "rating" -> form.rating.getOrElse("").toString, "email" -> form.email, "name" -> form.name,
        "isJavascript" -> form.isJavascript.toString, "improvementSuggestions" -> form.improvementSuggestions
      )

      val result = await(underTest.logoutSurveyAction()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/logout")

      verify(underTest.deskproService).submitSurvey(eqTo(form))(any[Request[AnyRef]], any[HeaderCarrier])
      verify(underTest.applicationService).userLogoutSurveyCompleted(eqTo(developerSession.developer.email), eqTo("John Doe"), eqTo(""), eqTo("no suggestions"))(any[HeaderCarrier])
    }
  }
}
