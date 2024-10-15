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

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{WithCSRFAddToken, _}

class CancelRequestControllerSpec
    extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with CollaboratorTracker
    with SubmissionsTestData {

  trait HasSubscriptions {
    val aSubscription = exampleSubscriptionWithoutFields("prefix")
  }

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, userSession)

    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with ApplicationCommandConnectorMockModule
      with SubmissionServiceMockModule
      with HasSessionDeveloperFlow
      with HasSubscriptions
      with FixedClock {

    val confirmCancelRequestForProductionCredentialsView = app.injector.instanceOf[ConfirmCancelRequestForProductionCredentialsView]
    val cancelledRequestForProductionCredentialsView     = app.injector.instanceOf[CancelledRequestForProductionCredentialsView]

    val controller = new CancelRequestController(
      mockErrorHandler,
      cookieSigner,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      SubmissionServiceMock.aMock,
      ApplicationCommandConnectorMock.aMock,
      confirmCancelRequestForProductionCredentialsView,
      cancelledRequestForProductionCredentialsView,
      clock
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)

    val extendedSubmission = aSubmission.withIncompleteProgress()
  }

  trait HasAppInProductionState {
    self: Setup with ApplicationActionServiceMock with ApplicationServiceMock =>

    givenApplicationAction(
      sampleApp.withSubscriptions(asSubscriptions(List(aSubscription))).withFieldValues(Map.empty),
      // sampleApp
      //   .withSubscriptions(Set(aSubscription))
      //   .withFields(Map.empty),
      userSession,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, sampleApp)
  }

  trait HasAppInTestingState {
    self: Setup with ApplicationActionServiceMock with ApplicationServiceMock =>

    givenApplicationAction(
      testingApp.withSubscriptions(asSubscriptions(List(aSubscription))).withFieldValues(Map.empty),
      // ApplicationWithSubscriptions(
      //   testingApp,
      //   asSubscriptions(List(aSubscription)),
      //   asFields(List.empty)
      // ),
      userSession,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, testingApp)
  }

  "CancelRequestController" should {
    "cancelRequestForProductionCredentialsPage returns the page" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extendedSubmission)

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe OK

      contentAsString(result) should include("Are you sure you want to cancel your request for production credentials")
    }

    "cancelRequestForProductionCredentialsPage fails when app is not in testing state" in new Setup with HasSubscriptions with HasAppInProductionState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extendedSubmission)

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe BAD_REQUEST
    }

    "cancelRequestForProductionCredentialsPage fails when no submission exists" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe NOT_FOUND
    }

    "cancelRequestForProductionCredentialsAction when cancelling the request" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "cancel-request")

      ApplicationCommandConnectorMock.DispatchWithThrow.thenReturnsSuccess(mock[ApplicationWithCollaborators])

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("We have cancelled your request to get production credentials")
    }

    "cancelRequestForProductionCredentialsAction fails when app is not in testing state" in new Setup with HasSubscriptions with HasAppInProductionState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "dont-cancel-request")

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }

    "cancelRequestForProductionCredentialsAction when rejecting the cancellation" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "dont-cancel-request")

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe s"/developer/submissions/application/${appId}/production-credentials-checklist"
    }
  }
}
