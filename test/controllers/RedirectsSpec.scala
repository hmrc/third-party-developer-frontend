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

import domain.models.applications.{Standard, UpdateApplicationRequest}
import domain.models.developers.Session
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestApplications._
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{AddRedirectView, ChangeRedirectView, DeleteRedirectConfirmationView, RedirectsView}

import scala.concurrent.ExecutionContext.Implicits.global
import domain.models.developers.Developer
import domain.models.developers.LoggedInState
import domain.models.applications.Application
import domain.models.developers.DeveloperSession
import domain.models.applications.Environment

class RedirectsSpec extends BaseControllerSpec with WithCSRFAddToken {

  val applicationId = "1234"
  val clientId = "clientId123"

  val developer = Developer("third.party.developer@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInDeveloper = DeveloperSession(session)

  val redirectUris = Seq("https://www.example.com", "https://localhost:8080")

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val redirectsView = app.injector.instanceOf[RedirectsView]
    val addRedirectView = app.injector.instanceOf[AddRedirectView]
    val deleteRedirectConfirmationView = app.injector.instanceOf[DeleteRedirectConfirmationView]
    val changeRedirectView = app.injector.instanceOf[ChangeRedirectView]

    val underTest = new Redirects(
      applicationServiceMock,
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      redirectsView,
      addRedirectView,
      deleteRedirectConfirmationView,
      changeRedirectView
    )

    implicit val hc = HeaderCarrier()

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, session)

    override def givenApplicationExists(application: Application): Unit = {
      super.givenApplicationExists(application)
      givenApplicationUpdateSucceeds()
    }

    def redirectsShouldRenderThePage(application: Application, shouldShowDeleteButton: Boolean) = {
      givenApplicationExists(application)

      val result = await(underTest.redirects(application.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK

      val document = Jsoup.parse(bodyOf(result))

      application.standardAccess.redirectUris.foreach(uri => elementExistsByText(document, "td", uri) shouldBe true)
      elementExistsById(document, "delete") shouldBe shouldShowDeleteButton
    }

    def addRedirectShouldRenderThePage(application: Application, resultStatus: Int, shouldShowAmendControls: Boolean) = {
      givenApplicationExists(application)

      val result = await(underTest.addRedirect(application.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(bodyOf(result))

      elementExistsById(document, "redirectUri") shouldBe shouldShowAmendControls
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe shouldShowAmendControls
    }

    def addRedirectActionShouldRenderAddRedirectPageWithError(application: Application) = {
      givenApplicationExists(application)

      val result = await(underTest.addRedirectAction(application.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-redirecturi", "This field is required") shouldBe true
    }

    def addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(application: Application, redirectUri: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUri)
      val result = await(underTest.addRedirectAction(application.id)(request))
        
      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-redirecturi", "You already provided that redirect URI") shouldBe true
    }

    def addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(application: Application, redirectUriToAdd: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToAdd)
      val result = await(underTest.addRedirectAction(application.id)(request))

      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) shouldBe s"/developer/applications/${application.id}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(any[HeaderCarrier])
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(redirectUriToAdd) shouldBe true
    }

    def deleteRedirectsShouldRenderThePage(application: Application, resultStatus: Int, shouldShowDeleteControls: Boolean, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete)
      val result = await(underTest.deleteRedirect(application.id)(request))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRenderTheConfirmationPage(application: Application,
                                                             resultStatus: Int,
                                                             shouldShowDeleteControls: Boolean,
                                                             redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result = await(underTest.deleteRedirectAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete)))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByAttrContainsText(
        document, "span", "data-field-error-deleteredirectconfirm", "Tell us if you want to delete this redirect URI") shouldBe true
      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(application: Application, resultStatus: Int, redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result = await(underTest.deleteRedirectAction(application.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete, "deleteRedirectConfirm" -> "Yes")))

      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe resultStatus
      result.header.headers(LOCATION) shouldBe s"/developer/applications/${application.id}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(any[HeaderCarrier])
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(redirectUriToDelete) shouldBe false
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(application: Application,
                                                                                        resultStatus: Int,
                                                                                        redirectUriToDelete: String) = {
      givenApplicationExists(application)

      val result = await(underTest.deleteRedirectAction(application.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete, "deleteRedirectConfirm" -> "no")))

      status(result) shouldBe resultStatus

      result.header.headers(LOCATION) shouldBe s"/developer/applications/${application.id}/redirect-uris"
    }

    def changeRedirectUriShouldRenderThePage(application: Application, resultStatus: Int, originalRedirectUri: String, newRedirectUri: String) = {
      givenApplicationExists(application)

      val result = await(underTest.changeRedirect(application.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByIdContainsValue(document, "originalRedirectUri", originalRedirectUri) shouldBe true
      elementIdentifiedByIdContainsValue(document, "newRedirectUri", newRedirectUri) shouldBe true
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe true
    }

    def changeRedirectUriActionShouldRenderError(originalRedirectUri: String, newRedirectUri: String, errorMessage: String) = {
      val application = anApplication(adminEmail = loggedInDeveloper.email).withRedirectUris(redirectUris)
      givenApplicationExists(application)

      val result = await(underTest.changeRedirectAction(application.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)))

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(bodyOf(result))

      elementIdentifiedByAttrContainsText(document, "span", "data-field-error-newredirecturi", errorMessage) shouldBe true
    }
 }

  "production application in state pre-production" should {

    trait PreApprovedReturnsNotFound extends Setup {
      def executeAction: Application => Result
      
      val testingApplication = aStandardNonApprovedApplication().withRedirectUris(redirectUris)

      givenApplicationExists(testingApplication)
      
      val result : Result = executeAction(testingApplication)

      status(result) shouldBe NOT_FOUND
    }

    "return not found for redirects action" in new PreApprovedReturnsNotFound {
      def executeAction = { 
        (app) => await(underTest.redirects(app.id)(loggedInRequest.withCSRFToken)) 
      }
    }

    "return not found for addRedirect action" in new PreApprovedReturnsNotFound {
       def executeAction = {
         (app) => await(underTest.addRedirect(app.id)(loggedInRequest.withCSRFToken))
       }
    }

    "return not found for addRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = {
        (app) => await(underTest.addRedirectAction(app.id)(loggedInRequest.withCSRFToken))
      }
    }

