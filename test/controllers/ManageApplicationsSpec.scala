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
import domain.models.applications._
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.joda.time.DateTimeZone
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditService
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global

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

  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

  private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

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

    val addApplicationController = new AddApplication(
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

    fetchSessionByIdReturns(sessionId, session)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(addApplicationController,implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(addApplicationController,implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  "manageApps" should {

    "return the manage Applications page with the user logged in" in new Setup {
      fetchByTeamMemberEmailReturns(loggedInUser.email, List(application))

      private val result = await(addApplicationController.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("App name 1")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = await(addApplicationController.manageApps()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  "tenDaysWarning" should {
    "return the 10 days warning interrupt page when the user is logged in" in new Setup {
      private val result = await(addApplicationController.tenDaysWarning()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("We will check your application")
      bodyOf(result) should include("This takes up to 10 working days, and we may ask you to demonstrate it.")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = await(addApplicationController.tenDaysWarning()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
