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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import builder.{DeveloperBuilder, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import mocks.service.ApplicationServiceMock
import mocks.service.ApplicationActionServiceMock
import mocks.connector.ApmConnectorMockModule
import uk.gov.hmrc.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.modules.submissions.views.html.CheckAnswersView
import uk.gov.hmrc.modules.submissions.views.html.QuestionView
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import utils.SubmissionsTestData.{applicationId, questionId, question2Id, submission, extendedSubmission}
import uk.gov.hmrc.modules.submissions.domain.models.QuestionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF

class QuestionControllerSpec 
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

  trait HasAppInTestingState {
    self: HasSubscriptions with ApplicationActionServiceMock with ApplicationServiceMock =>
      
    givenApplicationAction(
      ApplicationWithSubscriptionData(
        testingApp.copy(id = applicationId),
        asSubscriptions(List(aSubscription)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(aSubscription)
    )
    
    fetchByApplicationIdReturns(applicationId, sampleApp)
  }

  trait Setup 
    extends ApplicationServiceMock
    with ApplicationActionServiceMock
    with ApmConnectorMockModule
    with SubmissionServiceMockModule
    with HasSubscriptions
    with HasSessionDeveloperFlow
    with HasAppInTestingState {

    implicit val hc = HeaderCarrier()

    val questionView = app.injector.instanceOf[QuestionView]
    val checkAnswersView = app.injector.instanceOf[CheckAnswersView]

    val controller = new QuestionsController(
      mockErrorHandler,
      sessionServiceMock,
      applicationServiceMock,
      applicationActionServiceMock,
      SubmissionServiceMock.aMock,
      cookieSigner,
      questionView,
      checkAnswersView,
      mcc
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "showQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)

      val result = controller.showQuestion(submission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)

      val result = controller.showQuestion(submission.id, QuestionId("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "recordAnswer" should {
    "succeed when answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(extendedSubmission)
      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/${submission.id.value}/question/${question2Id.value}")
    }
    
    "return when no valid answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturnsNone()
      private val answer1 = ""
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("Please provide an answer to the question")
    }

    "fail if no answer field in form" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
