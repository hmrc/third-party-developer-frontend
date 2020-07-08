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

import java.util.UUID.randomUUID

import config.ErrorHandler
import domain._
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.verify
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithLoggedInSession._
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global

class EditApplicationNameSpec extends BaseControllerSpec with SubscriptionTestHelperSugar {

  val appId = "1234"
  val clientId = "clientId123"

  val developer: Developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser: DeveloperSession = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession: Session = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val application: Application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens: ApplicationToken = ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]
    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]
    val accessTokenSwitchView = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationStartPrincipalView = app.injector.instanceOf[AddApplicationStartPrincipalView]
    val addApplicationSubordinateSuccessView = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]

    val underTest = new AddApplication(
      applicationServiceMock,
      sessionServiceMock,
      mock[AuditService],
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      addApplicationSubordinateEmptyNestView,
      manageApplicationsView,
      accessTokenSwitchView,
      usingPrivilegedApplicationCredentialsView,
      tenDaysWarningView,
      addApplicationStartSubordinateView,
      addApplicationStartPrincipalView,
      addApplicationSubordinateSuccessView,
      addApplicationNameView
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)

    fetchSessionByIdReturns(partLoggedInSessionId, partLoggedInSession)

    givenApplicationUpdateSucceeds()

    fetchByApplicationIdReturns(application.id, application)

    givenApplicationNameIsValid()

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)

  }

  "NameApplicationPage in subordinate" should {

    "return the Edit Applications Name Page with user logged in" in new Setup {

      fetchByTeamMemberEmailReturns(loggedInUser.email,List(application))

      private val result = await(underTest.addApplicationName(Environment.SANDBOX)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("What&#x27;s the name of your application?")
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Continue")
      bodyOf(result) should include("Application name")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      private val result = await(underTest.addApplicationName(Environment.SANDBOX)(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when part logged in" in new Setup {
      val result: Result = await(underTest.addApplicationName(Environment.SANDBOX)(partLoggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")

    }

    "when an invalid name is entered" when {

      "and it contains HMRC it shows an error page and lets you re-submit the name" in new Setup {
        private val invalidApplicationName = "invalidApplicationName"

        givenApplicationNameIsInvalid(Invalid(invalidName = true, duplicateName = false))

        private val request = loggedInRequest
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", invalidApplicationName),
            ("environment", "SANDBOX"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.SANDBOX)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

        verify(applicationServiceMock, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(applicationServiceMock)
          .isApplicationNameValid(eqTo(invalidApplicationName), eqTo(Environment.SANDBOX), eqTo(None))(any[HeaderCarrier])
      }
    }
  }

  "NameApplicationPage in principal" should {

    "return the Edit Applications Name Page with user logged in" in new Setup {

      fetchByTeamMemberEmailReturns(loggedInUser.email,List(application))

      private val result = await(underTest.addApplicationName( Environment.PRODUCTION)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("What&#x27;s the name of your application?")
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("We show this name to your users when they authorise your software to interact with HMRC.")
      bodyOf(result) should include("It must comply with our")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      private val result = await(underTest.addApplicationName(Environment.PRODUCTION)(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when part logged in" in new Setup {
      val result: Result = await(underTest.addApplicationName(Environment.PRODUCTION)(partLoggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")

    }

    "when an invalid name is entered" when {

      "and it contains HMRC it shows an error page and lets you re-submit the name" in new Setup {
        private val invalidApplicationName = "invalidApplicationName"

        givenApplicationNameIsInvalid(Invalid(invalidName = true, duplicateName = false))

        private val request = loggedInRequest
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", invalidApplicationName),
            ("environment", "PRODUCTION"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.PRODUCTION)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

        verify(applicationServiceMock, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(applicationServiceMock)
          .isApplicationNameValid(eqTo(invalidApplicationName), eqTo(Environment.PRODUCTION), any())(any[HeaderCarrier])
      }

      "and it is duplicate it shows an error page and lets you re-submit the name" in new Setup {
        private val applicationName = "duplicate name"

        givenApplicationNameIsInvalid(Invalid(invalidName = false, duplicateName = true))

        private val request = loggedInRequest
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", applicationName),
            ("environment", "PRODUCTION"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.PRODUCTION)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("That application name already exists. Enter a unique name for your application")

        verify(applicationServiceMock, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(applicationServiceMock)
          .isApplicationNameValid(eqTo(applicationName), eqTo(Environment.PRODUCTION), any())(any[HeaderCarrier])
      }

    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
