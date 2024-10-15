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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, status}
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.StartUsingYourApplicationView
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{ApplicationStateHelper, SampleApplication}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class StartUsingYourApplicationControllerSpec extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData
    with FixedClock
    with ApplicationStateHelper {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, userSession)

    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  trait Setup extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with ApmConnectorMockModule
      with SubmissionServiceMockModule
      with HasSessionDeveloperFlow {
    val view                       = app.injector.instanceOf[StartUsingYourApplicationView]
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val underTest       = new StartUsingYourApplicationController(
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
    val applicationId   = ApplicationId.random
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "startUsingYourApplicationPage" should {
    "return success for app in PRE_PRODUCTION state" in new Setup {
      val app = sampleApp.withState(InState.preProduction(userSession.developer.email.text, userSession.developer.displayedName))
      givenApplicationAction(app, userSession)

      val result = underTest.startUsingYourApplicationPage(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return failure for app in non-PRE_PRODUCTION state" in new Setup {
      val pendingApprovalApp = sampleApp.withState(InState.pendingGatekeeperApproval(userSession.developer.email.text, userSession.developer.displayedName))
      givenApplicationAction(pendingApprovalApp, userSession)

      val result = underTest.startUsingYourApplicationPage(pendingApprovalApp.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "startUsingYourApplicationAction" should {
    "redirect to manage apps page when submission service called successfully" in new Setup {
      val app = sampleApp.withState(InState.preProduction(userSession.developer.email.text, userSession.developer.displayedName))
      givenApplicationAction(app, userSession)
      SubmissionServiceMock.ConfirmSetupComplete.thenReturnSuccessFor(app.id, userSession.developer.email)

      val result = underTest.startUsingYourApplicationAction(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageApplications.manageApps().url)
    }

    "redirect to bad request page when submission service called unsuccessfully" in new Setup {
      val app = sampleApp.withState(InState.preProduction(userSession.developer.email.text, userSession.developer.displayedName))
      givenApplicationAction(app, userSession)
      SubmissionServiceMock.ConfirmSetupComplete.thenReturnFailure()

      val result = underTest.startUsingYourApplicationAction(app.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
