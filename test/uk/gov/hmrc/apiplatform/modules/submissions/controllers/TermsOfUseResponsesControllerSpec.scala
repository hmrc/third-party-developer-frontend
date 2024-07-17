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

import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.status
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.TermsOfUseResponsesView
import uk.gov.hmrc.apiplatform.modules.tpd.builder.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{ApplicationStateHelper, SampleApplication, SampleSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class TermsOfUseResponsesControllerSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData
    with FixedClock
    with ApplicationStateHelper {

  trait HasSubscriptions {
    val aSubscription = exampleSubscriptionWithoutFields("prefix")
  }

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)

    fetchSessionByIdReturns(sessionId, session)

    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with ApmConnectorMockModule
      with SubmissionServiceMockModule
      with HasSubscriptions
      with HasSessionDeveloperFlow {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val termsOfUseResponsesView = app.injector.instanceOf[TermsOfUseResponsesView]

    def givenAppInState(appState: ApplicationState) = {
      val grantedApp = submittedApp.copy(state = appState)
      givenApplicationAction(
        ApplicationWithSubscriptionData(
          grantedApp,
          asSubscriptions(List(aSubscription)),
          asFields(List.empty)
        ),
        loggedInDeveloper,
        List(aSubscription)
      )

      fetchByApplicationIdReturns(appId, grantedApp)
    }

    val underTest = new TermsOfUseResponsesController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      ApmConnectorMock.aMock,
      SubmissionServiceMock.aMock,
      termsOfUseResponsesView
    )

    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "termsOfUseResponsesPage" should {
    "succeed when application is in production" in new Setup {
      givenAppInState(InState.production("requestedByEmail", "requestedByName", "verificationCode"))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(grantedSubmission.withCompletedProgress())

      val result = underTest.termsOfUseResponsesPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "succeed when application is in pre-production" in new Setup {
      givenAppInState(InState.preProduction("requestedByEmail", "requestedByName"))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(grantedSubmission.withCompletedProgress())

      val result = underTest.termsOfUseResponsesPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fails when application is not in production" in new Setup {
      givenAppInState(InState.pendingGatekeeperApproval("requestedByEmail", "requestedByName"))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturns(grantedSubmission.withCompletedProgress())

      val result = underTest.termsOfUseResponsesPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "fails when submission not found" in new Setup {
      givenAppInState(InState.production("requestedByEmail", "requestedByName", "verificationCode"))
      SubmissionServiceMock.FetchLatestExtendedSubmission.thenReturnsNone()

      val result = underTest.termsOfUseResponsesPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

}
