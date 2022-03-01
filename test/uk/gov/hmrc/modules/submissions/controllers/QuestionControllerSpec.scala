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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationActionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.CheckAnswersView
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.QuestionView
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import org.scalatest.AppendedClues

class QuestionControllerSpec 
  extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {

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
    with HasAppInTestingState
    with AppendedClues {

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
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val formSubmissionLink = s"${aSubmission.id.value}/question/${questionId.value}"
      val result = controller.showQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) contains(formSubmissionLink) shouldBe true withClue(s"(HTML content did not contain $formSubmissionLink)")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val result = controller.showQuestion(aSubmission.id, QuestionId("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "updateQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val formSubmissionLink = s"${aSubmission.id.value}/question/${questionId.value}/update"
      val result = controller.updateQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)
      
      status(result) shouldBe OK
      contentAsString(result) contains(formSubmissionLink) shouldBe true withClue(s"(HTML content did not contain $formSubmissionLink)")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val result = controller.updateQuestion(aSubmission.id, QuestionId("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "recordAnswer" should {
    "succeed when answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      SubmissionServiceMock.RecordAnswer.thenReturns(aSubmission.withIncompleteProgress)
      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/${aSubmission.id.value}/question/${question2Id.value}")
    }
    
    "return when no valid answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      SubmissionServiceMock.RecordAnswer.thenReturnsNone()
      private val answer1 = ""
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("Please provide an answer to the question")
    }

    "fail if no answer field in form" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
