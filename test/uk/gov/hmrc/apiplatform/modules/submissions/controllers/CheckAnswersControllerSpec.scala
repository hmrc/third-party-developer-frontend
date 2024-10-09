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
import scala.concurrent.Future.{failed, successful}

import org.mockito.captor.ArgCaptor

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.CheckAnswersController.ProdCredsRequestReceivedViewModel
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.{Completed, InProgress}
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ActualAnswer, ExtendedSubmission, QuestionnaireProgress}
import uk.gov.hmrc.apiplatform.modules.submissions.services.RequestProductionCredentials
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.{CheckAnswersView, ProductionCredentialsRequestReceivedView}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsIdsHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class CheckAnswersControllerSpec
    extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {

  trait HasSubscriptions {
    val aSubscription = exampleSubscriptionWithoutFields("prefix")
  }

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, userSession)

    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  trait HasAppInTestingState {
    self: HasSubscriptions with ApplicationActionServiceMock with ApplicationServiceMock =>

    givenApplicationAction(
      testingApp.withSubscriptions(asSubscriptions(List(aSubscription))).withFieldValues(Map.empty),
      // ApplicationWithSubscriptions(
      //   testingApp.copy(id = applicationId),
      //   asSubscriptions(List(aSubscription)),
      //   asFields(List.empty)
      // ),
      userSession,
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

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockApmConnector     = ApmConnectorMock.aMock
    val mockRequestProdCreds = mock[RequestProductionCredentials]

    val completedProgress           = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(Completed, q.questions.asIds())).toMap
    val completedExtendedSubmission = ExtendedSubmission(aSubmission, completedProgress)

    val incompleteProgress           = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(InProgress, q.questions.asIds())).toMap
    val incompleteExtendedSubmission = ExtendedSubmission(aSubmission, incompleteProgress)

    val checkAnswersView                         = mock[CheckAnswersView]
    when(checkAnswersView.apply(*, *, *, *)(*, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)
    val productionCredentialsRequestReceivedView = mock[ProductionCredentialsRequestReceivedView]
    val viewModelCaptor                          = ArgCaptor[ProdCredsRequestReceivedViewModel]
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
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect when submission is incomplete" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(aSubmission.withIncompleteProgress())

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/production-credentials-checklist")
    }

    "return an error when submission is not found" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "show submission declined text when previous submission was declined" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(declinedSubmission.withCompletedProgress())

      await(underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken))

      verify(checkAnswersView).apply(*, eqTo(true), *, *)(*, *, *, *)
    }
    "don't show submission declined text when previous submission was not declined" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      await(underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken))

      verify(checkAnswersView).apply(*, eqTo(false), *, *)(*, *, *, *)
    }
  }

  "checkAnswersAction" should {
    "succeed when production credentials are requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(*, *[UserSession], *, *)(*)).thenReturn(successful(Right(sampleApp)))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/request-received")
    }

    "fail when production credentials are not requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(*, *[UserSession], *, *)(*)).thenReturn(failed(new ApplicationNotFound))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeringSubmission.withIncompleteProgress())

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/production-credentials-checklist")
    }

    "don't display verification email text if requester is the Responsible Individual" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(*, *[UserSession], *, *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers       = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, ActualAnswer.SingleChoiceAnswer("Yes"))
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/request-received")
    }

    "do display verification email text if requester is not the Responsible Individual" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(*, *[UserSession], *, *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers       = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, ActualAnswer.SingleChoiceAnswer("No"))
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/request-received")
    }

    "don't display verification email text if requester is Responsible Individual question not answered" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(*, *[UserSession], *, *)(*)).thenReturn(successful(Right(sampleApp)))
      val answers       = answersToQuestions.updated(testQuestionIdsOfInterest.responsibleIndividualIsRequesterId, ActualAnswer.NoAnswer)
      val extSubmission = ExtendedSubmission(answeredSubmission.hasCompletelyAnsweredWith(answers), completedProgress)
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(extSubmission)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId}/request-received")
    }
  }

}
