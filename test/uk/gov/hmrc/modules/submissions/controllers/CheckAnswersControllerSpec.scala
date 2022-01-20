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

import controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import builder.{DeveloperBuilder, SampleApplication, SampleSession}
import controllers.SubscriptionTestHelperSugar
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import mocks.service.ApplicationServiceMock
import mocks.service.ApplicationActionServiceMock
import mocks.connector.ApmConnectorMockModule
import uk.gov.hmrc.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.modules.submissions.views.html.CheckAnswersView
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import utils.SubmissionsTestData.{applicationId, submission}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import uk.gov.hmrc.modules.submissions.services.RequestProductionCredentials
import uk.gov.hmrc.modules.submissions.views.html.ProductionCredentialsRequestReceivedView
import play.api.test.Helpers._
import uk.gov.hmrc.modules.submissions.domain.models.ExtendedSubmission
import uk.gov.hmrc.modules.submissions.domain.models.QuestionnaireProgress
import utils.SubmissionsTestData
import uk.gov.hmrc.modules.submissions.domain.models.Completed
import utils.AsIdsHelpers._
import uk.gov.hmrc.modules.submissions.domain.models.InProgress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import scala.concurrent.Future.{successful,failed}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound

class CheckAnswersControllerSpec 
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker  {

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

  trait Setup extends ApplicationServiceMock
    with ApplicationActionServiceMock
    with ApmConnectorMockModule
    with SubmissionServiceMockModule
    with HasSubscriptions
    with HasSessionDeveloperFlow
    with HasAppInTestingState {
    implicit val hc = HeaderCarrier()

    val mockSubmissionService = SubmissionServiceMock.aMock
    val mockApmConnector = ApmConnectorMock.aMock
    val mockRequestProdCreds = mock[RequestProductionCredentials]

    val completedProgress = List(SubmissionsTestData.DevelopmentPractices.questionnaire, SubmissionsTestData.BusinessDetails.questionnaire)
        .map(q => q.id -> QuestionnaireProgress(Completed, q.questions.asIds)).toMap
    val completedExtendedSubmission = ExtendedSubmission(submission, completedProgress)

    val incompleteProgress = List(SubmissionsTestData.DevelopmentPractices.questionnaire, SubmissionsTestData.BusinessDetails.questionnaire)
        .map(q => q.id -> QuestionnaireProgress(InProgress, q.questions.asIds)).toMap
    val incompleteExtendedSubmission = ExtendedSubmission(submission, incompleteProgress)

    val checkAnswersView = app.injector.instanceOf[CheckAnswersView]
    val productionCredentialsRequestReceivedView = app.injector.instanceOf[ProductionCredentialsRequestReceivedView]


    val underTest = new CheckAnswersController(mockErrorHandler, sessionServiceMock, applicationActionServiceMock, 
        applicationServiceMock, mcc, cookieSigner, mockApmConnector, mockSubmissionService, mockRequestProdCreds, 
        checkAnswersView, productionCredentialsRequestReceivedView)

    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "checkAnswersPage" should {
    "succeed when submission is complete" in new Setup {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(completedExtendedSubmission)

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect when submission is incomplete" in new Setup {
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(incompleteExtendedSubmission)

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
    }

    "return an error when submission is not found" in new Setup {
      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      val result = underTest.checkAnswersPage(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "checkAnswersAction" should {
    "succeed when production credentials are requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession])(*)).thenReturn(successful(Right(sampleApp)))
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(completedExtendedSubmission)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fail when production credentials are not requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession])(*)).thenReturn(failed(new ApplicationNotFound))
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(completedExtendedSubmission)

      intercept[ApplicationNotFound] {
        await(underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken))
      }
    }
  }

}
