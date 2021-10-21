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
import mocks.service.SessionServiceMock
import mocks.service.ApplicationActionServiceMock
import mocks.service.ApplicationServiceMock
import mocks.connector.ApmConnectorMockModule
import modules.submissions.services.mocks.SubmissionServiceMockModule
import play.api.test.Helpers._
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import modules.submissions.views.html.ProductionCredentialsChecklistView
import domain.models.developers.Session
import domain.models.developers.LoggedInState
import builder.DeveloperBuilder
import utils.LocalUserIdTracker
import play.api.test.FakeRequest
import play.filters.csrf.CSRF
import utils.WithCSRFAddToken
import domain.models.applications.ApplicationWithSubscriptionData
import builder.SampleApplication
import controllers.SubscriptionTestHelperSugar
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.apidefinitions.ApiIdentifier
import domain.models.apidefinitions.ApiContext
import domain.models.apidefinitions.ApiVersion
import domain.models.apidefinitions.ApiVersionDefinition
import domain.models.apidefinitions.APIStatus
import domain.models.developers.DeveloperSession
import builder.SampleSession
import uk.gov.hmrc.http.HeaderCarrier

class ProdCredsChecklistControllerSpec
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

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper = DeveloperSession(session)
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val apiIdentifier1 = ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))

    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, apiIdentifier1.context, apiIdentifier1.version)

    val testAPISubscriptionStatus1 = APISubscriptionStatus(
      "test-api-1",
      "api-example-microservice",
      apiIdentifier1.context,
      ApiVersionDefinition(apiIdentifier1.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    fetchByApplicationIdReturns(appId, sampleApp)

    givenApplicationAction(
      ApplicationWithSubscriptionData(
        sampleApp,
        asSubscriptions(List(testAPISubscriptionStatus1)),
        asFields(List.empty)
      ),
      loggedInDeveloper,
      List(testAPISubscriptionStatus1)
    )
  }

  "productionCredentialsChecklist" should {
    "fail with a BAD REQUEST" in new Setup {
      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      val result = controller.productionCredentialsChecklist(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }

    "succeed" in new Setup {
      import utils.SubmissionsTestData.submission

      SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

      val result = controller.productionCredentialsChecklist(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }
  }
}
