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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import views.html.ppns.PushSecretsView

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{PushPullNotificationsService, SessionService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, TestApplications, WithCSRFAddToken}

class PushPullNotificationsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with TestApplications
    with CollaboratorTracker
    with UserBuilder
    with LocalUserIdTracker
    with GuiceOneAppPerSuite {

  "showPushSecrets" when {
    "logged in as a Developer on an application" should {
      "return 403 for a prod app" in new Setup {
        val application: Application = anApplication(developerEmail = session.developer.email)
        givenApplicationAction(application, session)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

        status(result) shouldBe FORBIDDEN
      }

      "return the push secret for a sandbox app" in new Setup {
        showPushSecretsShouldRenderThePage(aSandboxApplication(developerEmail = session.developer.email))
      }

      "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
        val application: Application = aSandboxApplication(developerEmail = session.developer.email)
        givenApplicationAction(application, session)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "logged in as an Administrator on an application" should {
      "return the push secret for a production app" in new Setup {
        showPushSecretsShouldRenderThePage(anApplication(adminEmail = session.developer.email))
      }

      "return the push secret for a sandbox app" in new Setup {
        showPushSecretsShouldRenderThePage(aSandboxApplication(adminEmail = session.developer.email))
      }

      "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
        val application: Application = anApplication(adminEmail = session.developer.email)
        givenApplicationAction(application, session)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "not a team member on an application" should {
      "return not found" in new Setup {
        val application: Application = aStandardApplication
        givenApplicationAction(application, session)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "not logged in" should {
      "redirect to login" in new Setup {
        val application: Application = aStandardApplication
        givenApplicationAction(application, session)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedOutRequest)

        redirectsToLogin(result)
      }
    }
  }

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock {
    private val pushSecretsView                  = app.injector.instanceOf[PushSecretsView]
    private val pushPullNotificationsServiceMock = mock[PushPullNotificationsService]

    val underTest = new PushPullNotifications(
      mock[SessionService],
      applicationServiceMock,
      mockErrorHandler,
      cookieSigner,
      applicationActionServiceMock,
      mcc,
      pushSecretsView,
      pushPullNotificationsServiceMock
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val developer = buildTrackedUser()
    val sessionId = UserSessionId.random
    val session   = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)

    when(underTest.sessionService.fetch(eqTo(sessionId))(*))
      .thenReturn(successful(Some(session)))
    when(underTest.sessionService.updateUserFlowSessions(sessionId)).thenReturn(successful(()))

    val sessionParams    = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def showPushSecretsShouldRenderThePage(application: Application) = {
      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = "PPNSField")))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), session, subsData)

      val expectedSecrets = Seq("some secret")
      when(pushPullNotificationsServiceMock.fetchPushSecrets(eqTo(application))(*)).thenReturn(successful(expectedSecrets))

      val result = underTest.showPushSecrets(application.id)(loggedInRequest)

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsText(doc, "push-secret", expectedSecrets.head) shouldBe true
      linkExistsWithHref(doc, routes.ManageSubscriptions.listApiSubscriptions(application.id).url) shouldBe true
    }
  }
}
