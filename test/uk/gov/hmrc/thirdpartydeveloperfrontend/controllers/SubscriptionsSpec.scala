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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import views.helper.EnvironmentNameService
import views.html.include.ChangeSubscriptionConfirmationView
import views.html.{AddAppSubscriptionsView, ManageSubscriptionsView, SubscribeRequestSubmittedView, UnsubscribeRequestSubmittedView}

import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, CheckInformation, ClientSecret, ClientSecretResponse}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApiContext, ApiIdentifier, ApiVersionNbr}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock, SubscriptionsServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class SubscriptionsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubscriptionTestSugar
    with FixedClock {

  val apiName       = "api-1"
  val apiVersion    = ApiVersionNbr("1.0")
  val apiContext    = ApiContext("Context")
  val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  val displayStatus = "Status"

  trait Setup
      extends ApplicationServiceMock
      with SessionServiceMock
      with ApplicationActionServiceMock
      with SubscriptionsServiceMockModule
      with LocalUserIdTracker
      with UserBuilder
      with SampleUserSession
      with SampleApplications
      with SubscriptionTestHelper {
    val manageSubscriptionsView                                 = app.injector.instanceOf[ManageSubscriptionsView]
    val addAppSubscriptionsView                                 = app.injector.instanceOf[AddAppSubscriptionsView]
    val changeSubscriptionConfirmationView                      = app.injector.instanceOf[ChangeSubscriptionConfirmationView]
    val unsubscribeRequestSubmittedView                         = app.injector.instanceOf[UnsubscribeRequestSubmittedView]
    val subscribeRequestSubmittedView                           = app.injector.instanceOf[SubscribeRequestSubmittedView]
    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

    val tokens: ApplicationToken = ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

    val underTest = new SubscriptionsController(
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      SubscriptionsServiceMock.aMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      manageSubscriptionsView,
      addAppSubscriptionsView,
      changeSubscriptionConfirmationView,
      unsubscribeRequestSubmittedView,
      subscribeRequestSubmittedView,
      FraudPreventionConfig(true, List.empty, "some/uri")
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, userSession)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    fetchByApplicationIdReturns(developerApplication.id, developerApplication)

    val subsData = List(
      exampleSubscriptionWithFields("api1", 1),
      exampleSubscriptionWithFields("api2", 1)
    )

    val requestor = Actors.AppCollaborator("Bob".toLaxEmail)

    val sessionParams                                         = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "manage subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      val ropcApplication: ApplicationWithCollaborators = ropcApp.withCollaborators(userSession.developer.email.asDeveloperCollaborator)

      fetchByApplicationIdReturns(ropcApplication)
      givenApplicationAction(ropcApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(ropcApplication.id))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      val privilegedApplication: ApplicationWithCollaborators = privilegedApp.withCollaborators(userSession.developer.email.asDeveloperCollaborator)

      fetchByApplicationIdReturns(privilegedApplication)
      givenApplicationAction(privilegedApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(privilegedApplication.id))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(developerApplication.id))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Manage API subscriptions - HMRC Developer Hub - GOV.UK"
    }
  }

  "add app subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      val ropcApplication: ApplicationWithCollaborators = ropcApp.withCollaborators(userSession.developer.email.asDeveloperCollaborator)

      fetchByApplicationIdReturns(ropcApplication)
      givenApplicationAction(ropcApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(ropcApplication.id))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      val privilegedApplication: ApplicationWithCollaborators = privilegedApp.withCollaborators(userSession.developer.email.asDeveloperCollaborator)

      fetchByApplicationIdReturns(privilegedApplication)
      givenApplicationAction(privilegedApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(privilegedApplication.id))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
    }

    "return the subscriptions page for a developer on a standard app in the Sandbox environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in the sandbox")
    }

    "return the subscriptions page for a developer on a standard app in the Development environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in development")
    }
  }

  // "changeApiSubscription" should {
  //   def forbiddenSubscriptionChange(app: => ApplicationWithCollaborators): Unit = {
  //     "return 400 Bad Request" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

  //       fetchByApplicationIdReturns(appId, app)
  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe BAD_REQUEST
  //     }
  //   }

  //   def allowedSubscriptionChange(app: => ApplicationWithCollaborators): Unit = {
  //     "successfully subscribe to an API and redirect" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

  //       fetchByApplicationIdReturns(appId, app)
  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.SubscribeToApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe SEE_OTHER
  //       redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

  //       verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }

  //     "successfully unsubscribe from an API and redirect" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

  //       fetchByApplicationIdReturns(appId, app)
  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe SEE_OTHER
  //       redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

  //       verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }

  //     "return a Bad Request without changing the subscription when requesting a change to the subscription when the form is invalid" in new Setup {
  //       val redirectTo                                       = "APPLICATION_CHECK_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

  //       fetchByApplicationIdReturns(appId, app)
  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe BAD_REQUEST

  //       verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }
  //   }

  //   def allowedSubscriptionChangeWithCheckUpdate(app: => ApplicationWithCollaborators): Unit = {
  //     "successfully subscribe to an API, update the check information and redirect" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "true")

  //       fetchByApplicationIdReturns(appId, app)
  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.SubscribeToApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe SEE_OTHER
  //       redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

  //       verify(applicationServiceMock).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }

  //     "successfully unsubscribe from an API, update the check information and redirect" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("subscribed" -> "false")

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe SEE_OTHER
  //       redirectLocation(result) shouldBe Some(routes.Details.details(app.id).url)

  //       verify(applicationServiceMock).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }

  //     "return a Bad Request without changing the subscription or check information when requesting a change to the subscription when the form is invalid" in new Setup {
  //       val redirectTo                                       = "APPLICATION_CHECK_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(app, apiIdentifier)
  //       givenUpdateCheckInformationSucceeds(app)

  //       val result = underTest.changeApiSubscription(app.id, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe BAD_REQUEST

  //       verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
  //     }
  //   }

  //   // "an administrator attempts to change a submitted-for-checking production application" in { behave like forbiddenSubscriptionChange(adminSubmittedProductionApplication) }
  //   // "an administrator attempts to change a created production application" in new Setup { behave like allowedSubscriptionChangeWithCheckUpdate(adminCreatedProductionApplication) }
  //   // "an administrator attempts to change a submitted-for-checking sandbox application" in new Setup { behave like allowedSubscriptionChange(adminSubmittedSandboxApplication) }
  //   // "an administrator attempts to change a created sandbox application" in new Setup { behave like allowedSubscriptionChange(adminCreatedSandboxApplication) }
  //   // "a developer attempts to change a submitted-for-checking production application" in new Setup { behave like forbiddenSubscriptionChange(developerSubmittedProductionApplication) }
  //   // "a developer attempts to change a created production application" in new Setup { behave like allowedSubscriptionChangeWithCheckUpdate(developerCreatedProductionApplication) }
  //   // "a developer attempts to change a submitted-for-checking sandbox application" in new Setup { behave like allowedSubscriptionChange(developerSubmittedSandboxApplication) }
  //   // "a developer attempts to change a created sandbox application" in new Setup { behave like allowedSubscriptionChange(devloperCreatedSandboxApplication) }
  // }

  // "changeLockedApiSubscription" should {
  //   def checkBadLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators, expectedStatus: Int): Unit = {
  //     s"return $expectedStatus" in new Setup {
  //       val redirectTo                                   = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
  //         "GET",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

  //       val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe expectedStatus

  //       SubscriptionsServiceMock.IsSubscribedToApi.verifyNotCalled()
  //     }
  //   }

  //   def forbiddenLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     checkBadLockedSubscriptionChangeRequest(app, FORBIDDEN)
  //   }

  //   def badLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     checkBadLockedSubscriptionChangeRequest(app, BAD_REQUEST)
  //   }

  //   def allowedLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     "render the subscribe to locked subscription page when changing an unsubscribed api" in new Setup {
  //       val redirectTo                                   = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
  //         "GET",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isFalse(app.id, apiIdentifier)

  //       val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe OK
  //       contentAsString(result) should include(s"Are you sure you want to request to subscribe to $apiName ${apiVersion.value}?")
  //     }

  //     "render the unsubscribe from locked subscription page when changing a subscribed api" in new Setup {
  //       val redirectTo                                   = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
  //         "GET",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId)

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

  //       val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe OK
  //       contentAsString(result) should include(s"Are you sure you want to request to unsubscribe from $apiName ${apiVersion.value}?")
  //     }
  //   }

  //   // "an administrator attempts to change a submitted-for-checking production application" in new Setup {
  //   //   behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication)
  //   // }
  //   // "an administrator attempts to change a created production application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
  //   // "an administrator attempts to change a submitted-for-checking sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
  //   // "an administrator attempts to change a created sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
  //   // "a developer attempts to change a submitted-for-checking production application" in new Setup {
  //   //   behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication)
  //   // }
  //   // "a developer attempts to change a created production application" in new Setup { behave like badLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
  //   // "a developer attempts to change a submitted-for-checking sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
  //   // "a developer attempts to change a created sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }
  // }

  // "changeLockedApiSubscriptionAction" should {
  //   def forbiddenLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     "return 403 Forbidden" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)

  //       val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe FORBIDDEN
  //     }
  //   }

  //   def badLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     "return 400 Bad Request" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

  //       val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe BAD_REQUEST

  //       verify(underTest.subscriptionsService, never).isSubscribedToApi(eqTo(app.id), eqTo(apiIdentifier))(*)
  //     }
  //   }

  //   def allowedLockedSubscriptionChangeRequest(app: => ApplicationWithCollaborators): Unit = {
  //     "successfully request to subscribe to the api" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isFalse(app.id, apiIdentifier)
  //       SubscriptionsServiceMock.RequestApiSubscription.succeedsFor(userSession, app, apiName, apiVersion)

  //       val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe OK

  //       contentAsString(result) should include(s"success-request-subscribe-text")
  //     }

  //     "successfully request to unsubscribe from the api" in new Setup {
  //       val redirectTo                                       = "MANAGE_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody("confirm" -> "true")

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)
  //       SubscriptionsServiceMock.RequestApiUnsubscribe.succeedsFor(userSession, app, apiName, apiVersion)

  //       val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe OK

  //       contentAsString(result) should include(s"success-request-unsubscribe-text")
  //     }

  //     "return a Bad Request without requesting a change to the subscription when the form is invalid" in new Setup {
  //       val redirectTo                                       = "APPLICATION_CHECK_PAGE"
  //       val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
  //         "POST",
  //         s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext.value}&version=${apiVersion.value}&redirectTo=$redirectTo"
  //       ).withCSRFToken.withLoggedIn(underTest, implicitly)(sessionId).withFormUrlEncodedBody()

  //       fetchByApplicationIdReturns(appId, app)

  //       givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
  //       SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

  //       val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

  //       status(result) shouldBe BAD_REQUEST
  //     }
  //   }

  //   // "an administrator attempts to change a submitted-for-checking production application" in new Setup {
  //   //   behave like allowedLockedSubscriptionChangeRequest(adminSubmittedProductionApplication)
  //   // }
  //   // "an administrator attempts to change a created production application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminCreatedProductionApplication) }
  //   // "an administrator attempts to change a submitted-for-checking sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminSubmittedSandboxApplication) }
  //   // "an administrator attempts to change a created sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(adminCreatedSandboxApplication) }
  //   // "a developer attempts to change a submitted-for-checking production application" in new Setup {
  //   //   behave like forbiddenLockedSubscriptionChangeRequest(developerSubmittedProductionApplication)
  //   // }
  //   // "a developer attempts to change a created production application" in new Setup { behave like badLockedSubscriptionChangeRequest(developerCreatedProductionApplication) }
  //   // "a developer attempts to change a submitted-for-checking sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(developerSubmittedSandboxApplication) }
  //   // "a developer attempts to change a created sandbox application" in new Setup { behave like badLockedSubscriptionChangeRequest(devloperCreatedSandboxApplication) }
  // }

  "Authorization" should {
    val apiContext    = ApiContext("api/test")
    val apiVersion    = ApiVersionNbr("1.0")
    val apiAccessType = "PUBLIC"

    "unauthorized user should get 404 Not Found on unsubscribe to an API" in new Setup {
      val alteredActiveApplication = developerApplication.copy(collaborators = Set("randomEmail".toLaxEmail.asAdministratorCollaborator))

      when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(userSession)))
      fetchByApplicationIdReturns(appId, alteredActiveApplication)
      givenApplicationAction(alteredActiveApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)

      val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"developer/applications/$appId/subscribe?context=${apiContext.value}&version=${apiVersion.value}&accessType=$apiAccessType&tab=subscriptions")
          .withLoggedIn(underTest, implicitly)(sessionId)

      val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, apiAccessType)(request)
      status(result) shouldBe NOT_FOUND
      verify(applicationServiceMock, never).updateCheckInformation(eqTo(alteredActiveApplication), eqTo(CheckInformation()))(*)
    }
  }

  private def titleOf(result: Future[Result]) = {
    val titleRegEx = """<title[^>]*>(.*)</title>""".r
    val title      = titleRegEx.findFirstMatchIn(contentAsString(result)).map(_.group(1))
    title.isDefined shouldBe true
    title.get
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, instant)

}
