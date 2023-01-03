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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationActionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import play.api.test.Helpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.CredentialsRequestedView
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

class CredentialsRequestedControllerSpec
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
        submittedApp,
        asSubscriptions(List(aSubscription)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, submittedApp)
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

    val credentialsRequestedView = app.injector.instanceOf[CredentialsRequestedView]

    val controller = new CredentialsRequestedController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      ApmConnectorMock.aMock,
      SubmissionServiceMock.aMock,
      credentialsRequestedView
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "credentialsRequestedPage" should {
    "fail with NOT FOUND" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = controller.credentialsRequestedPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "succeed" in new Setup {
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(submittedSubmission.withCompletedProgress)

      val result = controller.credentialsRequestedPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }
  }
}
