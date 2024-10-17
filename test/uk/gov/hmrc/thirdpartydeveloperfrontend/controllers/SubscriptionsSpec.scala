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

import views.helper.EnvironmentNameService
import views.html.include.ChangeSubscriptionConfirmationView
import views.html.{AddAppSubscriptionsView, ManageSubscriptionsView, SubscribeRequestSubmittedView, UnsubscribeRequestSubmittedView}

import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApiContext, ApiIdentifier, ApiVersionNbr, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SubscriptionsServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class SubscriptionsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubscriptionTestSugar
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val apiName       = "api-1"
  val apiVersion    = ApiVersionNbr("1.0")
  val apiContext    = ApiContext("Context")
  val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  val displayStatus = "Status"

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubscriptionsServiceMockModule
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

    val developerApplication: ApplicationWithCollaborators = standardApp.withEnvironment(Environment.SANDBOX)

    fetchByApplicationIdReturns(developerApplication.id, developerApplication)

    val subsData = List(
      exampleSubscriptionWithFields(developerApplication.id, developerApplication.clientId)("api1", 1),
      exampleSubscriptionWithFields(developerApplication.id, developerApplication.clientId)("api2", 1)
    )

    val requestor = Actors.AppCollaborator("Bob".toLaxEmail)
    val appl      = developerApplication
    val appId     = appl.id
  }

  "manage subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      val ropcApplication: ApplicationWithCollaborators = ropcApp

      fetchByApplicationIdReturns(ropcApplication)
      givenApplicationAction(ropcApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(ropcApplication.id))(loggedInDevRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      val privilegedApplication: ApplicationWithCollaborators = privilegedApp

      fetchByApplicationIdReturns(privilegedApplication)
      givenApplicationAction(privilegedApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(privilegedApplication.id))(loggedInDevRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      val result = addToken(underTest.manageSubscriptions(developerApplication.id))(loggedInAdminRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Manage API subscriptions - HMRC Developer Hub - GOV.UK"
    }
  }

  "add app subscriptions" should {
    "return the ROPC page for a ROPC app" in new Setup {
      val ropcApplication: ApplicationWithCollaborators = ropcApp

      fetchByApplicationIdReturns(ropcApplication)
      givenApplicationAction(ropcApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(ropcApplication.id))(loggedInDevRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the privileged page for a privileged app" in new Setup {
      val privilegedApplication: ApplicationWithCollaborators = privilegedApp

      fetchByApplicationIdReturns(privilegedApplication)
      givenApplicationAction(privilegedApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(privilegedApplication.id))(loggedInDevRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the subscriptions page for a developer on a standard app" in new Setup {
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), adminSession, List.empty)
      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInAdminRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
    }

    "return the subscriptions page for a developer on a standard app in the Sandbox environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), adminSession, subsData)

      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInAdminRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in the sandbox")
    }

    "return the subscriptions page for a developer on a standard app in the Development environment" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      fetchByApplicationIdReturns(developerApplication)
      givenApplicationAction(developerApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), devSession, subsData)

      val result = addToken(underTest.addAppSubscriptions(developerApplication.id))(loggedInDevRequest)
      status(result) shouldBe OK
      titleOf(result) shouldBe "Which APIs do you want to use? - HMRC Developer Hub - GOV.UK"
      contentAsString(result) should include("Subscribe to the APIs you want to use in development")
    }
  }

  "changeApiSubscription" should {
    def forbiddenSubscriptionChange(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "return 400 Bad Request" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)

        val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST
      }
    }

    def allowedSubscriptionChange(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "successfully subscribe to an API and redirect" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.SubscribeToApi.succeeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(appId).url)

        verify(applicationServiceMock, never).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }
    }

    def allowedSubscriptionChangeWithCheckUpdate(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "successfully subscribe to an API, update the check information and redirect" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("subscribed" -> "true")

        fetchByApplicationIdReturns(appId, app)
        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.SubscribeToApi.succeeds(app, apiIdentifier)
        givenUpdateCheckInformationSucceeds(app)

        val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.Details.details(appId).url)

        verify(applicationServiceMock).updateCheckInformation(eqTo(app), any[CheckInformation])(*)
      }
    }

    "successfully unsubscribe from an API and redirect" in new Setup {
      val redirectTo                                       = "MANAGE_PAGE"
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
        "POST",
        s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest, implicitly)(devSession.sessionId).withFormUrlEncodedBody("subscribed" -> "false")

      fetchByApplicationIdReturns(appId, appl)
      givenApplicationAction(appl.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(appl, apiIdentifier)
      givenUpdateCheckInformationSucceeds(appl)

      val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appId).url)

      verify(applicationServiceMock, never).updateCheckInformation(eqTo(appl), any[CheckInformation])(*)
    }

    "return a Bad Request without changing the subscription when requesting a change to the subscription when the form is invalid" in new Setup {
      val redirectTo                                       = "APPLICATION_CHECK_PAGE"
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
        "POST",
        s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest, implicitly)(devSession.sessionId).withFormUrlEncodedBody()

      fetchByApplicationIdReturns(appId, appl)
      givenApplicationAction(appl.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(appl, apiIdentifier)
      givenUpdateCheckInformationSucceeds(appl)

      val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

      status(result) shouldBe BAD_REQUEST

      verify(applicationServiceMock, never).updateCheckInformation(eqTo(appl), any[CheckInformation])(*)
    }

    "successfully unsubscribe from an API, update the check information and redirect" in new Setup {
      val productionApp = standardApp.withId(applicationIdTwo).withState(appStateTesting)

      val redirectTo                                       = "MANAGE_PAGE"
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
        "POST",
        s"developer/applications/${applicationIdTwo}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest, implicitly)(adminSession.sessionId).withFormUrlEncodedBody("subscribed" -> "false")

      fetchByApplicationIdReturns(applicationIdTwo, productionApp)

      givenApplicationAction(productionApp.withSubscriptions(Set.empty).withFieldValues(Map.empty), adminSession, List.empty)
      SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(productionApp, apiIdentifier)
      givenUpdateCheckInformationSucceeds(productionApp)

      val result = underTest.changeApiSubscription(applicationIdTwo, apiContext, apiVersion, redirectTo)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(applicationIdTwo).url)

      verify(applicationServiceMock).updateCheckInformation(eqTo(productionApp), any[CheckInformation])(*)
    }

    "return a Bad Request without changing the subscription or check information when requesting a change to the subscription when the form is invalid" in new Setup {
      val redirectTo                                       = "APPLICATION_CHECK_PAGE"
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
        "POST",
        s"developer/applications/${appId}/change-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
      ).withCSRFToken.withLoggedIn(underTest, implicitly)(devSession.sessionId).withFormUrlEncodedBody()

      fetchByApplicationIdReturns(appId, appl)

      givenApplicationAction(appl.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
      SubscriptionsServiceMock.UnsubscribeFromApi.succeeds(appl, apiIdentifier)
      givenUpdateCheckInformationSucceeds(appl)

      val result = underTest.changeApiSubscription(appId, apiContext, apiVersion, redirectTo)(request)

      status(result) shouldBe BAD_REQUEST

      verify(applicationServiceMock, never).updateCheckInformation(eqTo(appl), any[CheckInformation])(*)
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(adminSession)(standardApp) }
    "an administrator attempts to change a created production application" should {
      behave like allowedSubscriptionChangeWithCheckUpdate(adminSession)(standardApp.withState(appStateTesting))
    }
    "an administrator attempts to change a submitted-for-checking sandbox application" should {
      behave like allowedSubscriptionChange(adminSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "an administrator attempts to change a created sandbox application" should {
      behave like allowedSubscriptionChange(adminSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenSubscriptionChange(devSession)(standardApp) }
    "a developer attempts to change a created production application" should {
      behave like allowedSubscriptionChangeWithCheckUpdate(devSession)(standardApp.withState(appStateTesting))
    }
    "a developer attempts to change a submitted-for-checking sandbox application" should {
      behave like allowedSubscriptionChange(devSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "a developer attempts to change a created sandbox application" should {
      behave like allowedSubscriptionChange(devSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }
  }

  "changeLockedApiSubscription" should {
    def checkBadLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators, expectedStatus: Int): Unit = {
      s"return $expectedStatus" in new Setup {
        val redirectTo                                   = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId)

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe expectedStatus

        SubscriptionsServiceMock.IsSubscribedToApi.verifyNotCalled()
      }
    }

    def forbiddenLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      checkBadLockedSubscriptionChangeRequest(userSession)(app, FORBIDDEN)
    }

    def badLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      checkBadLockedSubscriptionChangeRequest(userSession)(app, BAD_REQUEST)
    }

    def allowedLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "render the subscribe to locked subscription page when changing an unsubscribed api" in new Setup {
        val redirectTo                                   = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId)

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isFalse(app.id, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK
        contentAsString(result) should include(s"Are you sure you want to request to subscribe to $apiName ${apiVersion}?")
      }

      "render the unsubscribe from locked subscription page when changing a subscribed api" in new Setup {
        val redirectTo                                   = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
          "GET",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId)

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), devSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

        val result = underTest.changeLockedApiSubscription(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK
        contentAsString(result) should include(s"Are you sure you want to request to unsubscribe from $apiName ${apiVersion}?")
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChangeRequest(adminSession)(standardApp) }
    "an administrator attempts to change a created production application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withState(appStateTesting))
    }
    "an administrator attempts to change a submitted-for-checking sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "an administrator attempts to change a created sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }

    "a developer attempts to change a submitted-for-cecking production application" should { behave like forbiddenLockedSubscriptionChangeRequest(devSession)(standardApp) }
    "a developer attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withState(appStateTesting)) }
    "a developer attempts to change a submitted-for-checking sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "a developer attempts to change a created sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }
  }

  "changeLockedApiSubscriptionAction" should {
    def forbiddenLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "return 403 Forbidden" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe FORBIDDEN
      }
    }

    def badLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "return 400 Bad Request" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(appId, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

        val result = underTest.changeLockedApiSubscriptionAction(appId, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST

        verify(underTest.subscriptionsService, never).isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)
      }
    }

    def allowedLockedSubscriptionChangeRequest(userSession: UserSession)(app: => ApplicationWithCollaborators): Unit = {
      "successfully request to subscribe to the api" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isFalse(appId, apiIdentifier)
        SubscriptionsServiceMock.RequestApiSubscription.succeedsFor(userSession, app, apiName, apiVersion)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK

        contentAsString(result) should include(s"success-request-subscribe-text")
      }

      "successfully request to unsubscribe from the api" in new Setup {
        val redirectTo                                       = "MANAGE_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody("confirm" -> "true")

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isTrue(appId, apiIdentifier)
        SubscriptionsServiceMock.RequestApiUnsubscribe.succeedsFor(userSession, app, apiName, apiVersion)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe OK

        contentAsString(result) should include(s"success-request-unsubscribe-text")
      }

      "return a Bad Request without requesting a change to the subscription when the form is invalid" in new Setup {
        val redirectTo                                       = "APPLICATION_CHECK_PAGE"
        val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest(
          "POST",
          s"developer/applications/${app.id}/change-locked-subscription?name=$apiName&context=${apiContext}&version=${apiVersion}&redirectTo=$redirectTo"
        ).withCSRFToken.withLoggedIn(underTest, implicitly)(userSession.sessionId).withFormUrlEncodedBody()

        fetchByApplicationIdReturns(app.id, app)

        givenApplicationAction(app.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, List.empty)
        SubscriptionsServiceMock.IsSubscribedToApi.isTrue(app.id, apiIdentifier)

        val result = underTest.changeLockedApiSubscriptionAction(app.id, apiName, apiContext, apiVersion, redirectTo)(request)

        status(result) shouldBe BAD_REQUEST
      }
    }

    "an administrator attempts to change a submitted-for-checking production application" should { behave like allowedLockedSubscriptionChangeRequest(adminSession)(standardApp) }
    "an administrator attempts to change a created production application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withState(appStateTesting))
    }
    "an administrator attempts to change a submitted-for-checking sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "an administrator attempts to change a created sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(adminSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }
    "a developer attempts to change a submitted-for-checking production application" should { behave like forbiddenLockedSubscriptionChangeRequest(devSession)(standardApp) }
    "a developer attempts to change a created production application" should { behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withState(appStateTesting)) }
    "a developer attempts to change a submitted-for-checking sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withEnvironment(Environment.SANDBOX))
    }
    "a developer attempts to change a created sandbox application" should {
      behave like badLockedSubscriptionChangeRequest(devSession)(standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting))
    }
  }

  "Authorization" should {
    val apiContext    = ApiContext("api/test")
    val apiVersion    = ApiVersionNbr("1.0")
    val apiAccessType = "PUBLIC"

    "return not found for unauthorized user on unsubscribe to an API" in new Setup {
      val alteredActiveApplication = developerApplication.withCollaborators()

      fetchByApplicationIdReturns(appId, alteredActiveApplication)
      givenApplicationAction(alteredActiveApplication.withSubscriptions(Set.empty).withFieldValues(Map.empty), altDevSession, List.empty)

      val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"developer/applications/$appId/subscribe?context=${apiContext}&version=${apiVersion}&accessType=$apiAccessType&tab=subscriptions")
          .withLoggedIn(underTest, implicitly)(altDevSession.sessionId)

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
