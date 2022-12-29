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

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ChangePassword
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session, UpdateProfileRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.InvalidCredentials
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import play.api.http.Status.OK
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.PasswordChangeFailedDueToInvalidCredentials
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile.Profile
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker

class ProfileSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
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

    val loggedInDeveloper: Developer = buildDeveloper()
    val sessionId = "sessionId"

    def createRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withCSRFToken
  }

  "updateProfile" should {
    "update profile with normalized firstname and lastname" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
          ("firstname", "  first  "), // with whitespaces before and after
          ("lastname", "  last  ") // with whitespaces before and after
        )

      val requestCaptor: ArgumentCaptor[UpdateProfileRequest] = ArgumentCaptor.forClass(classOf[UpdateProfileRequest])

      fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, LoggedInState.LOGGED_IN))
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      when(underTest.connector.updateProfile(eqTo(loggedInDeveloper.userId), requestCaptor.capture())(*))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updateProfile())(request)

      status(result) shouldBe 200
      requestCaptor.getValue.firstName shouldBe "first"
      requestCaptor.getValue.lastName shouldBe "last"
    }

    "fail and send an audit event while changing the password if old password is incorrect" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(underTest.sessionService.fetch(eqTo(sessionId))(*))
        .thenReturn(Future.successful(Some(Session(sessionId, loggedInDeveloper, LoggedInState.LOGGED_IN))))
      when(underTest.connector.changePassword(eqTo(ChangePassword(loggedInDeveloper.email, "oldPassword", "StrongNewPwd!2")))(*))
        .thenReturn(failed(new InvalidCredentials()))

      val result = addToken(underTest.updatePassword())(request)

      status(result) shouldBe 401

      await(result) // Before we verify !
      verify(underTest.auditService).audit(eqTo(PasswordChangeFailedDueToInvalidCredentials(loggedInDeveloper.email)), eqTo(Map.empty))(*)
    }

    "Password updated should have correct page title" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(Future.successful(Some(Session(sessionId, loggedInDeveloper, LoggedInState.LOGGED_IN))))
      when(underTest.connector.changePassword(eqTo(ChangePassword(loggedInDeveloper.email, "oldPassword", "StrongNewPwd!2")))(*))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updatePassword())(request)

      status(result) shouldBe OK
      val dom = Jsoup.parse(contentAsString(result))
      dom.getElementsByClass("govuk-panel__title").get(0).text shouldEqual "Password changed"
    }
  }
}
