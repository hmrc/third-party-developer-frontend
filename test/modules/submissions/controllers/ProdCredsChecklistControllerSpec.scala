/*
 * Copyright 2021 HM Revenue & Customs
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

package modules.submissions.controllers

import controllers.BaseControllerSpec
import mocks.service.ApplicationServiceMock
import mocks.connector.ApmConnectorMockModule
import modules.submissions.services.mocks.SubmissionServiceMockModule
import play.api.test.Helpers._
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import modules.submissions.views.html.ProductionCredentialsChecklistView
import builder.DeveloperBuilder
import utils.LocalUserIdTracker
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import utils.WithCSRFAddToken
import domain.models.applications.ApplicationWithSubscriptionData
import builder.SampleApplication
import controllers.SubscriptionTestHelperSugar
import builder.SampleSession
import mocks.service.ApplicationActionServiceMock
import modules.submissions.domain.models.ExtendedSubmission

class ProdCredsChecklistControllerSpec
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
        testingApp,
        asSubscriptions(List(aSubscription)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, testingApp)
  }

  trait Setup 
    extends ApplicationServiceMock
    with ApplicationActionServiceMock
    with ApmConnectorMockModule
    with SubmissionServiceMockModule
    with HasSessionDeveloperFlow
    with HasSubscriptions
    with HasAppInTestingState {

    val productionCredentialsChecklistView = app.injector.instanceOf[ProductionCredentialsChecklistView]

    val controller = new ProdCredsChecklistController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      ApmConnectorMock.aMock,
      SubmissionServiceMock.aMock,
      productionCredentialsChecklistView
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


  "productionCredentialsChecklist" should {
    "fail with NOT FOUND" in new Setup {
      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "succeed" in new Setup {
      import utils.SubmissionsTestData.extendedSubmission

      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }
  }

  "productionCredentialsChecklistAction" should {
    "return success when form is valid and incomplete" in new Setup {
      import utils.SubmissionsTestData.extendedSubmission

      SubmissionServiceMock.FetchLatestSubmission.thenReturns(extendedSubmission)

      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect when when form is valid and complete" in new Setup {
      import utils.SubmissionsTestData.{submission,completedProgress}
      val completedSubmission = ExtendedSubmission(submission, completedProgress)

      SubmissionServiceMock.FetchLatestSubmission.thenReturns(completedSubmission)

      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(appId).url)
    }

  }
}
