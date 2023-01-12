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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.AddApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.UpliftJourneyConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{Off, On, OnDemand}
import play.api.mvc.Headers
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.apiplatform.modules.uplift.views.html.BeforeYouStartView
import uk.gov.hmrc.apiplatform.modules.uplift.services.GetProductionCredentialsFlowService
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.apiplatform.modules.uplift.controllers.UpliftJourneySwitch
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import scala.concurrent.Future

class AddApplicationStartSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with DeveloperBuilder
    with LocalUserIdTracker
    with ApplicationBuilder {

  val collaborator: Collaborator = loggedInDeveloper.email.asAdministratorCollaborator

  val sandboxAppSummaries = (1 to 5).map(_ => buildApplication(loggedInDeveloper.email)).map(ApplicationSummary.from(_, loggedInDeveloper.developer.userId)).toList

  trait Setup extends UpliftLogicMock with ApplicationServiceMock with ApmConnectorMockModule with ApplicationActionServiceMock with SessionServiceMock
      with EmailPreferencesServiceMock {
    val accessTokenSwitchView                     = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView                        = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView        = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationStartPrincipalView          = app.injector.instanceOf[AddApplicationStartPrincipalView]
    val addApplicationSubordinateSuccessView      = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView                    = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView             = app.injector.instanceOf[ChooseApplicationToUpliftView]

    val beforeYouStartView: BeforeYouStartView = app.injector.instanceOf[BeforeYouStartView]
    val upliftJourneyConfigMock                = mock[UpliftJourneyConfig]
    val flowServiceMock                        = mock[GetProductionCredentialsFlowService]

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
      new UpliftJourneySwitch(upliftJourneyConfigMock),
      beforeYouStartView,
      flowServiceMock
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

    "return the uplift journey 'before you start' page when the UpliftJourneyConfig returns On " +
      "and we have only 1 application" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None)))

        val summaries = sandboxAppSummaries.take(1)
        val myAppId   = summaries.head.id
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        when(upliftJourneyConfigMock.status).thenReturn(On)

        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
      }

    "return the uplift journey 'Which application do you want production credentials for?' page when the UpliftJourneyConfig returns On" +
      "and we have more than 1 application" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None)))

        val summaries = sandboxAppSummaries.take(2)
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        when(upliftJourneyConfigMock.status).thenReturn(On)

        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Which application do you want production credentials for")
      }

    "return the add applications page when the UpliftJourneyConfig " +
      "returns OnDemand and request header does not contain the uplift journey flag" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

        when(upliftJourneyConfigMock.status).thenReturn(OnDemand)

        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Add an application to QA")
      }

    "return the add applications page when the UpliftJourneyConfig " +
      "returns OnDemand and request header contains the uplift journey flag set to false" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

        when(upliftJourneyConfigMock.status).thenReturn(OnDemand)

        val loggedInRequestWithFlag = loggedInRequest.withHeaders(Headers("useNewUpliftJourney" -> "false"))

        private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

        status(result) shouldBe OK
        contentAsString(result) should include("Add an application to QA")
      }

    "return the uplift journey 'before you start' page when the UpliftJourneyConfig " +
      "returns OnDemand and request header contains the uplift journey flag set to true" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None)))

        val summaries = sandboxAppSummaries.take(1)
        val myAppId   = summaries.head.id
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        when(upliftJourneyConfigMock.status).thenReturn(OnDemand)

        val loggedInRequestWithFlag = loggedInRequest.withHeaders(Headers("useNewUpliftJourney" -> "true"))

        private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
      }

    "return the add applications page when the UpliftJourneyConfig " +
      "returns Off and request header contains the uplift journey flag set to true" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

        when(upliftJourneyConfigMock.status).thenReturn(Off)

        val loggedInRequestWithFlag = loggedInRequest.withHeaders(Headers("useNewUpliftJourney" -> "true"))

        private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

        status(result) shouldBe OK
        contentAsString(result) should include("Add an application to QA")
      }

    "return the uplift journey 'before you start' page when the UpliftJourneyConfig " +
      "returns On and request header contains the uplift journey flag set to false" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None)))

        val summaries = sandboxAppSummaries.take(1)
        val myAppId   = summaries.head.id
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        when(upliftJourneyConfigMock.status).thenReturn(On)

        val loggedInRequestWithFlag = loggedInRequest.withHeaders(Headers("useNewUpliftJourney" -> "false"))

        private val result = underTest.addApplicationPrincipal()(loggedInRequestWithFlag)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
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
