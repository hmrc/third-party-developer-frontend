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
import scala.concurrent.Future

import views.html.{LogoutConfirmationView, SignoutSurveyView}

import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF._
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketId
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, DeskproService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class UserLogoutAccountSpec extends BaseControllerSpec with WithCSRFAddToken with UserBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {
    val developer = buildTrackedUser()
    val sessionId = UserSessionId.random
    val session   = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)

    val developerSession: UserSession = session

    val signoutSurveyView      = app.injector.instanceOf[SignoutSurveyView]
    val logoutConfirmationView = app.injector.instanceOf[LogoutConfirmationView]

    val underTest = new UserLogoutAccount(
      mock[DeskproService],
      sessionServiceMock,
      mock[ApplicationService],
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      signoutSurveyView,
      logoutConfirmationView
    )

    when(underTest.sessionService.destroy(eqTo(session.sessionId))(*))
      .thenReturn(Future.successful(NO_CONTENT))

    def givenUserLoggedIn() = {
      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(
        underTest.sessionService
          .fetch(eqTo(session.sessionId))(*)
      ).thenReturn(Future.successful(Some(session)))
    }

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequestWithCsrfToken = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val notLoggedInRequestWithCsrfToken = FakeRequest()
      .withSession(sessionParams: _*)
  }

  "logging out" should {

    "display the logout confirmation page when the user calls logout" in new Setup {
      val request = loggedInRequestWithCsrfToken
      val result  = underTest.logout()(request)

      status(result) shouldBe 200

      contentAsString(result) should include("You are now signed out")
    }

    "display the logout confirmation page when a user that is not signed in attempts to log out" in new Setup {
      val request = FakeRequest()
      val result  = underTest.logout()(request)

      status(result) shouldBe 200
      contentAsString(result) should include("You are now signed out")
    }

    "destroy session on logout" in new Setup {
      implicit val request: FakeRequest[AnyContent] = loggedInRequestWithCsrfToken.withSession("access_uri" -> "https://www.example.com")
      val result                                    = await(underTest.logout()(request))

      verify(underTest.sessionService, atLeastOnce).destroy(eqTo(session.sessionId))(*)
      result.session.data shouldBe Map.empty
    }
  }

  "logoutSurvey" should {

    "redirect to the log authConfig in page if the user is not logged in" in new Setup {
      val request = notLoggedInRequestWithCsrfToken

      val result = underTest.logoutSurvey()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "display the survey page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      val request = loggedInRequestWithCsrfToken
      val result  = addToken(underTest.logoutSurvey())(request)

      status(result) shouldBe 200

      contentAsString(result) should include("Are you sure you want to sign out?")
    }
  }

  "logoutSurveyAction" should {
    "redirect to the login page if the user is not logged in" in new Setup {
      val request = notLoggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "blah" -> "thing"
      )
      val result  = underTest.logoutSurveyAction()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "submit the survey and redirect to the logout confirmation page if the user is logged in" in new Setup {
      givenUserLoggedIn()

      when(underTest.deskproService.submitSurvey(*)(any[Request[AnyRef]], *))
        .thenReturn(Future.successful(TicketId(123)))

      when(underTest.applicationService.userLogoutSurveyCompleted(*[LaxEmailAddress], *, *, *)(*))
        .thenReturn(Future.successful(Success))

      val form    =
        SignOutSurveyForm(
          Some(2),
          "no suggestions",
          s"${developerSession.developer.firstName} ${developerSession.developer.lastName}",
          developerSession.developer.email.text,
          isJavascript = true
        )
      val request = loggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "rating"                 -> form.rating.get.toString,
        "email"                  -> form.email,
        "name"                   -> form.name,
        "isJavascript"           -> form.isJavascript.toString,
        "improvementSuggestions" -> form.improvementSuggestions
      )

      val result = underTest.logoutSurveyAction()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/logout")

      verify(underTest.deskproService).submitSurvey(eqTo(form))(any[Request[AnyRef]], *)
      verify(underTest.applicationService).userLogoutSurveyCompleted(eqTo(developerSession.developer.email), eqTo("John Doe"), eqTo("2"), eqTo("no suggestions"))(
        *
      )
    }

    "submit the survey and redirect to logout confirmation page if the user is logged in and has not given a satisfaction rating" in new Setup {
      givenUserLoggedIn()

      when(underTest.deskproService.submitSurvey(*)(any[Request[AnyRef]], *))
        .thenReturn(Future.successful(TicketId(123)))

      when(underTest.applicationService.userLogoutSurveyCompleted(*[LaxEmailAddress], *, *, *)(*))
        .thenReturn(Future.successful(Success))

      val form    = SignOutSurveyForm(
        None,
        "no suggestions",
        s"${developerSession.developer.firstName} ${developerSession.developer.lastName}",
        developerSession.developer.email.text,
        isJavascript = true
      )
      val request = loggedInRequestWithCsrfToken.withFormUrlEncodedBody(
        "rating"                 -> form.rating.fold("")(_.toString),
        "email"                  -> form.email,
        "name"                   -> form.name,
        "isJavascript"           -> form.isJavascript.toString,
        "improvementSuggestions" -> form.improvementSuggestions
      )

      val result = underTest.logoutSurveyAction()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/logout")

      verify(underTest.deskproService).submitSurvey(eqTo(form))(any[Request[AnyRef]], *)
      verify(underTest.applicationService).userLogoutSurveyCompleted(eqTo(developerSession.developer.email), eqTo("John Doe"), eqTo(""), eqTo("no suggestions"))(*)
    }
  }
}
