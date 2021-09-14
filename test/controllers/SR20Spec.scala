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

package controllers

import builder.{DeveloperBuilder, SubscriptionsBuilder, _}
import controllers.models.ApiSubscriptionsFlow
import domain.models.apidefinitions._
import domain.models.applications.ApplicationWithSubscriptionData
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import mocks.connector.ApmConnectorMockModule
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import play.api.i18n.DefaultMessagesApi
import play.api.mvc.MessagesRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.html.{ConfirmApisView, TurnOffApisMasterView}

import scala.concurrent.ExecutionContext.Implicits.global

class SR20Spec extends BaseControllerSpec
                with SampleSession
                with SampleApplication
                with SubscriptionTestHelperSugar
                with WithCSRFAddToken
                with SubscriptionsBuilder
                with DeveloperBuilder
                with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with ApmConnectorMockModule with SessionServiceMock {

    implicit val hc = HeaderCarrier()

    val confirmApisView = app.injector.instanceOf[ConfirmApisView]
    val turnOffApisMasterView = app.injector.instanceOf[TurnOffApisMasterView]

    val controller = new SR20(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      confirmApisView,
      turnOffApisMasterView,
      ApmConnectorMock.aMock
    )
    val appName: String = "app"
    val apiVersion = ApiVersion("version")

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)

    val apiIdentifier1 = ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))
    val apiIdentifier2 = ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))

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

    val testAPISubscriptionStatus2 = APISubscriptionStatus(
      "test-api-2",
      "api-example-microservice",
      apiIdentifier2.context,
      ApiVersionDefinition(apiIdentifier2.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val singleApi: Map[ApiContext,ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
    )

    val multipleApis: Map[ApiContext,ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE)),
      ApiContext("test-api-context-2") ->
        ApiData("test-api-context-2", "test-api-context-2", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
    )
  }

  "confirmApiSubscription" should {

    "render the confirm apis view containing 1 upliftable api as there is only 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptionsFlow(Map(apiIdentifier1 -> true))
      val sessionSubscriptions = "subscriptions" -> ApiSubscriptionsFlow.toSessionString(testFlow)

      val applicationRequest = ApplicationRequest(
        sampleApp,
        sampleApp.deployedTo,
        List(testAPISubscriptionStatus1),
        Map.empty,
        loggedInDeveloper.email.asAdministratorCollaborator.role,
        loggedInDeveloper,
        new MessagesRequest(loggedInRequest.withCSRFToken.withSession(sessionSubscriptions), new DefaultMessagesApi))

      fetchByApplicationIdReturns(appId, sampleApp)
      givenApplicationAction(ApplicationWithSubscriptionData(sampleApp,
        asSubscriptions(List(testAPISubscriptionStatus1)),
        asFields(List.empty)),
        loggedInDeveloper,
        List(testAPISubscriptionStatus1))

      ApmConnectorMock.FetchAllApis.willReturn(singleApi)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.confirmApiSubscriptionsPage(appId)(applicationRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("test-api-1 - 1.0")
    }

    "render the confirm apis view without the 'Change my API subscriptions' link as there is only 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptionsFlow(Map(apiIdentifier1 -> true))
      val sessionSubscriptions = "subscriptions" -> ApiSubscriptionsFlow.toSessionString(testFlow)

      val applicationRequest = ApplicationRequest(
        sampleApp,
        sampleApp.deployedTo,
        List(testAPISubscriptionStatus1),
        Map.empty,
        loggedInDeveloper.email.asAdministratorCollaborator.role,
        loggedInDeveloper,
        new MessagesRequest(loggedInRequest.withCSRFToken.withSession(sessionSubscriptions), new DefaultMessagesApi))

      fetchByApplicationIdReturns(appId, sampleApp)
      givenApplicationAction(ApplicationWithSubscriptionData(sampleApp,
        asSubscriptions(List(testAPISubscriptionStatus1)),
        asFields(List.empty)),
        loggedInDeveloper,
        List(testAPISubscriptionStatus1))

      ApmConnectorMock.FetchAllApis.willReturn(singleApi)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.confirmApiSubscriptionsPage(appId)(applicationRequest)

      status(result) shouldBe OK

      contentAsString(result) should not include("Change my API subscriptions")
    }

    "render the confirm apis view with the 'Change my API subscriptions' link as there is more than 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptionsFlow(Map(apiIdentifier1 -> true, apiIdentifier2 -> true))
      val sessionSubscriptions = "subscriptions" -> ApiSubscriptionsFlow.toSessionString(testFlow)

      val applicationRequest = ApplicationRequest(
        sampleApp,
        sampleApp.deployedTo,
        List(testAPISubscriptionStatus1, testAPISubscriptionStatus2),
        Map.empty,
        loggedInDeveloper.email.asAdministratorCollaborator.role,
        loggedInDeveloper,
        new MessagesRequest(loggedInRequest.withCSRFToken.withSession(sessionSubscriptions), new DefaultMessagesApi))

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0")),
        ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))
      )

      fetchByApplicationIdReturns(appId, sampleApp)
      givenApplicationAction(ApplicationWithSubscriptionData(sampleApp,
        asSubscriptions(List(testAPISubscriptionStatus1, testAPISubscriptionStatus2)),
        asFields(List.empty)), loggedInDeveloper,
        List(testAPISubscriptionStatus1, testAPISubscriptionStatus2))

      ApmConnectorMock.FetchAllApis.willReturn(multipleApis)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)

      private val result = controller.confirmApiSubscriptionsPage(appId)(applicationRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Change my API subscriptions")
    }
  }
}
