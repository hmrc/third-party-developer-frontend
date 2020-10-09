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

package controllers

import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.applications._
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.subscriptions.ApiSubscriptionFields
import mocks.service._
import org.jsoup.Jsoup
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.filters.csrf.CSRF.TokenProvider
import service.{PushPullNotificationsService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestApplications._
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.ppns.PushSecretsView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class PushPullNotificationsSpec extends BaseControllerSpec with WithCSRFAddToken with SubscriptionTestHelperSugar {

  Helpers.running(app) {
    "showPushSecrets" when {
      "logged in as a Developer on an application" should {
        "return 403 for a prod app" in new Setup {
          val application: Application = anApplication(developerEmail = loggedInUser.email)
          givenApplicationAction(application, loggedInUser)

          val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

          status(result) shouldBe FORBIDDEN
        }

        "return the push secret for a sandbox app" in new Setup {
          showPushSecretsShouldRenderThePage(aSandboxApplication(developerEmail = loggedInUser.email))
        }

        "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
          val application: Application = aSandboxApplication(developerEmail = loggedInUser.email)
          givenApplicationAction(application, loggedInUser)

          val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "logged in as an Administrator on an application" should {
        "return the push secret for a production app" in new Setup {
          showPushSecretsShouldRenderThePage(anApplication(adminEmail = loggedInUser.email))
        }

        "return the push secret for a sandbox app" in new Setup {
          showPushSecretsShouldRenderThePage(aSandboxApplication(adminEmail = loggedInUser.email))
        }

        "return 404 when the application is not subscribed to an API with PPNS fields" in new Setup {
          val application: Application = anApplication(adminEmail = loggedInUser.email)
          givenApplicationAction(application, loggedInUser)

          val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "not a team member on an application" should {
        "return not found" in new Setup {
          val application: Application = aStandardApplication
          givenApplicationAction(application, loggedInUser)

          val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }
      }

      "not logged in" should {
        "redirect to login" in new Setup {
          val application: Application = aStandardApplication
          givenApplicationAction(application, loggedInUser)

          val result: Future[Result] = underTest.showPushSecrets(application.id)(loggedOutRequest)

          redirectsToLogin(result)
        }
      }
    }
  }

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock {
    private val pushSecretsView = app.injector.instanceOf[PushSecretsView]
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

    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInUser = DeveloperSession(session)

    when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
      .thenReturn(successful(Some(session)))
    when(underTest.sessionService.updateUserFlowSessions(sessionId)).thenReturn(successful(()))

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def showPushSecretsShouldRenderThePage(application: Application) = {
      val subscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("ppns", 1)
      val newFields: Seq[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = "PPNSField")))
      val subsData = Seq(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInUser, subsData)

      val expectedSecrets = Seq("some secret")
      when(pushPullNotificationsServiceMock.fetchPushSecrets(eqTo(application))(any[HeaderCarrier])).thenReturn(successful(expectedSecrets))

      val result = underTest.showPushSecrets(application.id)(loggedInRequest)

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsText(doc, "push-secret", expectedSecrets.head) shouldBe true
      linkExistsWithHref(doc, routes.ManageSubscriptions.listApiSubscriptions(application.id).url) shouldBe true
    }
  }
}
