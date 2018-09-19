/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ApplicationConfig
import connectors.{DeskproConnector, ThirdPartyDeveloperConnector}
import domain.SubscriptionRedirect.{APPLICATION_CHECK_PAGE, MANAGE_PAGE}
import controllers._
import domain.ApiSubscriptionFields.Fields
import domain._
import org.joda.time.DateTimeZone
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future._

class SubscriptionsSpec extends UnitSpec with MockitoSugar with WithFakeApplication with ScalaFutures with SubscriptionTestHelperSugar with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val appId = "1234"
  val apiName = "api-1"
  val apiVersion = "1.0"
  val apiContext = "Context"
  val displayStatus = "Status"
  val clientId = "clientId123"
  val loggedInDeveloper = Developer("third.party.developer@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInDeveloper)

  val anApplication = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
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
  val developerSubmittedProductionrApplication = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val developerCreatedProductionApplication = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val developerSubmittedSandboxApplication = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val devloperCreatedSandboxApplication = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new Subscriptions {
      override val sessionService = mock[SessionService]
      override val applicationService = mock[ApplicationService]
      override val developerConnector = mock[ThirdPartyDeveloperConnector]
      override val auditService = mock[AuditService]
      override val appConfig = mock[ApplicationConfig]
      override val subFieldsService = mock[SubscriptionFieldsService]
      override val subscriptionsService = mock[SubscriptionsService]
      override val apiSubscriptionsHelper = mock[ApiSubscriptionsHelper]
    }

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
    given(underTest.applicationService.fetchByApplicationId(mockEq(activeApplication.id))(any[HeaderCarrier])).willReturn(successful(activeApplication))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
  }

  "subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(ropcApplication))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a ROPC application")
    }

    "return the privileged page for a privileged app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(privilegedApplication))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a privileged application")
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.apiSubscriptionsHelper.fetchPageDataFor(mockEq(activeApplication))(any[HeaderCarrier])).willReturn(successful(PageData(activeApplication, ApplicationTokens(EnvironmentToken("", Nil, "")), None)))
      val result = await(addToken(underTest.subscriptions(appId))(loggedInRequest))
      status(result) shouldBe OK
      titleOf(result) shouldBe "Manage API subscriptions - HMRC Developer Hub - GOV.UK"
    }
  }

  "subscribeToApi" should {
    "successfully request subscribing to an API and be taken to the confirmation page" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.appConfig.title).willReturn("Test Title")

      val request = FakeRequest("GET", s"developer/applications/$appId/subscribe?name=$apiName&context=$apiContext&version=$apiVersion&status=$displayStatus&accessType="
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)

      val result = await(underTest.subscribeToApi(appId, apiName, apiContext, apiVersion)(request))

      status(result) shouldBe OK
      titleOf(result) shouldBe "Confirm subscribe - HMRC Developer Hub - GOV.UK"
    }

    "successfully confirm subscribing to an API and redirect to the subscriptions page" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.appConfig.title).willReturn("Test Title")

      val request = FakeRequest("POST", s"developer/applications/$appId/subscribe?name=$apiName&context=$apiContext&version=$apiVersion&status=$displayStatus&subscriptionRedirect="
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)

      val result = await(underTest.subscribeToApi(appId, apiName, apiContext, apiVersion)(request))

      status(result) shouldBe OK
      titleOf(result) shouldBe "Confirm subscribe - HMRC Developer Hub - GOV.UK"
    }
  }

  "subscribeToApiAction" should {
    val redirectTo = "MANAGE_PAGE"

    "subscribe to an API and show confirmation page for an active production app" in {
      val mockDeskproConnector = mock[DeskproConnector]
      val mockSubscriptionsService = new SubscriptionsService {
        override val auditService: AuditService = mock[AuditService]
        override val deskproConnector: DeskproConnector = mockDeskproConnector
      }

      val underTest = new Subscriptions {
        override val sessionService = mock[SessionService]
        override val applicationService = mock[ApplicationService]
        override val developerConnector = mock[ThirdPartyDeveloperConnector]
        override val auditService = mock[AuditService]
        override val appConfig = mock[ApplicationConfig]
        override val subFieldsService = mock[SubscriptionFieldsService]
        override val subscriptionsService = mockSubscriptionsService
        override val apiSubscriptionsHelper = mock[ApiSubscriptionsHelper]
      }

      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.apiSubscriptionsHelper.roleForApplication(activeApplication, loggedInDeveloper.email)).willReturn(Role.ADMINISTRATOR)
      given(underTest.applicationService.subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
      given(underTest.applicationService.updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
      given(mockSubscriptionsService.deskproConnector.createTicket(any[DeskproTicket])(any[HeaderCarrier])).willReturn(successful(TicketCreated))

      val request = FakeRequest("POST",
        s"developer/applications/$appId/subscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)
        .withFormUrlEncodedBody("subscribeConfirm" -> "Yes")

      val result = await(underTest.subscribeToApiAction(appId, apiName, apiContext, apiVersion, redirectTo)(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Request submitted")
      bodyOf(result) should include("We will review your request and respond within 2 working days.")

      verify(mockDeskproConnector).createTicket(any[DeskproTicket])(any[HeaderCarrier])
    }
  }

  "unsubscribeFromApi" should {
    "redirect to the confirmation page" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.appConfig.title).willReturn("Test Title")

      val redirectTo = "MANAGE_PAGE"

      val request = FakeRequest("GET",
        s"developer/applications/$appId/unsubscribe?name=$apiName&context=$apiName&version=$apiVersion&redirectTo=$redirectTo"
      ).withSession(sessionParams: _*).withLoggedIn(underTest)(sessionId)

      val result = await(addToken(underTest.unsubscribeFromApi(appId, apiName, apiName, apiVersion, displayStatus))(request))

      status(result) shouldBe OK
      titleOf(result) shouldBe "Confirm unsubscribe - HMRC Developer Hub - GOV.UK"
    }
  }

  "unsubscribeFromApiAction" should {
    val redirectTo = "MANAGE_PAGE"

    "unsubscribe from an API and show confirmation page for an active production app" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(activeApplication))
      given(underTest.apiSubscriptionsHelper.roleForApplication(activeApplication, loggedInDeveloper.email)).willReturn(Role.ADMINISTRATOR)
      given(underTest.applicationService.unsubscribeFromApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
      given(underTest.applicationService.updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
      given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(activeApplication), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(TicketCreated))

      val request = FakeRequest("POST",
        s"developer/applications/$appId/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)
        .withFormUrlEncodedBody("unsubscribeConfirm" -> "Yes")

      val result = await(underTest.unsubscribeFromApiAction(appId, apiName, apiContext, apiVersion, redirectTo)(request))

      status(result) shouldBe OK
      bodyOf(result) should include("Request submitted")
      bodyOf(result) should include("We will review your request and respond within 2 working days.")
    }

    "unsubscribe from an API and redirect to the subscriptions page for sandbox app" in new Setup {

      val sandboxApp = activeApplication.copy(deployedTo = Environment.SANDBOX)

      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(sandboxApp))

      given(underTest.applicationService.unsubscribeFromApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      val request = FakeRequest("POST",
        s"developer/applications/$appId/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=${SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString}"
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)
        .withFormUrlEncodedBody("unsubscribeConfirm" -> "Yes")

      val result = await(underTest.unsubscribeFromApiAction(appId, apiName, apiContext, apiVersion, SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString)(request))

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/subscriptions")
      status(result) shouldBe SEE_OTHER
      verify(underTest.applicationService).unsubscribeFromApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      verify(underTest.applicationService, never()).updateCheckInformation(any[String], any[CheckInformation])(any[HeaderCarrier])
    }

    "unsubscribe from an API and redirect to the subscriptions page in application check journey" in new Setup {
      val redirectTo = "APPLICATION_CHECK_PAGE"

      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(successful(newApplication))
      given(underTest.apiSubscriptionsHelper.roleForApplication(newApplication, loggedInDeveloper.email)).willReturn(Role.ADMINISTRATOR)
      given(underTest.applicationService.updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      given(underTest.applicationService.unsubscribeFromApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      val request = FakeRequest("POST",
        s"developer/applications/$appId/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest)(sessionId)
        .withFormUrlEncodedBody("unsubscribeConfirm" -> "Yes")

      val result = await(underTest.unsubscribeFromApiAction(appId, apiName, apiContext, apiVersion, redirectTo)(request))

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check/subscriptions")
      status(result) shouldBe SEE_OTHER
    }
  }

  "changeApiSubscription" when {
    def forbiddenSubscriptionChange(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))

        val result = await(underTest.changeApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe FORBIDDEN
      }
    }

    def allowedSubscriptionChange(app: => Application) = {
      "successfully subscribe to an API, update the check information and redirect to the subscriptions page" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageApplications.editApplication(app.id, None).url)

        verify(underTest.applicationService).subscribeToApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "successfully unsubscribe from an API, update the check information and redirect to the subscriptions page" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageApplications.editApplication(app.id, None).url)

        verify(underTest.applicationService).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "return a Bad Request without changing the subscription of check information when requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody()

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
        given(underTest.applicationService.updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

        val result = await(underTest.changeApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).unsubscribeFromApi(mockEq(app.id), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(app.id), any[CheckInformation])(any[HeaderCarrier])
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like allowedSubscriptionChange(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(developerSubmittedProductionrApplication) }
    "a developer attempts to change a created production application" should { behave like allowedSubscriptionChange(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like allowedSubscriptionChange(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like allowedSubscriptionChange(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscription" when {
    def forbiddenLockedSubscriptionChange(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

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
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def allowedLockedSubscriptionChange(app: => Application) = {
      "render the subscribe to locked subscription page when changing an unsubscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest(
          "GET", s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

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
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe OK
        bodyOf(result) should include(s"Are you sure you want to request to unsubscribe from $apiName $apiVersion?")

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChange(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenLockedSubscriptionChange(developerSubmittedProductionrApplication) }
    "a developer attempts to change a created production application" should { behave like forbiddenLockedSubscriptionChange(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like forbiddenLockedSubscriptionChange(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like forbiddenLockedSubscriptionChange(devloperCreatedSandboxApplication) }
  }

  "changeLockedApiSubscriptionAction" when {
    def forbiddenLockedSubscriptionChange(app: => Application) = {
      "return 403 Forbidden" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

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
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService, never).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
      }
    }

    def allowedLockedSubscriptionChange(app: => Application) = {
      "successfully request to subscribe to the api and redirect when confirming a change to an unsubscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(false))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageApplications.editApplication(app.id, None).url)

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])

      }

      "successfully request to unsubscribe from the api and redirect when confirming a change to a subscribed api" in new Setup {
        val redirectTo = "MANAGE_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))

        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageApplications.editApplication(app.id, None).url)

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
      }

      "successfully redirect to the redirectTo page without requesting a change to the subscription when not confirming a change" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody("confirm" -> "false")

        given(underTest.applicationService.fetchByApplicationId(mockEq(app.id))(any[HeaderCarrier])).willReturn(successful(app))
        given(underTest.applicationService.isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(true))
        given(underTest.subscriptionsService.requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))
        given(underTest.subscriptionsService.requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])).willReturn(successful(mock[TicketResult]))


        val result = await(underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ApplicationCheck.apiSubscriptionsPage(app.id).url)

        verify(underTest.applicationService).isSubscribedToApi(mockEq(app), mockEq(apiName), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiSubscription(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.subscriptionsService, never).requestApiUnsubscribe(mockEq(loggedInDeveloper), mockEq(app), mockEq(apiName), mockEq(apiVersion))(any[HeaderCarrier])
      }

      "return a Bad Request without requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo = "APPLICATION_CHECK_PAGE"
        val request = FakeRequest("POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody()

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

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChange(adminSubmittedProductionApplication) }
    "an administrator attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
    "an administrator attempts to change a submitted-for-checking sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
    "an administrator attempts to change a created sandbox application" should { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenLockedSubscriptionChange(developerSubmittedProductionrApplication) }
    "a developer attempts to change a created production application" should { behave like forbiddenLockedSubscriptionChange(developerCreatedProductionApplication) }
    "a developer attempts to change a submitted-for-checking sandbox application" should { behave like forbiddenLockedSubscriptionChange(developerSubmittedSandboxApplication) }
    "a developer attempts to change a created sandbox application" should { behave like forbiddenLockedSubscriptionChange(devloperCreatedSandboxApplication) }

  }

  "Authorization" should {
    val apiName = "api-1"
    val apiContext = "api/test"
    val apiVersion = "1.0"
    val apiStatus = "STABLE"
    val apiAccessType = "PUBLIC"

    "unauthorized user should get 404 on unsubscribe to an API" in new Setup {
      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier]))
        .willReturn(successful(activeApplication.copy(collaborators = Set(Collaborator("randomEmail", Role.ADMINISTRATOR)))))

      val request = FakeRequest("GET",
        s"developer/applications/$appId/subscribe?context=$apiContext&version=$apiVersion&accessType=$apiAccessType&tab=subscriptions"
      ).withLoggedIn(underTest)(sessionId)

      val result = await(underTest.unsubscribeFromApiAction(appId, apiName, apiContext, apiVersion, apiAccessType)(request))
      status(result) shouldBe 404
      verify(underTest.applicationService, never).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
    }
  }

  "subscribeApplicationToApi" should {
    val subscriptionFields = ApiSubscriptionFields.fields("field1" -> "value1", "field2" -> "value2")

    lazy val validForm = Seq(
      "fields[0].name" -> "field1",
      "fields[0].value" -> "value1",
      "fields[0].description" -> "desc1",
      "fields[0].hint" -> "hint1",
      "fields[0].type" -> "STRING",
      "fields[1].name" -> "field2",
      "fields[1].value" -> "value2",
      "fields[1].description" -> "desc0",
      "fields[1].hint" -> "hint0",
      "fields[1].type" -> "STRING"
    )

    def givenApplicationCanBeUpdatedUsingController(controller: Subscriptions, application: Application) = {
      given(controller.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      given(controller.subFieldsService.saveFieldValues(any[String], any[String], any[String], any[ApiSubscriptionFields.Fields])(any[HeaderCarrier]))
        .willReturn(successful(HttpResponse(OK)))
      given(controller.applicationService.subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
      given(controller.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
      given(controller.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier]))
        .willReturn(Seq(
          APISubscriptionStatus("api1", "service1", apiContext + "1", APIVersion(apiVersion, APIStatus.STABLE), subscribed = true, requiresTrust = false, None),
          APISubscriptionStatus("api1", "service1", apiContext, APIVersion(apiVersion, APIStatus.STABLE), subscribed = false, requiresTrust = false, None)
        ))
        .willReturn(Seq(APISubscriptionStatus("api1", "service1", apiContext, APIVersion(apiVersion, APIStatus.STABLE), subscribed = false, requiresTrust = false, None),
          APISubscriptionStatus("api1", "service1", apiContext, APIVersion(apiVersion, APIStatus.STABLE), subscribed = false, requiresTrust = false, None)))
      given(controller.applicationService.updateCheckInformation(mockEq(appId), any[CheckInformation])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))
    }

    def stubSuccessfulFieldUpdate(controller: Subscriptions, application: Application) = {
      given(controller.subFieldsService.saveFieldValues(any[String], any[String], any[String], any[ApiSubscriptionFields.Fields])(any[HeaderCarrier]))
        .willReturn(successful(HttpResponse(200)))
      given(controller.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
      given(controller.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier]))
        .willReturn(Seq(APISubscriptionStatus("api1", "service1", apiContext, APIVersion(apiVersion, APIStatus.STABLE), subscribed = true, requiresTrust = false, None)))
      given(controller.applicationService.updateCheckInformation(mockEq(application.id), any[CheckInformation])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))

    }

    "redirect when user is not logged in" in new Setup {
      val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, "fakeRedirect").apply(FakeRequest()))
      status(result) shouldBe 303
    }

    "with a logged in user subscribing to an api with subscription fields" should {
      "redirect to subscriptions section of application page" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=dodgyRedirect"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody(validForm: _*)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, "dodgyRedirect")(request))

        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId")
      }

      "save subscription field values" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/saveSubscriptionFields?subscriptionRedirect=${MANAGE_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody(validForm: _*)

        val result = await(underTest.saveSubscriptionFields(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId")

        verify(underTest.subFieldsService).saveFieldValues(mockEq(appId), mockEq(apiContext), mockEq(apiVersion), mockEq(subscriptionFields))(any[HeaderCarrier])
      }

      "subscribe to api in a production app" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody(validForm: _*)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        verify(underTest.applicationService).subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
      }

      "subscribe to api in a sandbox app" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, newSandboxApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody(validForm: _*)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        verify(underTest.applicationService).subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService, never()).updateCheckInformation(any[String], any[CheckInformation])(any[HeaderCarrier])
      }

      "redirect to subscriptions page as part of the application check journey" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${APPLICATION_CHECK_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId).withFormUrlEncodedBody(validForm: _*)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, APPLICATION_CHECK_PAGE.toString)(request))

        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check/subscriptions")
      }
    }

    "with a logged in user subscribing to an api without subscription fields" should {
      "redirect to subscriptions tab of application page" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=dodgyRedirect"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, "dodgyRedirect")(request))

        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId")
      }

      "redirect to subscriptions page in application check journey" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${APPLICATION_CHECK_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, APPLICATION_CHECK_PAGE.toString)(request))

        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check/subscriptions")
      }

      "not save subscription field values" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        verify(underTest.subFieldsService, never()).saveFieldValues(any[String], any[String], any[String], any[Fields])(any[HeaderCarrier])
      }

      "subscribe to api" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        ).withCSRFToken.withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        verify(underTest.applicationService).subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
      }
    }

    "with a logged in user making an AJAX request" should {
      "return Ok" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        )
          .withCSRFToken
          .withFormUrlEncodedBody(validForm: _*)
          .withHeaders("X-Requested-With" -> "XMLHttpRequest")
          .withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        status(result) shouldBe 200
        verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
      }

      "return number of subscriptions for api" in new Setup {
        givenApplicationCanBeUpdatedUsingController(underTest, activeApplication)
        stubSuccessfulFieldUpdate(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=${MANAGE_PAGE.toString}"
        )
          .withCSRFToken
          .withFormUrlEncodedBody(validForm: _*)
          .withHeaders("X-Requested-With" -> "XMLHttpRequest")
          .withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, MANAGE_PAGE.toString)(request))

        jsonBodyOf(result).shouldBe(Json.toJson(AjaxSubscriptionResponse(apiContext, APIGroup.API.toString, "1 subscription")))
        verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
      }
    }

    "with a logged in user updating subscription fields" should {
      "not subscribe to api" in new Setup {
        stubSuccessfulFieldUpdate(underTest, activeApplication)

        val request = FakeRequest(
          "POST", s"developer/applications/$appId/context/$apiContext/version/$apiVersion/subscriptions?subscriptionRedirect=dodgyRedirect"
        )
          .withCSRFToken
          .withFormUrlEncodedBody(validForm: _*)
          .withHeaders("X-Requested-With" -> "XMLHttpRequest")
          .withLoggedIn(underTest)(sessionId)

        val result = await(underTest.subscribeApplicationToApi(appId, apiContext, apiVersion, "dodgyRedirect")(request))

        verify(underTest.applicationService, never).subscribeToApi(mockEq(appId), mockEq(apiContext), mockEq(apiVersion))(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(CheckInformation()))(any[HeaderCarrier])
      }
    }
  }

  private def titleOf(result: Result) = {
    val titleRegEx = """<title[^>]*>(.*)</title>""".r
    val title = titleRegEx.findFirstMatchIn(bodyOf(result)).map(_.group(1))
    title.isDefined shouldBe true
    title.get
  }

  private def givenTheApplicationExistWithUserRole(applicationService: ApplicationService, appId: String, userRole: Role, state: ApplicationState = ApplicationState.testing) = {
    val application = Application(appId, clientId, "app", DateTimeUtils.now, Environment.PRODUCTION,
      collaborators = Set(Collaborator(loggedInDeveloper.email, userRole)), state = state)

    given(applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
    given(applicationService.fetchCredentials(mockEq(appId))(any[HeaderCarrier])).willReturn(tokens)
    given(applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty)
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
