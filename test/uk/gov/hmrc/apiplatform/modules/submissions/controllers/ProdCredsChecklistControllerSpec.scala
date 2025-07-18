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

import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.ProductionCredentialsChecklistView
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class ProdCredsChecklistControllerSpec
    extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with ExtendedSubscriptionTestHelper
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker {

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
      userSession,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, testingApp)
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with HasSessionDeveloperFlow
      with HasSubscriptions
      with SubmissionsTestData {

    val productionCredentialsChecklistView = app.injector.instanceOf[ProductionCredentialsChecklistView]

    val controller = new ProdCredsChecklistController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      SubmissionServiceMock.aMock,
      productionCredentialsChecklistView
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  trait HasAppInProductionState {
    self: Setup with ApplicationActionServiceMock with ApplicationServiceMock =>

    givenApplicationAction(
      sampleApp.withSubscriptions(asSubscriptions(List(aSubscription))).withFieldValues(Map.empty),
      userSession,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, sampleApp)
  }

  "productionCredentialsChecklist" should {
    "fail with NOT FOUND" in new Setup with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "succeed with app in testing state" in new Setup with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Terms of use checklist")
      contentAsString(result) shouldNot include("App name 1")
    }

    "succeed with app in production state" in new Setup with HasAppInProductionState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Agree to version 2 of the terms of use")
      contentAsString(result) should include("App name 1")
    }
  }

  "productionCredentialsChecklistAction" should {
    "return success when form is valid and incomplete" in new Setup with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeringSubmission.withIncompleteProgress())
      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Complete the development practices section")
      contentAsString(result) should include("Complete the customers authorising your software section")
      contentAsString(result) should include("Complete the organisation details section")
    }

    "redirect when when form is valid and complete" in new Setup with HasAppInTestingState {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress())

      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(appId).url)
    }
  }
}
