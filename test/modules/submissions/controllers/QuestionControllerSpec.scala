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

import controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import builder.{DeveloperBuilder, SampleApplication, SampleSession}
import controllers.SubscriptionTestHelperSugar
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import mocks.service.ApplicationServiceMock
import mocks.service.ApplicationActionServiceMock
import mocks.connector.ApmConnectorMockModule
import modules.submissions.services.mocks.SubmissionServiceMockModule
import mocks.service.SessionServiceMock
import uk.gov.hmrc.http.HeaderCarrier
import modules.submissions.views.html.QuestionView
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import domain.models.developers.Session
import domain.models.developers.LoggedInState
import domain.models.developers.DeveloperSession
import play.filters.csrf.CSRF
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.SubmissionsTestData.{applicationId, questionId, question2Id, submission, extendedSubmission}
import modules.submissions.domain.models.QuestionId
import domain.models.apidefinitions._
import domain.models.applications.ApplicationWithSubscriptionData
import domain.models.applications.Application
import uk.gov.hmrc.time.DateTimeUtils
import java.time.Period
import domain.models.applications.Environment
import domain.models.applications.ApplicationState
import domain.models.applications.Standard

class QuestionControllerSpec 
  extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup 
    extends ApplicationServiceMock
    with ApplicationActionServiceMock
    with ApmConnectorMockModule
    with SubmissionServiceMockModule
    with SessionServiceMock {

    implicit val hc = HeaderCarrier()

    val questionView = app.injector.instanceOf[QuestionView]

    val controller = new QuestionsController(
      mockErrorHandler,
      sessionServiceMock,
      applicationServiceMock,
      applicationActionServiceMock,
      SubmissionServiceMock.aMock,
      cookieSigner,
      questionView,
      mcc
    )

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper = DeveloperSession(session)
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)
    
    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val apiIdentifier1 = ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))

    val emptyFields = emptySubscriptionFieldsWrapper(applicationId, clientId, apiIdentifier1.context, apiIdentifier1.version)

    val testAPISubscriptionStatus1 = APISubscriptionStatus(
      "test-api-1",
      "api-example-microservice",
      apiIdentifier1.context,
      ApiVersionDefinition(apiIdentifier1.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val application = Application(
      applicationId,
      clientId,
      "App name 1",
      DateTimeUtils.now,
      DateTimeUtils.now,
      None,
      grantLength = Period.ofDays(547),
      Environment.PRODUCTION,
      Some("Description 1"),
      Set(loggedInDeveloper.email.asAdministratorCollaborator),
      state = ApplicationState.production(loggedInDeveloper.email, ""),
      access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
    )

    fetchByApplicationIdReturns(applicationId, application)

    givenApplicationAction(
      ApplicationWithSubscriptionData(
        application,
        asSubscriptions(List(testAPISubscriptionStatus1)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(testAPISubscriptionStatus1)
    )
  }

  "showQuestion" should {
    "succeed" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)

      val result = controller.showQuestion(submission.id, questionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "fail with a BAD REQUEST for an invalid questionId" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)

      val result = controller.showQuestion(submission.id, QuestionId("BAD_ID"))(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "recordAnswer" should {
    "succeed when answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturns(extendedSubmission)
      private val answer1 = "Yes"
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/${submission.id.value}/question/${question2Id.value}")
    }
    
    "return when no valid answer given" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      SubmissionServiceMock.RecordAnswer.thenReturnsNone()
      private val answer1 = ""
      private val request = loggedInRequest.withFormUrlEncodedBody("answer" -> answer1, "submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val body = contentAsString(result)

      body should include("Please provide an answer to the question")
    }

    "fail if no answer field in form" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(extendedSubmission)
      private val request = loggedInRequest.withFormUrlEncodedBody("submit-action" -> "save")

      val result = controller.recordAnswer(submission.id, questionId)(request.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
