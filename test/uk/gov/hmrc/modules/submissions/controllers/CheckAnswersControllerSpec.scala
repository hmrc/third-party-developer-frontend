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
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import uk.gov.hmrc.apiplatform.modules.submissions.services.RequestProductionCredentials
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.ProductionCredentialsRequestReceivedView
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ExtendedSubmission
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireProgress
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.Completed
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsIdsHelpers._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.InProgress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import scala.concurrent.Future.{successful,failed}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData

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

    val checkAnswersView = app.injector.instanceOf[CheckAnswersView]
    val productionCredentialsRequestReceivedView = app.injector.instanceOf[ProductionCredentialsRequestReceivedView]

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
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgresss)

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
  }

  "checkAnswersAction" should {
    "succeed when production credentials are requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession])(*)).thenReturn(successful(Right(sampleApp)))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgresss)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fail when production credentials are not requested successfully" in new Setup {
      when(mockRequestProdCreds.requestProductionCredentials(eqTo(applicationId), *[DeveloperSession])(*)).thenReturn(failed(new ApplicationNotFound))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeringSubmission.withIncompleteProgress)

      val result = underTest.checkAnswersAction(applicationId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe SEE_OTHER
    }
  }

}
