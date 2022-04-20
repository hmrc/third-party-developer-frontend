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

import org.mockito.captor.ArgCaptor
import play.api.data.Form
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationActionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import play.api.test.Helpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.ProductionCredentialsChecklistView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleSession
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.ProdCredsChecklistController.{DummyForm, ViewModel}

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
    with HasAppInTestingState
    with SubmissionsTestData {

    val productionCredentialsChecklistView = mock[ProductionCredentialsChecklistView]
    when(productionCredentialsChecklistView.apply(*[ViewModel], *)(*, *, *,*)).thenReturn(play.twirl.api.HtmlFormat.empty)
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
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "succeed" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress)

      val result = controller.productionCredentialsChecklistPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }
  }

  "productionCredentialsChecklistAction" should {
    "return success when form is valid and incomplete" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeringSubmission.withIncompleteProgress)
      val formCaptor = ArgCaptor[Form[DummyForm]]
      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      verify(productionCredentialsChecklistView).apply(*, formCaptor.capture)(*,*,*,*)
      val form = formCaptor.value
      form.errors.size shouldBe 3
      form.errors("development practices").head.messages shouldBe Seq("Complete the development practices section")
      form.errors("customers authorising your software").head.messages shouldBe Seq("Complete the customers authorising your software section")
      form.errors("organisation details").head.messages shouldBe Seq("Complete the organisation details section")
    }

    "redirect when when form is valid and complete" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(answeredSubmission.withCompletedProgress)

      val result = controller.productionCredentialsChecklistAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(appId).url)
    }

  }
}
