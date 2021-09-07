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
import domain.models.applications._
import mocks.service._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._
import mocks.connector.ApmConnectorMockModule

import scala.concurrent.ExecutionContext.Implicits.global
import utils.LocalUserIdTracker
import controllers.addapplication.AddApplication
import builder._
import config.UpliftJourneyConfigProvider
import config.On
import config.OnDemand
import play.api.mvc.Headers

class AddApplicationStartSpec 
    extends BaseControllerSpec 
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar 
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker {

  val collaborator: Collaborator = loggedInDeveloper.email.asAdministratorCollaborator

  trait Setup extends UpliftLogicMock with ApplicationServiceMock with ApmConnectorMockModule with ApplicationActionServiceMock with SessionServiceMock with EmailPreferencesServiceMock {
    val accessTokenSwitchView = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationStartPrincipalView = app.injector.instanceOf[AddApplicationStartPrincipalView]
    val addApplicationSubordinateSuccessView = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView = app.injector.instanceOf[ChooseApplicationToUpliftView]

    val upliftJourneyTermsOfUseView: UpliftJourneyTermsOfUseView = app.injector.instanceOf[UpliftJourneyTermsOfUseView]
    val upliftJourneyConfigProviderMock = mock[UpliftJourneyConfigProvider]

    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val underTest = new AddApplication(
      mock[ErrorHandler],
      applicationServiceMock,
      applicationActionServiceMock,
      emailPreferencesServiceMock,
      ApmConnectorMock.aMock,
      sessionServiceMock,
      mock[AuditService],
      upliftLogicMock,
      mcc,
      cookieSigner,
      accessTokenSwitchView,
      usingPrivilegedApplicationCredentialsView,
      tenDaysWarningView,
      addApplicationStartSubordinateView,
      addApplicationStartPrincipalView,
      addApplicationSubordinateSuccessView,
      addApplicationNameView,
      chooseApplicationToUpliftView,
      upliftJourneyConfigProviderMock,
      upliftJourneyTermsOfUseView
    )

    val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

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
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should not include "Sign in"
    }

    "return the add applications page with the user logged in when the environmennt is QA/Dev" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      private val result = underTest.addApplicationSubordinate()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Add an application to development")
      contentAsString(result) should include(loggedInDeveloper.displayedName)
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
      contentAsString(result) should include(loggedInDeveloper.displayedName)
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
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include("Now that you've tested your software you can request to add your application to QA.")
      contentAsString(result) should not include "Sign in"
    }

    "return the uplift journey terms of use page when the UpliftJourneyConfigProvider returns On" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      when(upliftJourneyConfigProviderMock.status).thenReturn(On)
      
      private val result = underTest.addApplicationPrincipal()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
    }

    "return the add applications page when the UpliftJourneyConfigProvider " +
      "returns OnDemand and request header does not contain the uplift journey flag" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      when(upliftJourneyConfigProviderMock.status).thenReturn(OnDemand)

      private val result = underTest.addApplicationPrincipal()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Add an application to QA")
    }

    "return the add applications page when the UpliftJourneyConfigProvider " +
      "returns OnDemand and request header contains the uplift journey flag set to false" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      when(upliftJourneyConfigProviderMock.status).thenReturn(OnDemand)

      val loggedInRequestWithFlag = loggedInRequest.copy(headers = Headers("useNewUpliftJourney" -> "false"))

      private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

      status(result) shouldBe OK
      contentAsString(result) should include("Add an application to QA")
    }

    "return the uplift journey terms of use page when the UpliftJourneyConfigProvider " +
        "returns OnDemand and request header contains the uplift journey flag set to true" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      when(upliftJourneyConfigProviderMock.status).thenReturn(OnDemand)

      val loggedInRequestWithFlag = loggedInRequest.copy(headers = Headers("useNewUpliftJourney" -> "true"))

      private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
    }

    "return the uplift journey terms of use view when the UpliftJourneyConfigProvider " +
          "returns Off and request header contains the uplift journey flag set to true" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      when(upliftJourneyConfigProviderMock.status).thenReturn(Off)

      val loggedInRequestWithFlag = loggedInRequest.copy(headers = Headers("useNewUpliftJourney" -> "true"))

      private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
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
