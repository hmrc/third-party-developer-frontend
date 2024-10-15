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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationName
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ResponsibleIndividualToUVerification, ResponsibleIndividualVerificationId, ResponsibleIndividualVerificationState}
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.ResponsibleIndividualVerificationServiceMockModule
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.{
  ResponsibleIndividualAcceptedView,
  ResponsibleIndividualDeclinedView,
  ResponsibleIndividualErrorView,
  VerifyResponsibleIndividualView
}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class VerifyResponsibleIndividualControllerSpec
    extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper
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
      submittedApp.withSubscriptions(asSubscriptions(List(aSubscription))).withFieldValues(Map.empty),
      // ApplicationWithSubscriptionFields(
      //   submittedApp,
      //   asSubscriptions(List(aSubscription)),
      //   asFields(List.empty)
      // ),
      userSession,
      List(aSubscription)
    )

    fetchByApplicationIdReturns(appId, submittedApp)
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with ApmConnectorMockModule
      with ResponsibleIndividualVerificationServiceMockModule
      with HasSessionDeveloperFlow
      with HasSubscriptions
      with HasAppInTestingState
      with SubmissionsTestData {

    val verifyResponsibleIndividualView   = app.injector.instanceOf[VerifyResponsibleIndividualView]
    val responsibleIndividualAcceptedView = app.injector.instanceOf[ResponsibleIndividualAcceptedView]
    val responsibleIndividualDeclinedView = app.injector.instanceOf[ResponsibleIndividualDeclinedView]
    val responsibleIndividualErrorView    = app.injector.instanceOf[ResponsibleIndividualErrorView]

    val controller = new VerifyResponsibleIndividualController(
      mockErrorHandler,
      cookieSigner,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      ResponsibleIndividualVerificationServiceMock.aMock,
      verifyResponsibleIndividualView: VerifyResponsibleIndividualView,
      responsibleIndividualAcceptedView: ResponsibleIndividualAcceptedView,
      responsibleIndividualDeclinedView: ResponsibleIndividualDeclinedView,
      responsibleIndividualErrorView: ResponsibleIndividualErrorView
    )

    val code = "12345678"

    val riVerification = ResponsibleIndividualToUVerification(
      ResponsibleIndividualVerificationId(code),
      ApplicationId.random,
      SubmissionId.random,
      0,
      ApplicationName("App name"),
      instant,
      ResponsibleIndividualVerificationState.INITIAL
    )

    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "verifyPage" should {
    "succeed" in new Setup {
      ResponsibleIndividualVerificationServiceMock.FetchResponsibleIndividualVerification.thenReturns(riVerification)

      val result = controller.verifyPage(code)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return bad request where no riVerification record can be found" in new Setup {
      ResponsibleIndividualVerificationServiceMock.FetchResponsibleIndividualVerification.thenReturnsNone()

      val result = controller.verifyPage(code)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "verifyAction" should {
    "succeed when RI has accepted" in new Setup {
      ResponsibleIndividualVerificationServiceMock.Accept.thenReturns(riVerification)
      private val request = loggedInRequest.withFormUrlEncodedBody("verified" -> "yes")

      val result = controller.verifyAction(code)(request.withCSRFToken)

      status(result) shouldBe OK
    }

    "succeed when RI has declined" in new Setup {
      ResponsibleIndividualVerificationServiceMock.Decline.thenReturns(riVerification)
      private val request = loggedInRequest.withFormUrlEncodedBody("verified" -> "no")

      val result = controller.verifyAction(code)(request.withCSRFToken)

      status(result) shouldBe OK
    }

    "bad request when RI has not selected a radio button" in new Setup {
      ResponsibleIndividualVerificationServiceMock.FetchResponsibleIndividualVerification.thenReturns(riVerification)

      val result = controller.verifyAction(code)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
