/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.DateTime
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import org.jsoup.Jsoup
import org.mockito.captor.ArgCaptor
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html.{ChangeDetailsView, DetailsView, UpdatePrivacyPolicyLocationView}
import views.html.application.PendingApprovalView
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.TestApplications
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails

class DetailsSpec 
    extends BaseControllerSpec 
    with WithCSRFAddToken 
    with TestApplications 
    with DeveloperBuilder 
    with CollaboratorTracker 
    with LocalUserIdTracker
    with SubmissionServiceMockModule {

  "details" when {
    "logged in as a Developer on an application" should {
      "return the view for a standard production app with no change link" in new Setup {
        val approvedApplication = anApplication(developerEmail = loggedInDeveloper.email)
        detailsShouldRenderThePage(approvedApplication, hasChangeButton = false)
      }

      "return the view for a standard production app with terms of use not agreed"  in new Setup {
        val approvedApplication = anApplication(developerEmail = loggedInDeveloper.email)
        detailsShouldRenderThePage(approvedApplication, hasChangeButton = false, hasTermsOfUseAgreement = false)
      }

      "return the view for a developer on a sandbox app" in new Setup {
        detailsShouldRenderThePage(aSandboxApplication(developerEmail = loggedInDeveloper.email), hasTermsOfUseAgreement = false)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        val approvedApplication = anApplication(adminEmail = loggedInDeveloper.email)
        detailsShouldRenderThePage(approvedApplication)
      }

      "return the view for an admin on a sandbox app" in new Setup {
        detailsShouldRenderThePage(aSandboxApplication(adminEmail = loggedInDeveloper.email), hasTermsOfUseAgreement = false)
      }

      "return a redirect when using an application in testing state" in new Setup {
        val testingApplication = anApplication(adminEmail = loggedInDeveloper.email, state = ApplicationState.testing)
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        givenApplicationAction(testingApplication, loggedInDeveloper)

        val result = testingApplication.callDetails

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${testingApplication.id.value}/request-check")
      }

      "return the credentials requested page on an application pending approval" in new Setup {
        val pendingApprovalApplication = anApplication(adminEmail = loggedInDeveloper.email, state = ApplicationState.pendingGatekeeperApproval("dont-care"))

        givenApplicationAction(pendingApprovalApplication, loggedInDeveloper)

        val result = addToken(underTest.details(pendingApprovalApplication.id))(loggedInDevRequest)

        status(result) shouldBe OK

        val document = Jsoup.parse(contentAsString(result))
        elementExistsByText(document, "h1", "Credentials requested") shouldBe true
        elementExistsByText(document, "span", pendingApprovalApplication.name) shouldBe true
      }

      "return the credentials requested page on an application pending verification" in new Setup {
        val pendingVerificationApplication = anApplication(adminEmail = loggedInDeveloper.email, state = ApplicationState.pendingRequesterVerification("dont-care", "dont-care"))

        givenApplicationAction(pendingVerificationApplication, loggedInDeveloper)

        val result = addToken(underTest.details(pendingVerificationApplication.id))(loggedInDevRequest)

        status(result) shouldBe OK

        val document = Jsoup.parse(contentAsString(result))
        elementExistsByText(document, "h1", "Credentials requested") shouldBe true
        elementExistsByText(document, "span", pendingVerificationApplication.name) shouldBe true
      }

      "redirect to the Start Using Your Application page on an application in pre-production state" in new Setup {
        val userEmail = "test@example.con"
        val preProdApplication = anApplication(adminEmail = loggedInDeveloper.email, state = ApplicationState.preProduction(userEmail))

        givenApplicationAction(preProdApplication, loggedInDeveloper)

        val result = addToken(underTest.details(preProdApplication.id))(loggedInDevRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.StartUsingYourApplicationController.startUsingYourApplicationPage(preProdApplication.id).url)
      }

      "display the Application Details page for an application in pre-production state when the forceAppDetails parameter is used" in new Setup {
        val userEmail = "test@example.con"
        val preProdApplication = anApplication(adminEmail = loggedInDeveloper.email, state = ApplicationState.preProduction(userEmail))

        returnAgreementDetails()
        givenApplicationAction(preProdApplication, loggedInDeveloper)
        val loggedInRequestWithForceAppDetailsParam = FakeRequest("GET", "/?forceAppDetails").withLoggedIn(underTest, implicitly)(devSessionId).withSession(sessionParams: _*)
        val result = addToken(underTest.details(preProdApplication.id))(loggedInRequestWithForceAppDetailsParam)

        status(result) shouldBe OK
        val document = Jsoup.parse(contentAsString(result))
        elementExistsByText(document, "h1", "Application details") shouldBe true
      }
    }

    "not a team member on an application" should {
      "return not found" in new Setup {
        val application = aStandardApplication
        givenApplicationAction(application, loggedInDeveloper)

        val result = application.callDetails

        status(result) shouldBe NOT_FOUND
      }
    }

    "not logged in" should {
      "redirect to login" in new Setup {
        val application = aStandardApplication
        givenApplicationAction(application, loggedInDeveloper)

        val result = application.callDetailsNotLoggedIn

        redirectsToLogin(result)
      }
    }
}

  "changeDetails" should {
    "return forbidden for an admin on a standard production app" in new Setup {
      val application = anApplication(adminEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callChangeDetails

      status(result) shouldBe FORBIDDEN
    }

    "return the view for a developer on a sandbox app" in new Setup {
      changeDetailsShouldRenderThePage(
        aSandboxApplication(developerEmail = loggedInDeveloper.email)
      )
    }

    "return the view for an admin on a sandbox app" in new Setup {
      changeDetailsShouldRenderThePage(
        aSandboxApplication(adminEmail = loggedInDeveloper.email)
      )
    }

    "return forbidden for a developer on a standard production app" in new Setup {
      val application = anApplication(developerEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callChangeDetails

      status(result) shouldBe FORBIDDEN
    }

    "return not found when not a teamMember on the app" in new Setup {
      val application = aStandardApprovedApplication
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callChangeDetails

      status(result) shouldBe NOT_FOUND
    }

    "redirect to login when not logged in" in new Setup {
      val application = aStandardApprovedApplication
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callDetailsNotLoggedIn

      redirectsToLogin(result)
    }

    "return not found for an ROPC application" in new Setup {
      val application = anROPCApplication()
      givenApplicationAction(application, loggedInDeveloper)

      val result = underTest.details(application.id)(loggedInDevRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return not found for a privileged application" in new Setup {
      val application = aPrivilegedApplication()
      givenApplicationAction(application, loggedInDeveloper)

      val result = underTest.details(application.id)(loggedInDevRequest)

      status(result) shouldBe NOT_FOUND
    }
  }

  "changeDetailsAction validation" should {
    "not pass when application is updated with empty name" in new Setup {
      val application = aSandboxApplication(adminEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withName("").callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }

    "not pass when application is updated with invalid name" in new Setup {
      val application = aSandboxApplication(adminEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withName("a").callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }

    "update name which contain HMRC should fail" in new Setup {
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(Invalid.invalidName))

      val application = aSandboxApplication(adminEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withName("my invalid HMRC application name").callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST

      verify(underTest.applicationService).isApplicationNameValid(eqTo("my invalid HMRC application name"), eqTo(application.deployedTo), eqTo(Some(application.id)))(
        *
      )
    }
  }

  "changeDetailsAction for production app in testing state" should {

    "return not found" in new Setup {
      val application = aStandardNonApprovedApplication()
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callChangeDetails

      status(result) shouldBe NOT_FOUND
    }

    "return not found when not a teamMember on the app" in new Setup {
      val application = aStandardApprovedApplication
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe NOT_FOUND
    }

    "redirect to login when not logged in" in new Setup {
      val application = aStandardApprovedApplication
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withDescription(newDescription).callChangeDetailsActionNotLoggedIn

      redirectsToLogin(result)
    }
  }

  "changeDetailsAction for production app in uplifted state" should {

    "return forbidden for a developer" in new Setup {
      val application = anApplication(developerEmail = loggedInDeveloper.email)

      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden for an admin" in new Setup {
      val application = anApplication(adminEmail = loggedInDeveloper.email)

      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeDetailsAction for sandbox app" should {

    "redirect to the details page on success for an admin" in new Setup {
      changeDetailsShouldRedirectOnSuccess(aSandboxApplication(adminEmail = loggedInDeveloper.email))
    }

    "redirect to the details page on success for a developer" in new Setup {
      changeDetailsShouldRedirectOnSuccess(aSandboxApplication(developerEmail = loggedInDeveloper.email))
    }

    "update all fields for an admin" in new Setup {
      changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInDeveloper.email))
    }

    "update all fields for a developer" in new Setup {
      changeDetailsShouldUpdateTheApplication(aSandboxApplication(adminEmail = loggedInDeveloper.email))
    }

    "update the app but not the check information" in new Setup {
      val application = aSandboxApplication(adminEmail = loggedInDeveloper.email)
      givenApplicationAction(application, loggedInDeveloper)

      await(application.withName(newName).callChangeDetailsAction)

      verify(underTest.applicationService).update(any[UpdateApplicationRequest])(*)
      verify(underTest.applicationService, never).updateCheckInformation(eqTo(application), any[CheckInformation])(*)
    }
  }

  "changing privacy policy location" should {
    def appWithPrivacyPolicyLocation(privacyPolicyLocation: PrivacyPolicyLocation) = anApplication(access = Standard(List.empty, None, None, Set.empty, None, Some(
      ImportantSubmissionData(None, ResponsibleIndividual.build("bob example", "bob@example.com"), Set.empty, TermsAndConditionsLocation.InDesktopSoftware,
        privacyPolicyLocation, List.empty)))
    )
    val privacyPolicyUrl = "http://example.com/priv-policy"

    implicit val writeChangeOfPrivacyPolicyLocationForm = Json.writes[ChangeOfPrivacyPolicyLocationForm]

    "display update page with 'in desktop' radio selected" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, loggedInAdmin)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe true
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe false
    }

    "display update page with 'url' radio selected" in new Setup {
      val appWithPrivPolicyUrl = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.Url(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, loggedInAdmin)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe false
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe true
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", privacyPolicyUrl)
    }

    "return Not Found error if application cannot be retrieved" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.InDesktopSoftware)
      givenApplicationActionReturnsNotFound(appWithPrivPolicyInDesktop.id)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return bad request if privacy policy location has not changed" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, loggedInAdmin)

      val form = ChangeOfPrivacyPolicyLocationForm("", true)
      val result = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest.withJsonBody(Json.toJson(form)))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request if form data is invalid" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, loggedInAdmin)

      val form = ChangeOfPrivacyPolicyLocationForm("", false)
      val result = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest.withJsonBody(Json.toJson(form)))

      status(result) shouldBe BAD_REQUEST
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocation.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, loggedInAdmin)
      when(applicationServiceMock.updatePrivacyPolicyLocation(eqTo(appWithPrivPolicyInDesktop), *[UserId], eqTo(PrivacyPolicyLocation.Url(privacyPolicyUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      val form = ChangeOfPrivacyPolicyLocationForm(privacyPolicyUrl, false)
      val result = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest.withJsonBody(Json.toJson(form)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appWithPrivPolicyInDesktop.id).url)
    }
  }

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with TermsOfUseServiceMock {
    val unauthorisedAppDetailsView = app.injector.instanceOf[UnauthorisedAppDetailsView]
    val pendingApprovalView = app.injector.instanceOf[PendingApprovalView]
    val detailsView = app.injector.instanceOf[DetailsView]
    val changeDetailsView = app.injector.instanceOf[ChangeDetailsView]
    val updatePrivacyPolicyLocationView = app.injector.instanceOf[UpdatePrivacyPolicyLocationView]

    val underTest = new Details(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mock[SessionService],
      mcc,
      cookieSigner,
      unauthorisedAppDetailsView,
      pendingApprovalView,
      detailsView,
      changeDetailsView,
      updatePrivacyPolicyLocationView,
      fraudPreventionConfig,
      SubmissionServiceMock.aMock,
      termsOfUseServiceMock
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val developer = buildDeveloper()
    val admin = buildDeveloper(emailAddress = "admin@example.com")
    val devSessionId = "dev sessionId"
    val adminSessionId = "admin sessionId"
    val devSession = Session(devSessionId, developer, LoggedInState.LOGGED_IN)
    val adminSession = Session(adminSessionId, admin, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(devSession)
    val loggedInAdmin = DeveloperSession(adminSession)

    val newName = "new name"
    val newDescription = Some("new description")
    val newTermsUrl = Some("http://example.com/new-terms")
    val newPrivacyUrl = Some("http://example.com/new-privacy")

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(Valid))

    when(underTest.sessionService.fetch(eqTo(devSessionId))(*)).thenReturn(successful(Some(devSession)))
    when(underTest.sessionService.fetch(eqTo(adminSessionId))(*)).thenReturn(successful(Some(adminSession)))

    when(underTest.sessionService.updateUserFlowSessions(*)).thenReturn(successful(()))

    when(underTest.applicationService.update(any[UpdateApplicationRequest])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    when(underTest.applicationService.updateCheckInformation(any[Application], any[CheckInformation])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInDevRequest = FakeRequest().withLoggedIn(underTest, implicitly)(devSessionId).withSession(sessionParams: _*)
    val loggedInAdminRequest = FakeRequest().withLoggedIn(underTest, implicitly)(adminSessionId).withSession(sessionParams: _*)

    def captureUpdatedApplication: UpdateApplicationRequest = {
      val captor = ArgCaptor[UpdateApplicationRequest]
      verify(underTest.applicationService).update(captor)(*)
      captor.value
    }

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def detailsShouldRenderThePage(application: Application, hasChangeButton: Boolean = true, hasTermsOfUseAgreement: Boolean = true) = {
      givenApplicationAction(application, loggedInDeveloper)

      if (hasTermsOfUseAgreement) {
        returnAgreementDetails(TermsOfUseAgreementDetails("test@example.com", None, DateTime.now, Some("1.2")))
      } else {
        returnAgreementDetails()
      }

      val result = application.callDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      // APIS-5669 - temporarily removed Change link
      // linkExistsWithHref(doc, routes.Details.changeDetails(application.id).url) shouldBe hasChangeButton
      elementIdentifiedByIdContainsText(doc, "applicationId", application.id.value) shouldBe true
      elementIdentifiedByIdContainsText(doc, "applicationName", application.name) shouldBe true
      elementIdentifiedByIdContainsText(doc, "description", application.description.getOrElse("None")) shouldBe true
      elementIdentifiedByIdContainsText(doc, "privacyPolicyUrl", PrivacyPolicyLocation.asText(application.privacyPolicyLocation)) shouldBe true
      elementIdentifiedByIdContainsText(doc, "termsAndConditionsUrl", TermsAndConditionsLocation.asText(application.termsAndConditionsLocation)) shouldBe true
      elementExistsContainsText(doc, "td", "Agreed by test@example.com") shouldBe hasTermsOfUseAgreement
    }

    def changeDetailsShouldRenderThePage(application: Application) = {
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.callChangeDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.Details.changeDetailsAction(application.id).url) shouldBe true
      linkExistsWithHref(doc, routes.Details.details(application.id).url) shouldBe true
      inputExistsWithValue(doc, "applicationId", "hidden", application.id.value) shouldBe true
      if (application.deployedTo == Environment.SANDBOX || application.state.name == State.TESTING) {
        inputExistsWithValue(doc, "applicationName", "text", application.name) shouldBe true
      } else {
        inputExistsWithValue(doc, "applicationName", "hidden", application.name) shouldBe true
      }
      textareaExistsWithText(doc, "description", application.description.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "privacyPolicyUrl", "text", PrivacyPolicyLocation.asText(application.privacyPolicyLocation)) shouldBe true
      inputExistsWithValue(doc, "termsAndConditionsUrl", "text", TermsAndConditionsLocation.asText(application.termsAndConditionsLocation)) shouldBe true
    }

    def changeDetailsShouldRedirectOnSuccess(application: Application) = {
      givenApplicationAction(application, loggedInDeveloper)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(application.id).url)
    }

    def changeDetailsShouldUpdateTheApplication(application: Application) = {
      givenApplicationAction(application, loggedInDeveloper)

      await(
        application
          .withName(newName)
          .withDescription(newDescription)
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

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

      final def toForm = EditApplicationForm(app.id, app.name, app.description, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl, app.grantLengthDisplayValue)

      final def callDetails: Future[Result] = underTest.details(app.id)(loggedInDevRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.details(app.id)(loggedOutRequest)

      final def callChangeDetails: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedInDevRequest)

      final def callChangeDetailsNotLoggedIn: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedOutRequest)

      final def callChangeDetailsAction: Future[Result] = callChangeDetailsAction(loggedInDevRequest)

      final def callChangeDetailsActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      private final def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toForm)))
      }
    }
  }
}
