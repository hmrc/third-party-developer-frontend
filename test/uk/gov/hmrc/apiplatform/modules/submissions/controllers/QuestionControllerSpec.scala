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

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

import cats.data.NonEmptyList
import org.scalatest.AppendedClues

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.{CheckAnswersView, QuestionView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

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

    val questionView     = app.injector.instanceOf[QuestionView]
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
      val result             = controller.showQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) contains (formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
    }

    "succeed and check for label, hintText and afterStatement" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val formSubmissionLink = s"${aSubmission.id.value}/question/${testQuestionIdsOfInterest.responsibleIndividualEmailId.value}"
      val result             = controller.showQuestion(aSubmission.id, testQuestionIdsOfInterest.responsibleIndividualEmailId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) contains (formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
      contentAsString(result) contains ("Email address") shouldBe true withClue ("HTML content did not contain label")
      contentAsString(result) contains ("Cannot be a shared mailbox") shouldBe true withClue ("HTML content did not contain hintText")
      contentAsString(
        result
      ) contains (s"""aria-describedby="hint-${testQuestionIdsOfInterest.responsibleIndividualEmailId.value}"""") shouldBe true withClue ("HTML content did not contain describeBy")
      contentAsString(result) contains ("We will email a verification link to the responsible individual that expires in 10 working days.") shouldBe true withClue ("HTML content did not contain afterStatement")
      contentAsString(result) contains ("<title>")
    }

    "display fail and show error in title when applicable" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val formSubmissionLink = s"${aSubmission.id.value}/question/${testQuestionIdsOfInterest.responsibleIndividualEmailId.value}"
      val result             =
        controller.showQuestion(aSubmission.id, testQuestionIdsOfInterest.responsibleIndividualEmailId, None, Some(ErrorInfo("blah", "message")))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) contains ("<title>Error:") shouldBe true withClue ("Page title should contain `Error: ` prefix")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val result = controller.showQuestion(aSubmission.id, Question.Id("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }

  }

  "updateQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val formSubmissionLink = s"${aSubmission.id.value}/question/${questionId.value}/update"
      val result             = controller.updateQuestion(aSubmission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) contains (formSubmissionLink) shouldBe true withClue (s"(HTML content did not contain $formSubmissionLink)")
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)

      val result = controller.updateQuestion(aSubmission.id, Question.Id("BAD_ID"))(loggedInRequest.withCSRFToken)

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

    "fail if invalid answer provided and returns custom error message" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      SubmissionServiceMock.RecordAnswer.thenReturnsNone()
      private val invalidEmailAnswer = "bob"
      private val request            = loggedInRequest.withFormUrlEncodedBody("answer" -> invalidEmailAnswer, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionRI2.id)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      val errorInfo = OrganisationDetails.questionRI2.errorInfo.get

      val expectedErrorSummary = errorInfo.summary
      body should include(expectedErrorSummary)

      val expectedErrorMessage = errorInfo.message.getOrElse(errorInfo.summary)
      body should include(expectedErrorMessage)
    }

    "fail if no answer provided and returns custom error message" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      SubmissionServiceMock.RecordAnswer.thenReturnsNone()
      private val blankAnswer = ""
      private val request     = loggedInRequest.withFormUrlEncodedBody("answer" -> blankAnswer, "submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, OrganisationDetails.questionRI1.id)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      val errorInfo = OrganisationDetails.questionRI1.errorInfo.get

      val expectedErrorSummary = errorInfo.summary
      body should include(expectedErrorSummary)

      val expectedErrorMessage = errorInfo.message.getOrElse(errorInfo.summary)
      body should include(expectedErrorMessage)
    }

    "fail if no answer field in form" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress)
      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "save")

      val result = controller.recordAnswer(aSubmission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "updateAnswer" should {
    "succeed when given an answer and redirect to check answers page if no more questions need answering" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        Submission.Id.random,
        applicationId,
        LocalDateTime.now(ZoneOffset.UTC),
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(completeAnswersToQuestions)
        .withCompletedProgress

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(fullyAnsweredSubmission)

      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.updateAnswer(aSubmission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId.value}/check-answers")
    }

    "succeed when given an answer and redirect to the next question to answer" in new Setup {
      val fullyAnsweredSubmission = Submission.create(
        "bob@example.com",
        Submission.Id.random,
        applicationId,
        LocalDateTime.now(ZoneOffset.UTC),
        testGroups,
        testQuestionIdsOfInterest,
        standardContext
      )
        .hasCompletelyAnsweredWith(completeAnswersToQuestions)
        .withCompletedProgress

      val modifiedAnswersToQuestions = fullyAnsweredSubmission.submission.latestInstance.answersToQuestions -
        OrganisationDetails.question2c.id ++ Map(
          OrganisationDetails.question2.id -> SingleChoiceAnswer("Unique Taxpayer Reference (UTR)")
        )

      val modifiedProgress = Map(OrganisationDetails.questionnaire.id ->
        QuestionnaireProgress(
          QuestionnaireState.InProgress,
          List(
            OrganisationDetails.questionRI1.id,
            OrganisationDetails.questionRI2.id,
            OrganisationDetails.question1.id,
            OrganisationDetails.question2.id,
            OrganisationDetails.question2b.id
          )
        ))

      val modifiedSubmission: ExtendedSubmission = fullyAnsweredSubmission.copy(
        submission = fullyAnsweredSubmission.submission.copy(
          instances = NonEmptyList(
            fullyAnsweredSubmission.submission.latestInstance.copy(
              answersToQuestions = modifiedAnswersToQuestions
            ),
            Nil
          )
        ),
        questionnaireProgress = fullyAnsweredSubmission.questionnaireProgress ++ modifiedProgress
      )

      SubmissionServiceMock.Fetch.thenReturns(fullyAnsweredSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(modifiedSubmission)

      private val utrAnswer = "Unique Taxpayer Reference (UTR)"
      private val request   = loggedInRequest.withFormUrlEncodedBody("answer" -> utrAnswer, "submit-action" -> "save")

      private val questionId         = OrganisationDetails.question2.id
      private val followUpQuestionId = OrganisationDetails.question2b.id

      val result = controller.updateAnswer(fullyAnsweredSubmission.submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/${fullyAnsweredSubmission.submission.id.value}/question/${followUpQuestionId.value}/update")
    }
  }

  "PossibleAnswer.htmlValue" should {
    "return no spaces" in {
      val htmlValue = PossibleAnswer("something with spaces").htmlValue
      htmlValue.contains(" ") shouldBe false
    }

    "return hyphens instead of spaces" in {
      val htmlValue = PossibleAnswer("something with spaces").htmlValue
      htmlValue shouldBe "something-with-spaces"
    }

    "remove extraneous characters" in {
      val htmlValue = PossibleAnswer("something#hashed").htmlValue
      htmlValue.contains("#") shouldBe false
      htmlValue shouldBe "somethinghashed"
    }
  }
}
