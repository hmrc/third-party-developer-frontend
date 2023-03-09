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

import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import views.html.{AddRedirectView, ChangeRedirectView, DeleteRedirectConfirmationView, RedirectsView}

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class RedirectsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with TestApplications
    with CollaboratorTracker
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock {
    val applicationId = "1234"
    val clientId      = ClientId("clientId123")

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session   = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    val redirectUris = List("https://www.example.com", "https://localhost:8080")

    val redirectsView                  = app.injector.instanceOf[RedirectsView]
    val addRedirectView                = app.injector.instanceOf[AddRedirectView]
    val deleteRedirectConfirmationView = app.injector.instanceOf[DeleteRedirectConfirmationView]
    val changeRedirectView             = app.injector.instanceOf[ChangeRedirectView]

    val underTest = new Redirects(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      redirectsView,
      addRedirectView,
      deleteRedirectConfirmationView,
      changeRedirectView,
      fraudPreventionConfig
    )

    implicit val hc = HeaderCarrier()

    val sessionParams    = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    override def givenApplicationExists(application: Application): Unit = {
      givenApplicationAction(application, loggedInDeveloper)
      givenApplicationUpdateSucceeds()
    }

    def redirectsShouldRenderThePage(application: Application, shouldShowDeleteButton: Boolean) = {
      givenApplicationExists(application)

      val result = underTest.redirects(application.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      val document = Jsoup.parse(contentAsString(result))

      application.standardAccess.redirectUris.foreach(uri => elementExistsByText(document, "td", uri) shouldBe true)
      elementExistsByText(document, "button", "Delete") shouldBe shouldShowDeleteButton
    }

    def addRedirectShouldRenderThePage(application: Application, resultStatus: Int, shouldShowAmendControls: Boolean) = {
      givenApplicationExists(application)

      val result = underTest.addRedirect(application.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "redirectUri") shouldBe shouldShowAmendControls
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe shouldShowAmendControls
    }

    def addRedirectActionShouldRenderAddRedirectPageWithError(application: Application) = {
      givenApplicationExists(application)

      val result = underTest.addRedirectAction(application.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-redirectUri") shouldBe true
    }

    def addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(application: Application, redirectUri: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUri)
      val result  = underTest.addRedirectAction(application.id)(request)

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-redirectUri") shouldBe true
    }

    def addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(application: Application, redirectUriToAdd: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToAdd)
      val result  = underTest.addRedirectAction(application.id)(request)

      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe SEE_OTHER
      headers(result)
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(*)
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(redirectUriToAdd) shouldBe true
    }

    def deleteRedirectsShouldRenderThePage(application: Application, resultStatus: Int, shouldShowDeleteControls: Boolean, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete)
      val result  = underTest.deleteRedirect(application.id)(request)

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRenderTheConfirmationPage(application: Application, resultStatus: Int, shouldShowDeleteControls: Boolean, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result = underTest.deleteRedirectAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-deleteRedirectConfirm") shouldBe true
      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(application: Application, resultStatus: Int, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result =
        underTest.deleteRedirectAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete, "deleteRedirectConfirm" -> "Yes"))

      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe resultStatus
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(*)
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(redirectUriToDelete) shouldBe false
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(application: Application, resultStatus: Int, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result =
        underTest.deleteRedirectAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete, "deleteRedirectConfirm" -> "no"))

      status(result) shouldBe resultStatus

      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"
    }

    def changeRedirectUriShouldRenderThePage(application: Application, resultStatus: Int, originalRedirectUri: String, newRedirectUri: String) = {
      givenApplicationExists(application)

      val result = underTest.changeRedirect(application.id)(
        loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)
      )

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementIdentifiedByIdContainsValue(document, "originalRedirectUri", originalRedirectUri) shouldBe true
      elementIdentifiedByIdContainsValue(document, "newRedirectUri", newRedirectUri) shouldBe true
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe true
    }

    def changeRedirectUriActionShouldRenderError(originalRedirectUri: String, newRedirectUri: String) = {
      val application = anApplication(adminEmail = loggedInDeveloper.email).withRedirectUris(redirectUris)
      givenApplicationExists(application)

      val result = underTest.changeRedirectAction(application.id)(
        loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)
      )

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-newRedirectUri") shouldBe true
    }
  }

  "production application in state pre-production" should {

    trait PreApprovedReturnsNotFound extends Setup {
      def executeAction: Application => Future[Result]

      val testingApplication = aStandardNonApprovedApplication().withRedirectUris(redirectUris)

      givenApplicationExists(testingApplication)

      val result = executeAction(testingApplication)

      status(result) shouldBe NOT_FOUND
    }

    "return not found for redirects action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.redirects(app.id)(loggedInRequest.withCSRFToken) }
    }

    "return not found for addRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.addRedirect(app.id)(loggedInRequest.withCSRFToken) }
    }

    "return not found for addRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.addRedirectAction(app.id)(loggedInRequest.withCSRFToken) }
    }

    "return not found for deleteRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.deleteRedirectAction(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")) }
    }

    "return not found for deleteRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.deleteRedirectAction(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")) }
    }

    "return not found for changeRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) =>
        underTest.changeRedirect(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test"))
      }
    }

    "return not found for changeRedirectACtion action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) =>
        underTest.changeRedirectAction(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test"))
      }
    }
  }

  "redirects" should {
    "return the redirects page with no redirect URIs for an application with no redirect URIs" in new Setup {
      redirectsShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(List.empty),
        shouldShowDeleteButton = false
      )
    }

    "return the redirects page with some redirect URIs for an admin and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(List("https://www.example.com", "https://localhost:8080")),
        shouldShowDeleteButton = true
      )
    }

    "return the redirects page with some redirect URIs for a developer and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(
        anApplication(developerEmail = loggedInDeveloper.email)
          .withRedirectUris(List("https://www.example.com", "https://localhost:8080")),
        shouldShowDeleteButton = false
      )
    }
  }

  "addRedirect" should {
    "return the add redirect page for an admin on a production application" in new Setup {
      addRedirectShouldRenderThePage(anApplication(adminEmail = loggedInDeveloper.email), OK, shouldShowAmendControls = true)
    }

    "return the add redirect page for a developer on a production application" in new Setup {
      addRedirectShouldRenderThePage(anApplication(developerEmail = loggedInDeveloper.email), FORBIDDEN, shouldShowAmendControls = false)
    }
  }

  "addRedirectAction" should {
    "redirect to the redirects page after adding a new redirect uri" in new Setup {
      addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        "https://localhost:1234"
      )
    }

    "re-render the add redirect page when submitted without a redirect uri" in new Setup {
      addRedirectActionShouldRenderAddRedirectPageWithError(anApplication(adminEmail = loggedInDeveloper.email))
    }

    "re-render the add redirect page with an error message when trying to add a duplicate redirect uri" in new Setup {
      addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        "https://localhost:8080"
      )
    }
  }

  "deleteRedirect" should {
    "return the delete redirect page for an admin with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        OK,
        shouldShowDeleteControls = true,
        redirectUris.head
      )
    }

    "return the delete redirect page for a developer with a sandbox application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(environment = Environment.SANDBOX, developerEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        OK,
        shouldShowDeleteControls = true,
        redirectUriToDelete = redirectUris.head
      )
    }

    "return forbidden for a developer with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(developerEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        FORBIDDEN,
        shouldShowDeleteControls = false,
        redirectUris.head
      )
    }
  }

  "deleteRedirectAction" should {
    "return the delete redirect confirmation page when page is submitted with no radio button selected" in new Setup {
      deleteRedirectsActionShouldRenderTheConfirmationPage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        BAD_REQUEST,
        shouldShowDeleteControls = true,
        redirectUris.head
      )
    }

    "return the redirects page having successfully deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        SEE_OTHER,
        redirectUris.head
      )
    }

    "return the redirects page having not deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        SEE_OTHER,
        redirectUris.head
      )
    }
  }

  "changeRedirect" should {
    "return the change redirect page for an admin with a production application" in new Setup {
      changeRedirectUriShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris),
        OK,
        redirectUris.head,
        "https://www.another.example.com"
      )
    }
  }

  "changeRedirectAction" should {

    "return the redirect page for an admin with a production application when submitted a changed uri" in new Setup {
      val application         = anApplication(adminEmail = loggedInDeveloper.email).withRedirectUris(redirectUris)
      val originalRedirectUri = redirectUris.head
      val newRedirectUri      = "https://localhost:1111"
      givenApplicationExists(application)

      val result                                             = underTest.changeRedirectAction(application.id)(
        loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)
      )
      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe SEE_OTHER
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(*)
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(originalRedirectUri) shouldBe false
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(newRedirectUri) shouldBe true
    }

    "return the change redirect page for an admin with a production application when submitted a duplicate uri" in new Setup {
      changeRedirectUriActionShouldRenderError(originalRedirectUri = redirectUris.head, newRedirectUri = redirectUris.last)
    }

    "return the change redirect page for an admin with a production application when submitted an invalid uri" in new Setup {
      changeRedirectUriActionShouldRenderError(originalRedirectUri = redirectUris.head, newRedirectUri = "invalidURI")
    }
  }
}
