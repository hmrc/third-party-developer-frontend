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

import config.ErrorHandler
import domain.models.applications._
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._
import views.html.include.DevMain

import scala.concurrent.ExecutionContext.Implicits.global

class AddApplicationStartSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val collaborator: Collaborator = Collaborator(loggedInUser.email, Role.ADMINISTRATOR)

  val application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(collaborator),
    state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]
    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]
    val accessTokenSwitchView = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val devMain = app.injector.asInstanceOf[DevMain]
    val environmentNameService = new EnvironmentNameService(appConfig)
    val addApplicationStartSubordinateView2 =  new AddApplicationStartSubordinateView(devMain, environmentNameService: EnvironmentNameService)
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

    val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)

    fetchSessionByIdReturns(partLoggedInSessionId, partLoggedInSession)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withCSRFToken

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)
  }

    "Add subordinate applications start page" should {
      "return the add applications page with the user logged in when the environment is Production" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

        private val result = underTest.addApplicationSubordinate()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Add an application to the sandbox")
      contentAsString(result) should include(loggedInUser.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include("get its sandbox credentials")
      contentAsString(result) should include("use its credentials for integration testing")
      contentAsString(result) should include("In production, your application will need to comply with the expectations set out in our")
      contentAsString(result) should include("Once you add your application and subscribe it to the sandbox APIs you want to integrate with you can:")
      contentAsString(result) should not include "Sign in"
    }

      "return the add applications page with the user logged in when the environmennt is QA/Dev" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        private val result = underTest.addApplicationSubordinate()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Add an application to development")
        contentAsString(result) should include(loggedInUser.displayedName)
        contentAsString(result) should include("Sign out")
        contentAsString(result) should include("get its development credentials")
        contentAsString(result) should include("use its credentials for integration testing")
        contentAsString(result) should include("In production, your application will need to comply with the expectations set out in our")
        contentAsString(result) should include("Once you add your application and subscribe it to the development APIs you want to integrate with you can:")
        contentAsString(result) should not include "Sign in"
      }


      "return to the login page when the user is not logged in" in new Setup {
        val request = FakeRequest()

      private val result = underTest.addApplicationSubordinate()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when partly logged" in new Setup {
      private val result = underTest.addApplicationSubordinate()(partLoggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

    "Add principal applications start page" should {
      "return the add applications page with the user logged in when the environment is Production" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Get production credentials")
        contentAsString(result) should include(loggedInUser.displayedName)
        contentAsString(result) should include("Sign out")
        contentAsString(result) should include("Now that you've tested your software you can request production credentials to use live data.")
        contentAsString(result) should not include "Sign in"
      }

      "return the add applications page with the user logged in when the environment is QA" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Add an application to QA")
        contentAsString(result) should include(loggedInUser.displayedName)
        contentAsString(result) should include("Sign out")
        contentAsString(result) should include("Now that you've tested your software you can request to add your application to QA.")
        contentAsString(result) should not include "Sign in"
      }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = underTest.addApplicationPrincipal()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when partly logged" in new Setup {
      private val result = underTest.addApplicationPrincipal()(partLoggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}
