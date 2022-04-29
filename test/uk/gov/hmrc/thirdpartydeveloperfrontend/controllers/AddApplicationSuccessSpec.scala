/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.CombinedApiTestDataHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.AddApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.apiplatform.modules.uplift.views.html.BeforeYouStartView
import uk.gov.hmrc.apiplatform.modules.uplift.services.GetProductionCredentialsFlowService
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.apiplatform.modules.uplift.controllers.UpliftJourneySwitch

class AddApplicationSuccessSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with DeveloperBuilder
    with LocalUserIdTracker {

  val principalApp = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    Some(DateTimeUtils.now),
    None,
    grantLength,
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(loggedInDeveloper.email.asAdministratorCollaborator),
    state = ApplicationState.production(loggedInDeveloper.email, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  val subordinateApp = Application(
    appId,
    clientId,
    "App name 2",
    DateTimeUtils.now,
    Some(DateTimeUtils.now),
    None,
    grantLength,
    Environment.SANDBOX,
    Some("Description 2"),
    Set(loggedInDeveloper.email.asAdministratorCollaborator),
    state = ApplicationState.production(loggedInDeveloper.email, ""),
    access = Standard(redirectUris = List("https://red3", "https://red4"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  trait Setup extends UpliftLogicMock with ApplicationServiceMock with ApmConnectorMockModule with ApplicationActionServiceMock with SessionServiceMock with EmailPreferencesServiceMock with CombinedApiTestDataHelper {
    val accessTokenSwitchView = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationStartPrincipalView = app.injector.instanceOf[AddApplicationStartPrincipalView]
    val addApplicationSubordinateSuccessView = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView = app.injector.instanceOf[ChooseApplicationToUpliftView]
    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val beforeYouStartView: BeforeYouStartView = app.injector.instanceOf[BeforeYouStartView]
    val sr20UpliftJourneySwitchMock = mock[UpliftJourneySwitch]

    val flowServiceMock = mock[GetProductionCredentialsFlowService]

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
      sr20UpliftJourneySwitchMock,
      beforeYouStartView,
      flowServiceMock
    )

    implicit val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    fetchSessionByIdReturns(partLoggedInSessionId, partLoggedInSession)

    private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  "Add applications subordinate success page" should {

    "send the user on a email preferences journey when logged in and environment is Sandbox" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

      // Have the lookup for subscribed apis not already in email preferences return an List containing some api definitions
      // so that we follow the new email preferences route through this journey.
      fetchAPIDetailsReturns(List(combinedApi("Test Api Definition")))
      givenApplicationAction(subordinateApp, loggedInDeveloper)

      private val result = underTest.addApplicationSuccess(appId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
    }

    "return the page with the user is logged in and the environment is Sandbox" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

      // Have the lookup for subscribed apis not already in email preferences return an empty List so that we follow
      // the original route through this journey.
      fetchAPIDetailsReturns(List.empty)
      givenApplicationAction(subordinateApp, loggedInDeveloper)

      private val result = underTest.addApplicationSuccess(appId)(loggedInRequest)

      status(result) shouldBe OK
      titleOf(result) shouldBe "Application added to the sandbox - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("You can now use its credentials to test with sandbox APIs.")
      contentAsString(result) should include("Read the guidance on")
      contentAsString(result) should include("to find out which endpoints to use, creating a test user and types of test data.")
      contentAsString(result) should not include "Sign in"
    }

    "return the page with the user is logged in and the environment is Development" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      
      // Have the lookup for subscribed apis not already in email preferences return an empty List so that we follow
      // the original route through this journey.
      fetchAPIDetailsReturns(List.empty)
      givenApplicationAction(subordinateApp, loggedInDeveloper)

      private val result = underTest.addApplicationSuccess(appId)(loggedInRequest)

      status(result) shouldBe OK
      titleOf(result) shouldBe "Application added to development - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("You can now use its credentials to test with development APIs.")
      contentAsString(result) should include("Read the guidance on")
      contentAsString(result) should include("to find out which endpoints to use, creating a test user and types of test data.")
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

  private def titleOf(result: Future[Result]) = {
    val titleRegEx = """<title[^>]*>(.*)</title>""".r
    val title = titleRegEx.findFirstMatchIn(contentAsString(result)).map(_.group(1))
    title.isDefined shouldBe true
    title.get
  }
}