    "return not found for deleteRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = {
        (app) => await(underTest.deleteRedirectAction(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")))
      }
    }

    "return not found for deleteRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = {
        (app) => await(underTest.deleteRedirectAction(app.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")))
      }
    }

    "return not found for changeRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = {
        (app) => await(underTest.changeRedirect(app.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test")))
      }
    }

    "return not found for changeRedirectACtion action" in new PreApprovedReturnsNotFound {
      def executeAction = {
        (app) => await(underTest.changeRedirectAction(app.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test")))
      }
    }
  }

  "redirects" should {
    "return the redirects page with no redirect URIs for an application with no redirect URIs" in new Setup {
      redirectsShouldRenderThePage(anApplication(adminEmail = loggedInDeveloper.email)
        .withRedirectUris(Seq()), shouldShowDeleteButton = false)
    }

    "return the redirects page with some redirect URIs for an admin and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(anApplication(adminEmail = loggedInDeveloper.email)
        .withRedirectUris(Seq("https://www.example.com", "https://localhost:8080")), shouldShowDeleteButton = true)
    }

    "return the redirects page with some redirect URIs for a developer and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(anApplication(developerEmail = loggedInDeveloper.email)
        .withRedirectUris(Seq("https://www.example.com", "https://localhost:8080")), shouldShowDeleteButton = false)
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
      val redirectUris = Seq("https://www.example.com", "https://localhost:8080")

      addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), "https://localhost:1234")
    }

    "re-render the add redirect page when submitted without a redirect uri" in new Setup {
      addRedirectActionShouldRenderAddRedirectPageWithError(anApplication(adminEmail = loggedInDeveloper.email))
    }

    "re-render the add redirect page with an error message when trying to add a duplicate redirect uri" in new Setup {
      val redirectUris = Seq("https://www.example.com", "https://localhost:8080")

      addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), "https://localhost:8080")
    }
  }

  "deleteRedirect" should {
    "return the delete redirect page for an admin with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), OK, shouldShowDeleteControls = true, redirectUris.head)
    }

    "return the delete redirect page for a developer with a sandbox application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(environment = Environment.SANDBOX, developerEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), OK, shouldShowDeleteControls = true, redirectUriToDelete = redirectUris.head)
    }

    "return forbidden for a developer with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(
        anApplication(developerEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), FORBIDDEN, shouldShowDeleteControls = false, redirectUris.head)
    }
  }

  "deleteRedirectAction" should {
    "return the delete redirect confirmation page when page is submitted with no radio button selected" in new Setup {
      deleteRedirectsActionShouldRenderTheConfirmationPage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), BAD_REQUEST, shouldShowDeleteControls = true, redirectUris.head)
    }

    "return the redirects page having successfully deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), SEE_OTHER, redirectUris.head)
    }

    "return the redirects page having not deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), SEE_OTHER, redirectUris.head)
    }
  }

  "changeRedirect" should {
    "return the change redirect page for an admin with a production application" in new Setup {
      changeRedirectUriShouldRenderThePage(
        anApplication(adminEmail = loggedInDeveloper.email)
          .withRedirectUris(redirectUris), OK, redirectUris.head, "https://www.another.example.com")
    }
  }

  "changeRedirectAction" should {

    "return the redirect page for an admin with a production application when submitted a changed uri" in new Setup {
      val application = anApplication(adminEmail = loggedInDeveloper.email).withRedirectUris(redirectUris)
      val originalRedirectUri = redirectUris.head
      val newRedirectUri = "https://localhost:1111"
      givenApplicationExists(application)

      val result = await(underTest.changeRedirectAction(application.id)(
          loggedInRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)))
      val argument: ArgumentCaptor[UpdateApplicationRequest] = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) shouldBe s"/developer/applications/${application.id}/redirect-uris"

      verify(underTest.applicationService).update(argument.capture())(any[HeaderCarrier])
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(originalRedirectUri) shouldBe false
      argument.getValue.access.asInstanceOf[Standard].redirectUris.contains(newRedirectUri) shouldBe true
    }

    "return the change redirect page for an admin with a production application when submitted a duplicate uri" in new Setup {
      changeRedirectUriActionShouldRenderError(
        originalRedirectUri = redirectUris.head,
        newRedirectUri = redirectUris.last,
        errorMessage = "You already provided that redirect URI")
    }

    "return the change redirect page for an admin with a production application when submitted an invalid uri" in new Setup {
      changeRedirectUriActionShouldRenderError(
        originalRedirectUri = redirectUris.head,
        newRedirectUri = "invalidURI",
        errorMessage = "Provide a valid redirect URI")
    }
  }
}
