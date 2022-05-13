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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers

import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, status}
import play.filters.csrf.CSRF
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.StartUsingYourApplicationView
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ApplicationState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global

class StartUsingYourApplicationControllerSpec extends BaseControllerSpec
  with SampleSession
  with SampleApplication
  with SubscriptionTestHelperSugar
  with WithCSRFAddToken
  with DeveloperBuilder
  with LocalUserIdTracker
  with SubmissionsTestData {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, session)

    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  trait Setup extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with ApmConnectorMockModule
      with SubmissionServiceMockModule
      with HasSessionDeveloperFlow {
    val view = app.injector.instanceOf[StartUsingYourApplicationView]
    implicit val hc = HeaderCarrier()

    val underTest = new StartUsingYourApplicationController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      ApmConnectorMock.aMock,
      SubmissionServiceMock.aMock,
      view
    )
    val applicationId = ApplicationId.random
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "startUsingYourApplicationPage" should {
    "return success for app in PRE_PRODUCTION state" in new Setup {
      val app = sampleApp.copy(state = ApplicationState.preProduction(loggedInDeveloper.email))
      givenApplicationAction(app, loggedInDeveloper)

      val result = underTest.startUsingYourApplicationPage(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return failure for app in non-PRE_PRODUCTION state" in new Setup {
      val pendingApprovalApp = sampleApp.copy(state = ApplicationState.pendingGatekeeperApproval(loggedInDeveloper.email))
      givenApplicationAction(pendingApprovalApp, loggedInDeveloper)

      val result = underTest.startUsingYourApplicationPage(pendingApprovalApp.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "startUsingYourApplicationAction" should {
    "redirect to manage apps page when submission service called successfully" in new Setup {
      val app = sampleApp.copy(state = ApplicationState.preProduction(loggedInDeveloper.email))
      givenApplicationAction(app, loggedInDeveloper)
      SubmissionServiceMock.ConfirmSetupComplete.thenReturnSuccessFor(app.id, loggedInDeveloper.email)

      val result = underTest.startUsingYourApplicationAction(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageApplications.manageApps.url)
    }

    "redirect to bad request page when submission service called unsuccessfully" in new Setup {
      val app = sampleApp.copy(state = ApplicationState.preProduction(loggedInDeveloper.email))
      givenApplicationAction(app, loggedInDeveloper)
      SubmissionServiceMock.ConfirmSetupComplete.thenReturnFailure()

      val result = underTest.startUsingYourApplicationAction(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}