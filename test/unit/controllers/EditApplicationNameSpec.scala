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

import config.ErrorHandler
import controllers._
import domain._
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.verify
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future.successful

class EditApplicationNameSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer: Developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser: DeveloperSession = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession: Session = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val application: Application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens: ApplicationTokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new AddApplication(
      mock[ApplicationService],
      mock[SessionService],
      mock[AuditService],
      mock[ErrorHandler],
      messagesApi
    )

    val hc: HeaderCarrier = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    given(underTest.sessionService.fetch(mockEq(partLoggedInSessionId))(any[HeaderCarrier]))
      .willReturn(Some(partLoggedInSession))

    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .willReturn(successful(ApplicationUpdateSuccessful))

    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier]))
      .willReturn(successful(application))

    given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
      .willReturn(successful(Valid))

    private val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)

  }

  "NameApplicationPage in subordinate" should {

    "return the Edit Applications Name Page with user logged in" in new Setup {

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application)))

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

        given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Invalid(invalidName = true, duplicateName = false))

        private val request = utils.CSRFTokenHelper.CSRFRequestHeader(loggedInRequest)
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", invalidApplicationName),
            ("environment", "SANDBOX"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.SANDBOX)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

        verify(underTest.applicationService, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(underTest.applicationService)
          .isApplicationNameValid(mockEq(invalidApplicationName), mockEq(Environment.SANDBOX), mockEq(None))(any[HeaderCarrier])
      }
    }
  }

  "NameApplicationPage in principal" should {

    "return the Edit Applications Name Page with user logged in" in new Setup {

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application)))

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

        given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Invalid(invalidName = true, duplicateName = false))

        private val request = utils.CSRFTokenHelper.CSRFRequestHeader(loggedInRequest)
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", invalidApplicationName),
            ("environment", "PRODUCTION"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.PRODUCTION)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

        verify(underTest.applicationService, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(underTest.applicationService)
          .isApplicationNameValid(mockEq(invalidApplicationName), mockEq(Environment.PRODUCTION), any())(any[HeaderCarrier])
      }

      "and it is duplicate it shows an error page and lets you re-submit the name" in new Setup {
        private val applicationName = "duplicate name"

        given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Invalid(invalidName = false, duplicateName = true))

        private val request = utils.CSRFTokenHelper.CSRFRequestHeader(loggedInRequest)
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", applicationName),
            ("environment", "PRODUCTION"),
            ("description", ""))

        private val result = await(underTest.editApplicationNameAction(Environment.PRODUCTION)(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("That application name already exists. Enter a unique name for your application")

        verify(underTest.applicationService, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(underTest.applicationService)
          .isApplicationNameValid(mockEq(applicationName), mockEq(Environment.PRODUCTION), any())(any[HeaderCarrier])
      }

    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
