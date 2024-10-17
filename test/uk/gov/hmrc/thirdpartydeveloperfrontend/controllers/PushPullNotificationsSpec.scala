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
import views.html.ppns.PushSecretsView

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class PushPullNotificationsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  "showPushSecrets" when {
    "logged in as a Developer on an application" should {
      "return 403 for a prod app" in new Setup {
        val application: ApplicationWithCollaborators = standardApp
        givenApplicationAction(application, devSession)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInDevRequest)

        status(result) shouldBe FORBIDDEN
      }

      "return the push secret for a sandbox app" in new Setup {
        val application: ApplicationWithCollaborators = standardApp.withEnvironment(Environment.SANDBOX)
        showPushSecretsShouldRenderThePage(adminSession, loggedInAdminRequest)(application)
      }

      "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
        val application: ApplicationWithCollaborators = standardApp
        givenApplicationAction(application, adminSession)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInAdminRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "logged in as an Administrator on an application" should {
      "return the push secret for a production app" in new Setup {
        showPushSecretsShouldRenderThePage(adminSession, loggedInAdminRequest)(standardApp)
      }

      "return the push secret for a sandbox app" in new Setup {
        showPushSecretsShouldRenderThePage(adminSession, loggedInAdminRequest)(standardApp.withEnvironment(Environment.SANDBOX))
      }

      "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
        val application: ApplicationWithCollaborators = standardApp
        givenApplicationAction(application, adminSession)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInAdminRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "not a team member on an application" should {
      "return not found" in new Setup {
        val application: ApplicationWithCollaborators = standardApp.withCollaborators()
        givenApplicationAction(application, devSession)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInAdminRequest)

        status(result) shouldBe NOT_FOUND
      }
    }

    "not logged in" should {
      "redirect to login" in new Setup {
        val application: ApplicationWithCollaborators = standardApp
        givenApplicationAction(application, devSession)

        val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedOutRequest)

        redirectsToLogin(result)
      }
    }
  }

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock
      with SubscriptionTestHelper with FixedClock with SessionServiceMock {
    private val pushSecretsView                  = app.injector.instanceOf[PushSecretsView]
    private val pushPullNotificationsServiceMock = mock[PushPullNotificationsService]

    val underTest = new PushPullNotifications(
      sessionServiceMock,
      applicationServiceMock,
      mockErrorHandler,
      cookieSigner,
      applicationActionServiceMock,
      mcc,
      pushSecretsView,
      pushPullNotificationsServiceMock
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    FetchSessionById.succeedsWith(devSession.sessionId, devSession)
    UpdateUserFlowSessions.succeedsWith(devSession.sessionId)
    FetchSessionById.succeedsWith(adminSession.sessionId, adminSession)
    UpdateUserFlowSessions.succeedsWith(adminSession.sessionId)

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def showPushSecretsShouldRenderThePage(session: UserSession, request: FakeRequest[_])(application: ApplicationWithCollaborators) = {
      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields(application.id, application.clientId)("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = "PPNSField")))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), session, subsData)

      val expectedSecrets = Seq("some secret")
      when(pushPullNotificationsServiceMock.fetchPushSecrets(eqTo(application))(*)).thenReturn(successful(expectedSecrets))

      val result = underTest.showPushSecrets(application.id)(request)

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsText(doc, "push-secret", expectedSecrets.head) shouldBe true
      linkExistsWithHref(doc, routes.ManageSubscriptions.listApiSubscriptions(application.id).url) shouldBe true
    }
  }
}
