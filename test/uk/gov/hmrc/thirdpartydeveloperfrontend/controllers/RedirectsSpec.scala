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
import views.html.{AddRedirectView, ChangeRedirectView, DeleteRedirectConfirmationView, RedirectsView}

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures, RedirectUri}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, RedirectsServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class RedirectsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  implicit class AppAugment(val app: ApplicationWithCollaborators) {

    def standardAccess: Access.Standard = {
      if (app.isStandard) {
        app.access.asInstanceOf[Access.Standard]
      } else {
        throw new IllegalArgumentException(s"You can only use this method on a Standard application. Your app was ${app.access.accessType}")
      }
    }

    final def withRedirectUris(someRedirectUris: List[RedirectUri]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(redirectUris = someRedirectUris))
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with RedirectsServiceMockModule {

    val redirectUris = List("https://www.example.com", "https://localhost:8080").map(RedirectUri.unsafeApply)

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
      fraudPreventionConfig,
      RedirectsServiceMock.aMock
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    fetchSessionByIdReturns(adminSession.sessionId, adminSession)
    updateUserFlowSessionsReturnsSuccessfully(adminSession.sessionId)

    fetchSessionByIdReturns(devSession.sessionId, devSession)
    updateUserFlowSessionsReturnsSuccessfully(devSession.sessionId)

    override def givenApplicationExists(application: ApplicationWithCollaborators): Unit = {
      givenApplicationAction(application, adminSession)
    }

    def redirectsShouldRenderThePage(request: FakeRequest[_])(application: ApplicationWithCollaborators, shouldShowDeleteButton: Boolean) = {
      givenApplicationExists(application)

      val result = underTest.redirects(application.id)(request.withCSRFToken)

      status(result) shouldBe OK

      val document = Jsoup.parse(contentAsString(result))

      application.standardAccess.redirectUris.foreach(uri => elementExistsByText(document, "td", uri.toString()) shouldBe true)
      elementExistsByText(document, "button", "Delete") shouldBe shouldShowDeleteButton
    }

    def addRedirectShouldRenderThePage(request: FakeRequest[_])(application: ApplicationWithCollaborators, resultStatus: Int, shouldShowAmendControls: Boolean) = {
      givenApplicationExists(application)

      val result = underTest.addRedirect(application.id)(request.withCSRFToken)

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "redirectUri") shouldBe shouldShowAmendControls
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe shouldShowAmendControls
    }

    def addRedirectActionShouldRenderAddRedirectPageWithError(application: ApplicationWithCollaborators) = {
      givenApplicationExists(application)

      val result = underTest.addRedirectAction(application.id)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-redirectUri") shouldBe true
    }

    def addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(application: ApplicationWithCollaborators, redirectUri: String) = {
      givenApplicationExists(application)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUri)
      val result  = underTest.addRedirectAction(application.id)(request)

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-redirectUri") shouldBe true
    }

    def addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(application: ApplicationWithCollaborators, redirectUriToAdd: String) = {
      givenApplicationExists(application)
      RedirectsServiceMock.AddRedirect.succeedsWith(RedirectUri.unsafeApply(redirectUriToAdd))

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToAdd)
      val result  = underTest.addRedirectAction(application.id)(request)

      status(result) shouldBe SEE_OTHER
      headers(result)
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"
    }

    def deleteRedirectsShouldRenderThePage(
        request: FakeRequest[_]
      )(
        application: ApplicationWithCollaborators,
        resultStatus: Int,
        shouldShowDeleteControls: Boolean,
        redirectUriToDelete: String
      ) = {
      givenApplicationExists(application)

      val modRequest = request.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete)
      val result     = underTest.deleteRedirect(application.id)(modRequest)

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRenderTheConfirmationPage(
        application: ApplicationWithCollaborators,
        resultStatus: Int,
        shouldShowDeleteControls: Boolean,
        redirectUriToDelete: RedirectUri
      ) = {
      givenApplicationExists(application)

      val result = underTest.deleteRedirectAction(application.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> redirectUriToDelete.uri))

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-deleteRedirectConfirm") shouldBe true
      elementIdentifiedByIdContainsText(document, "redirectUriToDelete", redirectUriToDelete.uri) shouldBe shouldShowDeleteControls
      elementIdentifiedByIdContainsText(document, "submit", "Submit") shouldBe shouldShowDeleteControls
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(application: ApplicationWithCollaborators, resultStatus: Int, redirectUriToDelete: RedirectUri) = {
      givenApplicationExists(application)
      RedirectsServiceMock.DeleteRedirect.succeedsWith(redirectUriToDelete)

      val result =
        underTest.deleteRedirectAction(application.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody(
          "redirectUri"           -> redirectUriToDelete.uri,
          "deleteRedirectConfirm" -> "Yes"
        ))

      status(result) shouldBe resultStatus
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"
    }

    def deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(
        application: ApplicationWithCollaborators,
        resultStatus: Int,
        redirectUriToDelete: RedirectUri
      ) = {
      givenApplicationExists(application)

      val result =
        underTest.deleteRedirectAction(application.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody(
          "redirectUri"           -> redirectUriToDelete.uri,
          "deleteRedirectConfirm" -> "no"
        ))

      status(result) shouldBe resultStatus

      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"
    }

    def changeRedirectUriShouldRenderThePage(application: ApplicationWithCollaborators, resultStatus: Int, originalRedirectUri: String, newRedirectUri: String) = {
      givenApplicationExists(application)

      val result = underTest.changeRedirect(application.id)(
        loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)
      )

      status(result) shouldBe resultStatus

      val document = Jsoup.parse(contentAsString(result))

      elementIdentifiedByIdContainsValue(document, "originalRedirectUri", originalRedirectUri) shouldBe true
      elementIdentifiedByIdContainsValue(document, "newRedirectUri", newRedirectUri) shouldBe true
      elementIdentifiedByIdContainsText(document, "add", "Continue") shouldBe true
    }

    def changeRedirectUriActionShouldRenderError(originalRedirectUri: String, newRedirectUri: String) = {
      val application = standardApp.withRedirectUris(redirectUris)
      givenApplicationExists(application)

      val result = underTest.changeRedirectAction(application.id)(
        loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri, "newRedirectUri" -> newRedirectUri)
      )

      status(result) shouldBe BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))

      elementExistsById(document, "data-field-error-newRedirectUri") shouldBe true
    }
  }

  "production application in state pre-production" should {

    trait PreApprovedReturnsNotFound extends Setup {
      def executeAction: ApplicationWithCollaborators => Future[Result]

      val testingApplication = standardApp.withState(appStateTesting).withRedirectUris(redirectUris)

      givenApplicationExists(testingApplication)

      val result = executeAction(testingApplication)

      status(result) shouldBe NOT_FOUND
    }

    "return not found for redirects action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.redirects(app.id)(loggedInAdminRequest.withCSRFToken) }
    }

    "return not found for addRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.addRedirect(app.id)(loggedInAdminRequest.withCSRFToken) }
    }

    "return not found for addRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.addRedirectAction(app.id)(loggedInAdminRequest.withCSRFToken) }
    }

    "return not found for deleteRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.deleteRedirectAction(app.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")) }
    }

    "return not found for deleteRedirectAction action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) => underTest.deleteRedirectAction(app.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("redirectUri" -> "test")) }
    }

    "return not found for changeRedirect action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) =>
        underTest.changeRedirect(app.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test"))
      }
    }

    "return not found for changeRedirectACtion action" in new PreApprovedReturnsNotFound {
      def executeAction = { (app) =>
        underTest.changeRedirectAction(app.id)(loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> "test", "newRedirectUri" -> "test"))
      }
    }
  }

  "redirects" should {
    "return the redirects page with no redirect URIs for an application with no redirect URIs" in new Setup {
      redirectsShouldRenderThePage(loggedInAdminRequest)(
        standardApp
          .withRedirectUris(List.empty),
        shouldShowDeleteButton = false
      )
    }

    "return the redirects page with some redirect URIs for an admin and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(loggedInAdminRequest)(
        standardApp
          .withRedirectUris(redirectUris),
        shouldShowDeleteButton = true
      )
    }

    "return the redirects page with some redirect URIs for a developer and an application with some redirect URIs" in new Setup {
      redirectsShouldRenderThePage(loggedInDevRequest)(
        standardApp
          .withRedirectUris(redirectUris),
        shouldShowDeleteButton = false
      )
    }
  }

  "addRedirect" should {
    "return the add redirect page for an admin on a production application" in new Setup {
      addRedirectShouldRenderThePage(loggedInAdminRequest)(standardApp, OK, shouldShowAmendControls = true)
    }

    "return the add redirect page for a developer on a production application" in new Setup {
      addRedirectShouldRenderThePage(loggedInDevRequest)(standardApp, FORBIDDEN, shouldShowAmendControls = false)
    }
  }

  "addRedirectAction" should {
    "redirect to the redirects page after adding a new redirect uri" in new Setup {
      addRedirectActionShouldRenderRedirectsPageAfterAddingTheRedirectUri(
        standardApp
          .withRedirectUris(redirectUris),
        "https://localhost:1234"
      )
    }

    "re-render the add redirect page when submitted without a redirect uri" in new Setup {
      addRedirectActionShouldRenderAddRedirectPageWithError(standardApp)
    }

    "re-render the add redirect page with an error message when trying to add a duplicate redirect uri" in new Setup {
      addRedirectActionShouldRenderAddRedirectPageWithDuplicateUriError(
        standardApp
          .withRedirectUris(redirectUris),
        "https://localhost:8080"
      )
    }
  }

  "deleteRedirect" should {
    "return the delete redirect page for an admin with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(loggedInAdminRequest)(
        standardApp
          .withRedirectUris(redirectUris),
        OK,
        shouldShowDeleteControls = true,
        redirectUris.head.uri
      )
    }

    "return the delete redirect page for a developer with a sandbox application" in new Setup {
      deleteRedirectsShouldRenderThePage(loggedInAdminRequest)(
        standardApp.withEnvironment(Environment.SANDBOX)
          .withRedirectUris(redirectUris),
        OK,
        shouldShowDeleteControls = true,
        redirectUriToDelete = redirectUris.head.uri
      )
    }

    "return forbidden for a developer with a production application" in new Setup {
      deleteRedirectsShouldRenderThePage(loggedInDevRequest)(
        standardApp
          .withRedirectUris(redirectUris),
        FORBIDDEN,
        shouldShowDeleteControls = false,
        redirectUris.head.uri
      )
    }
  }

  "deleteRedirectAction" should {
    "return the delete redirect confirmation page when page is submitted with no radio button selected" in new Setup {
      deleteRedirectsActionShouldRenderTheConfirmationPage(
        standardApp
          .withRedirectUris(redirectUris),
        BAD_REQUEST,
        shouldShowDeleteControls = true,
        redirectUris.head
      )
    }

    "return the redirects page having successfully deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenSuccessful(
        standardApp
          .withRedirectUris(redirectUris),
        SEE_OTHER,
        redirectUris.head
      )
    }

    "return the redirects page having not deleted a redirect uri" in new Setup {
      deleteRedirectsActionShouldRedirectToTheRedirectsPageWhenUserChoosesNotToDelete(
        standardApp
          .withRedirectUris(redirectUris),
        SEE_OTHER,
        redirectUris.head
      )
    }
  }

  "changeRedirect" should {
    "return the change redirect page for an admin with a production application" in new Setup {
      changeRedirectUriShouldRenderThePage(
        standardApp
          .withRedirectUris(redirectUris),
        OK,
        redirectUris.head.uri,
        "https://www.another.example.com"
      )
    }
  }

  "changeRedirectAction" should {

    "return the redirect page for an admin with a production application when submitted a changed uri" in new Setup {
      val application         = standardApp.withRedirectUris(redirectUris)
      val originalRedirectUri = redirectUris.head
      val newRedirectUri      = RedirectUri.unsafeApply("https://localhost:1111")
      givenApplicationExists(application)
      RedirectsServiceMock.ChangeRedirect.succeedsWith(originalRedirectUri, newRedirectUri)

      val result = underTest.changeRedirectAction(application.id)(
        loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("originalRedirectUri" -> originalRedirectUri.uri, "newRedirectUri" -> newRedirectUri.uri)
      )
      status(result) shouldBe SEE_OTHER
      headers(result).apply(LOCATION) shouldBe s"/developer/applications/${application.id.value}/redirect-uris"
    }

    "return the change redirect page for an admin with a production application when submitted a duplicate uri" in new Setup {
      changeRedirectUriActionShouldRenderError(originalRedirectUri = redirectUris.head.uri, newRedirectUri = redirectUris.last.uri)
    }

    "return the change redirect page for an admin with a production application when submitted an invalid uri" in new Setup {
      changeRedirectUriActionShouldRenderError(originalRedirectUri = redirectUris.head.uri, newRedirectUri = "invalidURI")
    }
  }
}
