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

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain._
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.{never, verify}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestApplications._
import utils.ViewHelpers._
import utils.{TestApplications, WithCSRFAddToken}
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class DetailsSpec extends BaseControllerSpec with WithCSRFAddToken {

  Helpers.running(fakeApplication) {

    "details" should {
      "return the view for a developer on a standard production app with no change link" in new Setup {
        detailsShouldRenderThePage(anApplication(developerEmail = loggedInUser.email), hasChangeButton = false)
      }

      "return the view for an admin on a standard production app" in new Setup {
        detailsShouldRenderThePage(anApplication(adminEmail = loggedInUser.email))
      }

      "return the view for a developer on a sandbox app" in new Setup {
        detailsShouldRenderThePage(aSandboxApplication(developerEmail = loggedInUser.email))
      }

      "return the view for an admin on a sandbox app" in new Setup {
        detailsShouldRenderThePage(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "return not found when not a teamMember on the app" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.callDetails

        status(result) shouldBe NOT_FOUND
      }

      "redirect to login when not logged in" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.callDetailsNotLoggedIn

        redirectsToLogin(result)
      }
    }

    "changeDetails" should {
      "return the view for an admin on a standard production app" in new Setup {
        changeDetailsShouldRenderThePage(anApplication(adminEmail = loggedInUser.email))
      }

      "return the view for a developer on a sandbox app" in new Setup {
        changeDetailsShouldRenderThePage(aSandboxApplication(developerEmail = loggedInUser.email))
      }

      "return the view for an admin on a sandbox app" in new Setup {
        changeDetailsShouldRenderThePage(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "return forbidden for a developer on a standard production app" in new Setup {
        val application = anApplication(developerEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.callChangeDetails

        status(result) shouldBe FORBIDDEN
      }

      "return not found when not a teamMember on the app" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.callChangeDetails

        status(result) shouldBe NOT_FOUND
      }

      "redirect to login when not logged in" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.callDetailsNotLoggedIn

        redirectsToLogin(result)
      }

      "return not found for an ROPC application" in new Setup {
        val application = anROPCApplication()
        givenTheApplicationExists(application)

        val result = await(underTest.details(application.id)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "return not found for a privileged application" in new Setup {
        val application = aPrivilegedApplication()
        givenTheApplicationExists(application)

        val result = await(underTest.details(application.id)(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }
    }

    "changeDetailsAction validation" should {
      "not pass when application is updated with empty name" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withName("").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST
      }

      "not pass when application is updated with invalid name" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withName("a").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST
      }

      "update name which contain HMRC should fail" in new Setup {
        given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Future.successful(Invalid.invalidName))

        val application = anApplication(adminEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withName("my invalid HMRC application name").callChangeDetailsAction

        status(result) shouldBe BAD_REQUEST

        verify(underTest.applicationService).isApplicationNameValid(
          mockEq("my invalid HMRC application name"),
          mockEq(application.deployedTo),
          mockEq(Some(application.id)))(any[HeaderCarrier])
      }
    }

    "changeDetailsAction for production app in testing state" should {

      "redirect to the details page on success for an admin" in new Setup {
        changeDetailsShouldRedirectOnSuccess(anApplication(adminEmail = loggedInUser.email))
      }

      "update all fields for an admin" in new Setup {
        changeDetailsShouldUpdateTheApplication(anApplication(adminEmail = loggedInUser.email))
      }

      "update both the app and the check information" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withName(newName).callChangeDetailsAction

        verify(underTest.applicationService).update(any[UpdateApplicationRequest])(any[HeaderCarrier])
        verify(underTest.applicationService).updateCheckInformation(mockEq(application.id), any[CheckInformation])(any[HeaderCarrier])
      }

      "return forbidden for a developer" in new Setup {
        val application = anApplication(developerEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withDescription(newDescription).callChangeDetailsAction

        status(result) shouldBe FORBIDDEN
      }

      "return not found when not a teamMember on the app" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.withDescription(newDescription).callChangeDetailsAction

        status(result) shouldBe NOT_FOUND
      }

      "redirect to login when not logged in" in new Setup {
        val application = aStandardApplication()
        givenTheApplicationExists(application)

        val result = application.withDescription(newDescription).callChangeDetailsActionNotLoggedIn

        redirectsToLogin(result)
      }
    }

    "changeDetailsAction for production app in uplifted state" should {

      "redirect to the details page on success for an admin" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
          .withState(ApplicationState.pendingGatekeeperApproval(loggedInUser.email))

        changeDetailsShouldRedirectOnSuccess(application)
      }

      "return forbidden for a developer" in new Setup {
        val application = anApplication(developerEmail = loggedInUser.email)
          .withState(ApplicationState.pendingGatekeeperApproval(loggedInUser.email))

        givenTheApplicationExists(application)

        val result = application.withDescription(newDescription).callChangeDetailsAction

        status(result) shouldBe FORBIDDEN
      }


      "keep original application name when administrator does an update" in new Setup {
        val application = anApplication(adminEmail = loggedInUser.email)
          .withState(ApplicationState.pendingGatekeeperApproval(loggedInUser.email))

        givenTheApplicationExists(application)

        val result = application.withName(newName).callChangeDetailsAction

        val updatedApplication = captureUpdatedApplication
        updatedApplication.name shouldBe application.name
      }
    }

    "changeDetailsAction for sandbox app" should {

      "redirect to the details page on success for an admin" in new Setup {
        changeDetailsShouldRedirectOnSuccess(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "redirect to the details page on success for a developer" in new Setup {
        changeDetailsShouldRedirectOnSuccess(aSandboxApplication(developerEmail = loggedInUser.email))
      }

      "update all fields for an admin" in new Setup {
        changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "update all fields for a developer" in new Setup {
        changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInUser.email))
      }

      "update the app but not the check information" in new Setup {
        val application = aSandboxApplication(adminEmail = loggedInUser.email)
        givenTheApplicationExists(application)

        val result = application.withName(newName).callChangeDetailsAction

        verify(underTest.applicationService).update(any[UpdateApplicationRequest])(any[HeaderCarrier])
        verify(underTest.applicationService, never).updateCheckInformation(mockEq(application.id), any[CheckInformation])(any[HeaderCarrier])
      }

    }

  }

  trait Setup {
    val underTest = new Details (
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mock[ApplicationService],
      mock[SessionService],
      mockErrorHandler,
      messagesApi,
      mock[ApplicationConfig]
    )

    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInUser = DeveloperSession(session)

    val newName = "new name"
    val newDescription = Some("new description")
    val newTermsUrl = Some("http://example.com/new-terms")
    val newPrivacyUrl = Some("http://example.com/new-privacy")

    given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
      .willReturn(Future.successful(Valid))

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .willReturn(successful(ApplicationUpdateSuccessful))

    given(underTest.applicationService.updateCheckInformation(any[String], any[CheckInformation])(any[HeaderCarrier]))
      .willReturn(successful(ApplicationUpdateSuccessful))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExists(application: Application) = {
      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens())
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq())
    }

    def captureUpdatedApplication = {
      val captor = ArgumentCaptor.forClass(classOf[UpdateApplicationRequest])
      verify(underTest.applicationService).update(captor.capture())(any[HeaderCarrier])
      captor.getValue
    }

    def redirectsToLogin(result: Result) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def detailsShouldRenderThePage(application: Application, hasChangeButton: Boolean = true) = {
      givenTheApplicationExists(application)

      val result = application.callDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(bodyOf(result))
      linkExistsWithHref(doc, routes.Details.changeDetails(application.id).url) shouldBe hasChangeButton
      elementIdentifiedByIdContainsText(doc, "applicationId", application.id) shouldBe true
      elementIdentifiedByIdContainsText(doc, "applicationName", application.name) shouldBe true
      elementIdentifiedByIdContainsText(doc, "description", application.description.getOrElse("None")) shouldBe true
      elementIdentifiedByIdContainsText(doc, "privacyPolicyUrl", application.privacyPolicyUrl.getOrElse("None")) shouldBe true
      elementIdentifiedByIdContainsText(doc, "termsAndConditionsUrl", application.termsAndConditionsUrl.getOrElse("None")) shouldBe true
    }

    def changeDetailsShouldRenderThePage(application: Application) = {
      givenTheApplicationExists(application)

      val result = application.callChangeDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(bodyOf(result))
      formExistsWithAction(doc, routes.Details.changeDetailsAction(application.id).url) shouldBe true
      linkExistsWithHref(doc, routes.Details.details(application.id).url) shouldBe true
      inputExistsWithValue(doc, "applicationId", "hidden", application.id) shouldBe true
      if (application.deployedTo == Environment.SANDBOX || application.state.name == State.TESTING) {
        inputExistsWithValue(doc, "applicationName", "text", application.name) shouldBe true
      } else {
        inputExistsWithValue(doc, "applicationName", "hidden", application.name) shouldBe true
      }
      textareaExistsWithText(doc, "description", application.description.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "privacyPolicyUrl", "text", application.privacyPolicyUrl.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "termsAndConditionsUrl", "text", application.termsAndConditionsUrl.getOrElse("None")) shouldBe true
    }

    def changeDetailsShouldRedirectOnSuccess(application: Application) = {
      givenTheApplicationExists(application)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(application.id).url)
    }

    def changeDetailsShouldUpdateTheApplication(application: Application) = {
      givenTheApplicationExists(application)

      application
        .withName(newName)
        .withDescription(newDescription)
        .withTermsAndConditionsUrl(newTermsUrl)
        .withPrivacyPolicyUrl(newPrivacyUrl)
        .callChangeDetailsAction

      val updatedApplication = captureUpdatedApplication
      updatedApplication.name shouldBe newName
      updatedApplication.description shouldBe newDescription
      updatedApplication.access match {
        case access: Standard =>
          access.termsAndConditionsUrl shouldBe newTermsUrl
          access.privacyPolicyUrl shouldBe newPrivacyUrl

        case _ => fail("Expected AccessType of STANDARD")
      }
    }

    implicit val format = Json.format[EditApplicationForm]

    implicit class ChangeDetailsAppAugment(val app: Application) {
      private val appAccess = app.access.asInstanceOf[Standard]

      final def toForm = EditApplicationForm(app.id, app.name, app.description,
        appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl)

      final def callDetails: Result = await(underTest.details(app.id)(loggedInRequest))

      final def callDetailsNotLoggedIn: Result = await(underTest.details(app.id)(loggedOutRequest))

      final def callChangeDetails: Result = await(addToken(underTest.changeDetails(app.id))(loggedInRequest))

      final def callChangeDetailsNotLoggedIn: Result = await(addToken(underTest.changeDetails(app.id))(loggedOutRequest))

      final def callChangeDetailsAction: Result = callChangeDetailsAction(loggedInRequest)

      final def callChangeDetailsActionNotLoggedIn: Result = callChangeDetailsAction(loggedOutRequest)

      private final def callChangeDetailsAction[T](request: FakeRequest[T]): Result = {
        await(addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toForm))))
      }
    }
  }
}
