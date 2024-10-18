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
import scala.concurrent.Future.failed

import org.jsoup.Jsoup
import views.html._

import play.api.http.Status.OK
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile.Profile
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.InvalidCredentials
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.PasswordChangeFailedDueToInvalidCredentials
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, ProfileService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class ProfileSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends ApplicationServiceMock {
    val changeProfileView             = app.injector.instanceOf[ChangeProfileView]
    val profileView                   = app.injector.instanceOf[ProfileView]
    val profileUpdatedView            = app.injector.instanceOf[ProfileUpdatedView]
    val changeProfilePasswordView     = app.injector.instanceOf[ChangeProfilePasswordView]
    val passwordUpdatedView           = app.injector.instanceOf[PasswordUpdatedView]
    val profileDeleteConfirmationView = app.injector.instanceOf[ProfileDeleteConfirmationView]
    val profileDeleteSubmittedView    = app.injector.instanceOf[ProfileDeleteSubmittedView]

    val underTest = new Profile(
      applicationServiceMock,
      mock[ProfileService],
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

    val loggedInDeveloper: User = devUser
    val sessionId               = devSession.sessionId

    def createRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withCSRFToken
  }

  "updateProfile" should {
    "update profile with normalized firstname and lastname" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
        ("firstname", "  first  "), // with whitespaces before and after
        ("lastname", "  last  ")    // with whitespaces before and after
      )

      fetchSessionByIdReturns(sessionId, UserSession(sessionId, LoggedInState.LOGGED_IN, loggedInDeveloper))
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      when(underTest.profileService.updateProfileName(eqTo(loggedInDeveloper.userId), eqTo(loggedInDeveloper.email), eqTo("first"), eqTo("last"))(*))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updateProfile())(request)

      status(result) shouldBe OK
    }

    "show error if first name not filled in" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
        ("firstname", " "), // with whitespaces
        ("lastname", "last")
      )

      fetchSessionByIdReturns(sessionId, UserSession(sessionId, LoggedInState.LOGGED_IN, loggedInDeveloper))
      updateUserFlowSessionsReturnsSuccessfully(sessionId)

      val result = addToken(underTest.updateProfile())(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Enter your first name")
    }

    "fail and send an audit event while changing the password if old password is incorrect" in new Setup {
      val request = createRequest.withFormUrlEncodedBody(
        ("currentpassword", "oldPassword"),
        ("password", "StrongNewPwd!2"),
        ("confirmpassword", "StrongNewPwd!2")
      )

      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      when(underTest.sessionService.fetch(eqTo(sessionId))(*))
        .thenReturn(Future.successful(Some(UserSession(sessionId, LoggedInState.LOGGED_IN, loggedInDeveloper))))
      when(underTest.connector.changePassword(eqTo(PasswordChangeRequest(loggedInDeveloper.email, "oldPassword", "StrongNewPwd!2")))(*))
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
      when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(Future.successful(Some(UserSession(sessionId, LoggedInState.LOGGED_IN, loggedInDeveloper))))
      when(underTest.connector.changePassword(eqTo(PasswordChangeRequest(loggedInDeveloper.email, "oldPassword", "StrongNewPwd!2")))(*))
        .thenReturn(Future.successful(OK))

      val result = addToken(underTest.updatePassword())(request)

      status(result) shouldBe OK
      val dom = Jsoup.parse(contentAsString(result))
      dom.getElementsByClass("govuk-panel__title").get(0).text shouldEqual "Password changed"
    }
  }
}
