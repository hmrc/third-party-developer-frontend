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

import org.mockito.captor.ArgCaptor
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationActionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.CheckAnswersView
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import uk.gov.hmrc.apiplatform.modules.submissions.services.RequestProductionCredentials
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.ProductionCredentialsRequestReceivedView
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ExtendedSubmission, NoAnswer, QuestionnaireProgress, SingleChoiceAnswer, Submission}
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.Completed
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsIdsHelpers._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.InProgress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.CheckAnswersController.ProdCredsRequestReceivedViewModel

class CheckAnswersControllerSpec 
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
    with HasAppInTestingState {

    implicit val hc = HeaderCarrier()

    val mockApmConnector = ApmConnectorMock.aMock
    val mockRequestProdCreds = mock[RequestProductionCredentials]

    val completedProgress = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire)
        .map(q => q.id -> QuestionnaireProgress(Completed, q.questions.asIds)).toMap
    val completedExtendedSubmission = ExtendedSubmission(aSubmission, completedProgress)

    val incompleteProgress = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire)
        .map(q => q.id -> QuestionnaireProgress(InProgress, q.questions.asIds)).toMap
    val incompleteExtendedSubmission = ExtendedSubmission(aSubmission, incompleteProgress)

    val checkAnswersView = mock[CheckAnswersView]
    when(checkAnswersView.apply(*,*,*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    val productionCredentialsRequestReceivedView = mock[ProductionCredentialsRequestReceivedView]
    val viewModelCaptor = ArgCaptor[ProdCredsRequestReceivedViewModel]
    when(productionCredentialsRequestReceivedView.apply(*)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)

    val underTest = new CheckAnswersController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      mockApmConnector,
      SubmissionServiceMock.aMock,
      mockRequestProdCreds,
      checkAnswersView,
      productionCredentialsRequestReceivedView
    )

    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "checkAnswersPage" should {
    "succeed when submission is complete" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress)

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect when submission is incomplete" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(aSubmission.withIncompleteProgress)

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
    }

    "return an error when submission is not found" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "show submission declined text when previous submission was declined" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(declinedSubmission.withCompletedProgress)

      await(underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken))

      verify(checkAnswersView).apply(*, eqTo(true), *)(*, *, *, *)
    }
    "don't show submission declined text when previous submission was not declined" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress)

      await(underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken))

      verify(checkAnswersView).apply(*, eqTo(false), *)(*, *, *, *)
    }
  }

  "checkAnswersAction" should {
    "succeed when production credentials are requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession], *)(*)).thenReturn(successful(Right(sampleApp)))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fail when production credentials are not requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession], *)(*)).thenReturn(failed(new ApplicationNotFound))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeringSubmission.withIncompleteProgress)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe SEE_OTHER
    }

    "don't display verification email text if requester is the Responsible Individual" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession], *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, SingleChoiceAnswer("Yes"))
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      await(underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken))

      verify(productionCredentialsRequestReceivedView).apply(viewModelCaptor.capture)(*, *, *, *)
      viewModelCaptor.value.requesterIsResponsibleIndividual shouldBe true
    }

    "do display verification email text if requester is not the Responsible Individual" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession], *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, SingleChoiceAnswer("No"))
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      await(underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken))

      verify(productionCredentialsRequestReceivedView).apply(viewModelCaptor.capture)(*, *, *, *)
      viewModelCaptor.value.requesterIsResponsibleIndividual shouldBe false
    }

    "don't display verification email text if requester is Responsible Individual question not answered" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession], *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, NoAnswer)
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      await(underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken))

      verify(productionCredentialsRequestReceivedView).apply(viewModelCaptor.capture)(*, *, *, *)
      viewModelCaptor.value.requesterIsResponsibleIndividual shouldBe false
    }
  }

}
