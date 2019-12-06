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
import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.verify
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._

class ManageApplicationsSpec
  extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  private val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

  trait AddApplicationSetup {
    val addApplicationController = new AddApplication(
      mock[ApplicationService],
      mock[ThirdPartyDeveloperConnector],
      mock[SessionService],
      mock[AuditService],
      mock[ErrorHandler],
      messagesApi,
      mock[ApplicationConfig]
    )

    given(addApplicationController.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(addApplicationController)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(addApplicationController)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  trait OldManageAppsSetup {
    val manageApps = new OldManageApplications(
        mock[ApplicationService],
        mock[ThirdPartyDeveloperConnector],
        mock[SessionService],
        mock[AuditService],
        mock[ErrorHandler],
        messagesApi,
        mock[ApplicationConfig]
      )

    val hc = HeaderCarrier()

    given(manageApps.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    given(manageApps.sessionService.fetch(mockEq(partLoggedInSessionId))(any[HeaderCarrier]))
      .willReturn(Some(partLoggedInSession))

    given(manageApps.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .willReturn(successful(ApplicationUpdateSuccessful))

    given(manageApps.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier]))
      .willReturn(successful(application))

    given(manageApps.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
      .willReturn(successful(Valid))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageApps)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageApps)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  "manageApps" should {

    "return the manage Applications page with the user logged in" in new AddApplicationSetup {

      given(addApplicationController.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application)))

      private val result = await(addApplicationController.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("App name 1")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new AddApplicationSetup {

      val request = FakeRequest()

      private val result = await(addApplicationController.manageApps()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  "addApplication" should {
    "contain a google analytics event (via the data-journey attribute) when adding an application is successful" in new OldManageAppsSetup {
      given(manageApps.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      private val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "PRODUCTION"),
          ("description", ""))

      private val result = await(manageApps.addApplicationAction()(request))
      private val dom = Jsoup.parse(bodyOf(result))
      private val element = dom.getElementsByAttribute("data-journey").first

      element.attr("data-journey") shouldEqual "application:added"
    }
    "show the application check page button when the environment specified is PRODUCTION" in new OldManageAppsSetup {
      given(manageApps.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      private val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "PRODUCTION"),
          ("description", ""))

      private val result = await(manageApps.addApplicationAction()(request))
      private val dom = Jsoup.parse(bodyOf(result))
      val element = Option(dom.getElementById("start"))

      element shouldBe defined
    }

    "show the manage subscriptions button when the environment specified is SANDBOX" in new OldManageAppsSetup {
      given(manageApps.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      private val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "SANDBOX"),
          ("description", ""))

      private val result = await(manageApps.addApplicationAction()(request))
      private val dom = Jsoup.parse(bodyOf(result))
      val element = Option(dom.getElementById("manage-api-subscriptions"))
      elementIdentifiedByAttrWithValueContainsText(
        dom, "a", "href", s"/developer/applications/${application.id}/subscriptions", "Manage API subscriptions") shouldBe true
      element shouldBe defined
    }

    "when part logged in" should {
      "redirect to the login screen" in new AddApplicationSetup {
        private val result = await(addApplicationController.manageApps()(partLoggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "when an invalid name is entered" when {

      "and it contains HMRC it shows an error page and lets you re-submit the name" in new OldManageAppsSetup {
        private val invalidApplicationName = "invalidApplicationName"

        given(manageApps.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Invalid(invalidName = true, duplicateName = false))

        private val request = utils.CSRFTokenHelper.CSRFRequestHeader(loggedInRequest)
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", invalidApplicationName),
            ("environment", "SANDBOX"),
            ("description", ""))

        private val result = await(manageApps.addApplicationAction()(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

        verify(manageApps.applicationService, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(manageApps.applicationService)
          .isApplicationNameValid(mockEq(invalidApplicationName), mockEq(Environment.SANDBOX), any())(any[HeaderCarrier])
      }

      "and it is duplicate it shows an error page and lets you re-submit the name" in new OldManageAppsSetup {
        private val applicationName = "duplicate name"

        given(manageApps.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Invalid(invalidName = false, duplicateName = true))

        private val request = utils.CSRFTokenHelper.CSRFRequestHeader(loggedInRequest)
          .withCSRFToken
          .withFormUrlEncodedBody(
            ("applicationName", applicationName),
            ("environment", "SANDBOX"),
            ("description", ""))

        private val result = await(manageApps.addApplicationAction()(request))

        status(result) shouldBe BAD_REQUEST
        bodyOf(result) should include("That application name already exists. Enter a unique name for your application")

        verify(manageApps.applicationService, Mockito.times(0))
          .createForUser(any[CreateApplicationRequest])(any[HeaderCarrier])

        verify(manageApps.applicationService)
          .isApplicationNameValid(mockEq(applicationName), mockEq(Environment.SANDBOX), any())(any[HeaderCarrier])
      }

    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
