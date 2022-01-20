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

package uk.gov.hmrc.modules.submissions.controllers

import controllers.BaseControllerSpec
import builder._
import utils._
import mocks.service.ApplicationServiceMock
import mocks.service.ApplicationActionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyApplicationProductionConnector
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.modules.submissions.services.mocks.SubmissionServiceMockModule
import domain.models.applications.ApplicationId
import uk.gov.hmrc.modules.submissions.views.html._
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import play.api.test.Helpers._
import domain.models.applications._
import controllers.SubscriptionTestHelperSugar

trait TPAProductionConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  object TPAProductionConnectorMock {
    val aMock = mock[ThirdPartyApplicationProductionConnector]

    object DeleteApplication {
      def willReturn() =
        when(aMock.deleteApplication(*[ApplicationId])(*)).thenReturn(successful(()))
    }
  }
}

class CancelRequestControllerSpec
  extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait HasSubscriptions {
    val aSubscription = exampleSubscriptionWithoutFields("prefix")
  }

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, session)
    
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }
  
  trait Setup 
    extends ApplicationServiceMock
    with ApplicationActionServiceMock
    with TPAProductionConnectorMockModule
    with SubmissionServiceMockModule
    with HasSessionDeveloperFlow
    with HasSubscriptions
    {

    val confirmCancelRequestForProductionCredentialsView = app.injector.instanceOf[ConfirmCancelRequestForProductionCredentialsView]
    val cancelledRequestForProductionCredentialsView = app.injector.instanceOf[CancelledRequestForProductionCredentialsView]

    val controller = new CancelRequestController(
      mockErrorHandler,
      cookieSigner,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      SubmissionServiceMock.aMock,
      TPAProductionConnectorMock.aMock,
      confirmCancelRequestForProductionCredentialsView,
      cancelledRequestForProductionCredentialsView
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  trait HasAppInProductionState {
    self: Setup with ApplicationActionServiceMock with ApplicationServiceMock =>
      
    givenApplicationAction(
      ApplicationWithSubscriptionData(
        sampleApp,
        asSubscriptions(List(aSubscription)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(aSubscription)
    )
    
    fetchByApplicationIdReturns(appId, sampleApp)
  }

  trait HasAppInTestingState {
    self: Setup with ApplicationActionServiceMock with ApplicationServiceMock =>
      
    givenApplicationAction(
      ApplicationWithSubscriptionData(
        testingApp,
        asSubscriptions(List(aSubscription)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, testingApp)
  }


  "CancelRequestController" should {
    import utils.SubmissionsTestData.extendedSubmission

    "cancelRequestForProductionCredentialsPage returns the page" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe OK

      contentAsString(result) should include("Are you sure you want to cancel your request for production credentials")
    }
    
    "cancelRequestForProductionCredentialsPage fails when app is not in testing state" in new Setup with HasSubscriptions with HasAppInProductionState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe BAD_REQUEST
    }
    
    "cancelRequestForProductionCredentialsPage fails when no submission exists" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      val result = controller.cancelRequestForProductionCredentialsPage(appId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe NOT_FOUND
    }
    
    "cancelRequestForProductionCredentialsAction when cancelling the request" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "cancel-request")

      TPAProductionConnectorMock.DeleteApplication.willReturn()

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("We have cancelled your request to get production credentials")
    }

    "cancelRequestForProductionCredentialsAction fails when app is not in testing state" in new Setup with HasSubscriptions with HasAppInProductionState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "dont-cancel-request")

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
    
    "cancelRequestForProductionCredentialsAction when rejecting the cancellation" in new Setup with HasSubscriptions with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "dont-cancel-request")

      val result = controller.cancelRequestForProductionCredentialsAction(appId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe s"/developer/submissions/application/${appId.value}/production-credentials-checklist"
    }
  }
}