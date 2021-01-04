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
import connectors.ThirdPartyDeveloperConnector
import domain.models.connectors.ChangePassword
import domain.models.developers.{LoggedInState, Session, UpdateProfileRequest}
import domain.InvalidCredentials
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import play.api.http.Status.OK
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction.PasswordChangeFailedDueToInvalidCredentials
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed
import controllers.profile.Profile

class ProfileSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends ApplicationServiceMock with SessionServiceMock with DeveloperBuilder {
    val changeProfileView = app.injector.instanceOf[ChangeProfileView]
    val profileView = app.injector.instanceOf[ProfileView]
    val profileUpdatedView = app.injector.instanceOf[ProfileUpdatedView]
    val changeProfilePasswordView = app.injector.instanceOf[ChangeProfilePasswordView]
    val passwordUpdatedView = app.injector.instanceOf[PasswordUpdatedView]
    val profileDeleteConfirmationView = app.injector.instanceOf[ProfileDeleteConfirmationView]
    val profileDeleteSubmittedView = app.injector.instanceOf[ProfileDeleteSubmittedView]

    val underTest = new Profile(
      applicationServiceMock,
      mock[AuditService],
      sessionServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      changeProfileView,
      profileView,
      profileUpdatedView,
      changeProfilePasswordView,
      passwordUpdatedView,
      profileDeleteConfirmationView,
      profileDeleteSubmittedView
    )

    val loggedInUser = buildDeveloper()

    val sessionId = "sessionId"
  }

  "updateProfile" should {
    "update profile with normalized firstname and lastname" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withFormUrlEncodedBody(
          ("firstname", "  first  "), // with whitespaces before and after
          ("lastname", "  last  ") // with whitespaces before and after
        )

      val requestCaptor: ArgumentCaptor[UpdateProfileRequest] = ArgumentCaptor.forClass(classOf[UpdateProfileRequest])

      fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN))
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      when(underTest.connector.updateProfile(eqTo(loggedInUser.email), requestCaptor.capture())(any[HeaderCarrier]))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updateProfile())(request)

      status(result) shouldBe 200
      requestCaptor.getValue.firstName shouldBe "first"
      requestCaptor.getValue.lastName shouldBe "last"
    }

    "fail and send an audit event while changing the password if old password is incorrect" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN))))
      when(underTest.connector.changePassword(eqTo(ChangePassword(loggedInUser.email, "oldPassword", "StrongNewPwd!2")))(any[HeaderCarrier]))
        .thenReturn(failed(new InvalidCredentials()))

      val result = addToken(underTest.updatePassword())(request)

      status(result) shouldBe 401

      await(result) // Before we verify !
      verify(underTest.auditService).audit(eqTo(PasswordChangeFailedDueToInvalidCredentials(loggedInUser.email)), eqTo(Map.empty))(any[HeaderCarrier])
    }

    "Password updated should have correct page title" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).thenReturn(Future.successful(Some(Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN))))
      when(underTest.connector.changePassword(eqTo(ChangePassword(loggedInUser.email, "oldPassword", "StrongNewPwd!2")))(any[HeaderCarrier]))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updatePassword())(request)

      status(result) shouldBe OK
      val dom = Jsoup.parse(contentAsString(result))
      dom.getElementsByClass("heading-xlarge").get(0).text shouldEqual "Password changed"

    }
  }
}
