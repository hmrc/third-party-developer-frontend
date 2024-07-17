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

import views.helper.EnvironmentNameService
import views.html._

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSessionId
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import uk.gov.hmrc.apiplatform.modules.uplift.services.GetProductionCredentialsFlowService
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.apiplatform.modules.uplift.views.html.BeforeYouStartView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.AddApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class AddApplicationStartSpec
    extends BaseControllerSpec
    with SampleDeveloperSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with ApplicationBuilder {

  val collaborator: Collaborator = loggedInDeveloper.email.asAdministratorCollaborator

  val sandboxAppSummaries = (1 to 5).map(_ => buildApplication(loggedInDeveloper.email)).map(ApplicationSummary.from(_, loggedInDeveloper.developer.userId)).toList

  trait Setup extends UpliftLogicMock with ApplicationServiceMock with ApmConnectorMockModule with ApplicationActionServiceMock with SessionServiceMock
      with EmailPreferencesServiceMock {
    val accessTokenSwitchView                     = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val addApplicationStartSubordinateView        = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationSubordinateSuccessView      = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView                    = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView             = app.injector.instanceOf[ChooseApplicationToUpliftView]

    val beforeYouStartView: BeforeYouStartView = app.injector.instanceOf[BeforeYouStartView]
    val flowServiceMock                        = mock[GetProductionCredentialsFlowService]

    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

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
      addApplicationStartSubordinateView,
      addApplicationSubordinateSuccessView,
      addApplicationNameView,
      chooseApplicationToUpliftView,
      beforeYouStartView,
      flowServiceMock
    )

    val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, userSession)
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
      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

      val summaries = sandboxAppSummaries.take(1)
      val myAppId   = summaries.head.id
      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

      private val result = underTest.addApplicationPrincipal()(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
    }

    "return the add applications page with the user logged in when the environment is QA" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

      val summaries = sandboxAppSummaries.take(1)
      val myAppId   = summaries.head.id
      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

      private val result = underTest.addApplicationPrincipal()(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
    }

    "return the uplift journey 'before you start' page when the UpliftJourneyConfig returns On " +
      "and we have only 1 application" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

        val summaries = sandboxAppSummaries.take(1)
        val myAppId   = summaries.head.id
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${myAppId.value}/before-you-start")
      }

    "return the uplift journey 'Which application do you want production credentials for?' page when the UpliftJourneyConfig returns On" +
      "and we have more than 1 application" in new Setup {
        when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
        when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
        when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

        val summaries = sandboxAppSummaries.take(2)
        aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

        private val result = underTest.addApplicationPrincipal()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("Which application do you want production credentials for")
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
