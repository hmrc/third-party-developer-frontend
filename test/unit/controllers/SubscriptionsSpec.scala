/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.controllers

import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain._
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._

class SubscriptionsSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val apiName = "api-1"
  val apiVersion = "1.0"
  val apiContext = "Context"
  val displayStatus = "Status"
  val clientId = "clientId123"

  val developer = Developer("third.party.developer@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInDeveloper = DeveloperSession(session)

  val anApplication = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInDeveloper.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInDeveloper.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val activeApplication = anApplication

  val activeDeveloperApplication = anApplication.copy(collaborators = Set(Collaborator(loggedInDeveloper.email, Role.DEVELOPER)))

  val ropcApplication = anApplication.copy(access = ROPC())

  val privilegedApplication = anApplication.copy(access = Privileged())

  val newApplication = anApplication.copy(state = ApplicationState.testing)

  val newSandboxApplication = anApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val adminApplication = anApplication.copy(collaborators = Set(Collaborator(loggedInDeveloper.email, Role.ADMINISTRATOR)))
  val developerApplication = anApplication.copy(collaborators = Set(Collaborator(loggedInDeveloper.email, Role.DEVELOPER)))

  val adminSubmittedProductionApplication = adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedProductionApplication = adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val adminSubmittedSandboxApplication = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedSandboxApplication = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)
  val developerSubmittedProductionApplication = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val developerCreatedProductionApplication = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val developerSubmittedSandboxApplication = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val devloperCreatedSandboxApplication = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new Subscriptions(
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mock[SubscriptionFieldsService],
      mock[SubscriptionsService],
      mock[ApiSubscriptionsHelper],
      mock[ApplicationService],
      mock[SessionService],
      mockErrorHandler,
      messagesApi
    )

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
    given(underTest.applicationService.fetchByApplicationId(mockEq(activeApplication.id))(any[HeaderCarrier])).willReturn(successful(activeApplication))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(ropcApplication))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(privilegedApplication))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe FORBIDDEN
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.apiSubscriptionsHelper.fetchPageDataFor(mockEq(activeApplication))(any[HeaderCarrier])).willReturn(successful(PageData(activeApplication, ApplicationTokens(EnvironmentToken("", Nil, "")), None)))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe OK
      titleOf(result) shouldBe "Manage API subscriptions - HMRC Developer Hub - GOV.UK"
    }
  }

  "subscriptions2" should {
    "return the ROPC page for a ROPC app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(ropcApplication))
      val result = await(addToken(underTest.subscriptions2(appId, Environment.PRODUCTION))(loggedInRequest))
      status(result) shouldBe FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(privilegedApplication))
      val result = await(addToken(underTest.subscriptions2(appId, Environment.PRODUCTION))(loggedInRequest))
      status(result) shouldBe FORBIDDEN
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.apiSubscriptionsHelper.fetchPageDataFor(mockEq(activeApplication))(any[HeaderCarrier])).willReturn(successful(PageData(activeApplication, ApplicationTokens(EnvironmentToken("", Nil, "")), None)))
      val result = await(addToken(underTest.subscriptions2(appId, Environment.PRODUCTION))(loggedInRequest))
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
    }
  }

  "changeApiSubscription" when {
    def forbiddenSubscriptionChange(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe FORBIDDEN
      }
    }

    def allowedSubscriptionChange(app: => Application) = {
      "successfully subscribe to an API and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(underTest.applicationService).subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "successfully unsubscribe from an API and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(underTest.applicationService).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "return a Bad Request without changing the subscription when requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }
    }

    def allowedSubscriptionChangeWithCheckUpdate(app: => Application) = {
      "successfully subscribe to an API, update the check information and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(underTest.applicationService).subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "successfully unsubscribe from an API, update the check information and redirect" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

        verify(underTest.applicationService).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "return a Bad Request without changing the subscription or check information when requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like allowedSubscriptionChangeWithCheckUpdate(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(developerSubmittedProductionApplication) }
    "a developer attempts to change a created production application" should { behave like allowedSubscriptionChangeWithCheckUpdate(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscription" when {
    def forbiddenLockedSubscriptionChangeRequest(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe FORBIDDEN

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def badLockedSubscriptionChangeRequest(app: => Application) = {
      "return 400 Bad Request" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def allowedLockedSubscriptionChangeRequest(app: => Application) = {
      "render the subscribe to locked subscription page when changing an unsubscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(false))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe OK
        bodyOf(result) should include(s"Are you sure you want to request to subscribe to $apiName $apiVersion?")

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }

      "render the unsubscribe from locked subscription page when changing a subscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe OK
        bodyOf(result) should include(s"Are you sure you want to request to unsubscribe from $apiName $apiVersion?")

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication) }
    "a developer attempts to change a created production application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like forbiddenLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscriptionAction" when {
    def forbiddenLockedSubscriptionChangeRequest(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe FORBIDDEN

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def badLockedSubscriptionChangeRequest(app: => Application) = {
      "return 400 Bad Request" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def allowedLockedSubscriptionChangeRequest(app: => Application) = {
      "successfully request to subscribe to the api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(false))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe OK

        bodyOf(result) should include(s"success-request-subscribe-text")

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])

      }

      "successfully request to unsubscribe from the api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe OK

        bodyOf(result) should include(s"success-request-unsubscribe-text")

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
      }

      "return a Bad Request without requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))


        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication) }
    "a developer attempts to change a created production application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like forbiddenLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }

  }

  "Authorization" should {
    val apiName = "api-1"
    val apiContext = "api/test"
    val apiVersion = "1.0"
    val apiStatus = "STABLE"
    val apiAccessType = "PUBLIC"

    "unauthorized user should get 404 Not Found on unsubscribe to an API" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier]))
        .willReturn(successful(activeApplication.copy(collaborators = Set(Collaborator("randomEmail", Role.ADMINISTRATOR)))))

      val request = FakeRequest("GET",
        s"developer/applications/$appId/subscribe?context=$apiContext&version=$apiVersion&accessType=$apiAccessType&tab=subscriptions"
      ).withLoggedIn(underTest, implicitly)(sessionId)

      val result = await(underTest.changeApiSubscription(appId, apiContext, apiVersion, apiAccessType)(request))
      status(result) shouldBe NOT_FOUND
      verify(underTest.applicationService, never).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
    }
  }

   private def titleOf(result: Result) = {
    val titleRegEx = """<title[^>]*>(.*)</title>""".r
    val title = titleRegEx.findFirstMatchIn(bodyOf(result)).map(_.group(1))
    title.isDefined shouldBe true
    title.get
  }

  private def givenTheApplicationExistWithUserRole(applicationService: ApplicationService, appId: String, userRole: Role, state: ApplicationState = ApplicationState.testing) = {
    val application = Application(appId, clientId, "app", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION,
      collaborators = Set(Collaborator(loggedInDeveloper.email, userRole)), state = state)

    given(applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
    given(applicationService.fetchCredentials(mockEq(appId))(any[HeaderCarrier])).willReturn(tokens)
    given(applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty)
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
