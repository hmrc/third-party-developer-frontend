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

import builder.{DeveloperBuilder, SubscriptionsBuilder}
import domain.models.apidefinitions._
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import mocks.connector.ApmConnectorMockModule
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.html.{ConfirmApisView, TurnOffApisMasterView}

import scala.concurrent.ExecutionContext.Implicits.global
import domain.models.applications.Application
import domain.models.applications.ApplicationWithSubscriptionData
import builder._

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
  }

  "confirmApiSubscription" should {

    "render the confirm apis view containing 1 upliftable api as there is only 1 upliftable api available to the application" in new Setup {

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))
      )

      val apis: Map[ApiContext,ApiData] = Map(
        ApiContext("test-api-context-1") ->
          ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
      )

      val application = mock[Application]

      fetchByApplicationIdReturns(appId, application)
      givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(List.empty), asFields(List.empty)), loggedInDeveloper, List.empty)

      ApmConnectorMock.FetchAllApis.willReturn(apis)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("test-api-context-1 - 1.0")
    }

    "render the confirm apis view without the 'Change my API subscriptions' link as there is only 1 upliftable api available to the application" in new Setup {

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))
      )

      val apis: Map[ApiContext,ApiData] = Map(
        ApiContext("test-api-context-1") ->
          ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
      )

      ApmConnectorMock.FetchAllApis.willReturn(apis)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should not include("Change my API subscriptions")
    }

    "render the confirm apis view with the 'Change my API subscriptions' link as there is more than 1 upliftable api available to the application" in new Setup {

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0")),
        ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))
      )

      val apis: Map[ApiContext,ApiData] = Map(
        ApiContext("test-api-context-1") ->
          ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE)),
        ApiContext("test-api-context-2") ->
          ApiData("test-api-context-2", "test-api-context-2", true, Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
      )

      ApmConnectorMock.FetchAllApis.willReturn(apis)
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Change my API subscriptions")
    }
  }
}
