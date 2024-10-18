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
import org.mockito.captor.ArgCaptor
import views.html._
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommand
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment, LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class DetailsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures {

  val approvedApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val sandboxApplication  = approvedApplication.inSandbox()
  val inTestingApp        = approvedApplication.withState(appStateTesting)

  "details" when {
    "logged in as a Developer on an application" should {

      "return the view for a standard production app with no change link" in new Setup {
        detailsShouldRenderThePage(devSession)(approvedApplication, hasChangeButton = false)
      }

      "return the view for a standard production app with terms of use not agreed" in new Setup {
        detailsShouldRenderThePage(devSession)(approvedApplication, hasChangeButton = false, hasTermsOfUseAgreement = false)
      }

      "return the view for a developer on a sandbox app" in new Setup {
        detailsShouldRenderThePage(devSession)(sandboxApplication, hasTermsOfUseAgreement = false)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePage(adminSession)(approvedApplication)
      }

      "return the view for an admin on a sandbox app" in new Setup {
        detailsShouldRenderThePage(adminSession)(sandboxApplication, hasTermsOfUseAgreement = false)
      }

      "return a redirect when using an application in testing state" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        givenApplicationAction(inTestingApp, adminSession)

        val result = inTestingApp.callDetails

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${inTestingApp.id.value}/production-credentials-checklist")
      }

      "return a bad request when using an application in testing state for the old journey" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        givenApplicationAction(inTestingApp, adminSession)

        val result = inTestingApp.callDetails

        status(result) shouldBe BAD_REQUEST
      }

      "return the credentials requested page on an application pending approval" in new Setup {
        val pendingApprovalApplication = standardApp.withState(appStatePendingGatekeeperApproval)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        givenApplicationAction(pendingApprovalApplication, adminSession)

        val result = addToken(underTest.details(pendingApprovalApplication.id))(loggedInDevRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${pendingApprovalApplication.id.value}/view-answers")
      }

      "return the credentials requested page on an application pending verification" in new Setup {
        val pendingVerificationApplication = standardApp.withState(appStatePendingRequesterVerification)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        givenApplicationAction(pendingVerificationApplication, adminSession)

        val result = addToken(underTest.details(pendingVerificationApplication.id))(loggedInDevRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${pendingVerificationApplication.id.value}/view-answers")
      }

      "return a bad request on an application pending verification for the old journey" in new Setup {
        val pendingVerificationApplication = standardApp.withState(appStatePendingRequesterVerification)
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        givenApplicationAction(pendingVerificationApplication, adminSession)

        val result = addToken(underTest.details(pendingVerificationApplication.id))(loggedInDevRequest)

        status(result) shouldBe BAD_REQUEST
      }

      "redirect to the Start Using Your Application page on an application in pre-production state" in new Setup {
        val preProdApplication = standardApp.withState(appStatePreProduction)

        givenApplicationAction(preProdApplication, adminSession)

        val result = addToken(underTest.details(preProdApplication.id))(loggedInDevRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.StartUsingYourApplicationController.startUsingYourApplicationPage(preProdApplication.id).url
        )
      }

      "display the Application Details page for an application in pre-production state when the forceAppDetails parameter is used" in new Setup {
        val preProdApplication = standardApp.withState(appStatePreProduction)

        returnAgreementDetails()
        givenApplicationAction(preProdApplication, adminSession)
        val loggedInRequestWithForceAppDetailsParam =
          FakeRequest("GET", "/?forceAppDetails").withLoggedIn(underTest, implicitly)(altDevSession.sessionId).withSession(sessionParams: _*)
        val result                                  = addToken(underTest.details(preProdApplication.id))(loggedInRequestWithForceAppDetailsParam)

        status(result) shouldBe OK
        val document = Jsoup.parse(contentAsString(result))
        elementExistsByText(document, "h1", "Application details") shouldBe true
      }
    }

    "not a team member on an application" should {
      "return not found" in new Setup {
        val application = approvedApplication
        givenApplicationAction(application, altDevSession)

        val result = application.callDetails

        status(result) shouldBe NOT_FOUND
      }
    }

    "not logged in" should {
      "redirect to login" in new Setup {
        val application = approvedApplication
        givenApplicationAction(application, devSession)

        val result = application.callDetailsNotLoggedIn

        redirectsToLogin(result)
      }
    }
  }

  "changeDetails" should {
    "return forbidden for an admin on a standard production app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeDetails

      status(result) shouldBe FORBIDDEN
    }

    "return the view for a developer on a sandbox app" in new Setup {
      changeDetailsShouldRenderThePage(devSession)(
        sandboxApplication
      )
    }

    "return the view for an admin on a sandbox app" in new Setup {
      changeDetailsShouldRenderThePage(adminSession)(
        sandboxApplication
      )
    }

    "return forbidden for a developer on a standard production app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeDetails

      status(result) shouldBe FORBIDDEN
    }

    "return not found when not a teamMember on the app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, altDevSession)

      val result = application.callChangeDetails

      status(result) shouldBe NOT_FOUND
    }

    "redirect to login when not logged in" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.callDetailsNotLoggedIn

      redirectsToLogin(result)
    }

    "return bad request for an ROPC application" in new Setup {
      val application = ropcApp
      givenApplicationAction(application, devSession)

      val result = underTest.changeDetails(application.id)(loggedInDevRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request for a privileged application" in new Setup {
      val application = privilegedApp
      givenApplicationAction(application, devSession)

      val result = underTest.changeDetails(application.id)(loggedInDevRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "changeDetailsAction validation" should {
    "not pass when application is updated with empty name" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("")).callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }

    "not pass when application is updated with invalid name" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("a")).callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }

    "update name which contain HMRC should fail" in new Setup {
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(Invalid.invalidName))

      val application = sandboxApplication
      givenApplicationAction(application, adminSession)

      val result = application.withName(ApplicationName("my invalid HMRC application name")).callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST

      verify(underTest.applicationService).isApplicationNameValid(eqTo("my invalid HMRC application name"), eqTo(application.deployedTo), eqTo(Some(application.id)))(
        *
      )
    }
  }

  "changeDetailsAction for production app in testing state" should {

    "return not found due to not being in a state of production" in new Setup {
      val application = sandboxApplication.withState(appStateTesting)
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("")).callChangeDetailsAction

      status(result) shouldBe NOT_FOUND
    }

    "return not found when not a teamMember on the app" in new Setup {
      val application = sandboxApplication.withCollaborators()
      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe NOT_FOUND
    }

    "redirect to login when not logged in" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeDetailsActionNotLoggedIn

      redirectsToLogin(result)
    }
  }

  "changeDetailsAction for production app in uplifted state" should {

    "return forbidden for a developer" in new Setup {
      val application = approvedApplication

      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden for an admin" in new Setup {
      val application = approvedApplication

      givenApplicationAction(application, adminSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeDetailsAction for sandbox app" should {

    "redirect to the details page on success for an admin" in new Setup {
      changeDetailsShouldRedirectOnSuccess(adminSession)(sandboxApplication)
    }

    "redirect to the details page on success for a developer" in new Setup {
      changeDetailsShouldRedirectOnSuccess(devSession)(sandboxApplication)
    }

    "update all fields for an admin" in new Setup {
      changeDetailsShouldUpdateTheApplication(adminSession)(sandboxApplication)
    }

    "update all fields for a developer" in new Setup {
      changeDetailsShouldUpdateTheApplication(adminSession)(sandboxApplication)
    }

    "update the app but not the check information" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, adminSession)

      await(application.withName(newName).callChangeDetailsAction)

      verify(underTest.applicationService, times(1)).dispatchCmd(*[ApplicationId], *)(*)
      verify(underTest.applicationService, never).updateCheckInformation(eqTo(application), any[CheckInformation])(*)
    }
  }

  "changeOfApplicationName" should {
    "show page successfully" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      val result = addToken(underTest.requestChangeOfAppName(approvedApplication.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Application name")
    }

    "return forbidden when not an admin" in new Setup {
      givenApplicationAction(approvedApplication, devSession)

      val result = addToken(underTest.requestChangeOfAppName(approvedApplication.id))(loggedInDevRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeOfApplicationNameAction" should {
    "show success page if name changed successfully" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      when(underTest.applicationService.requestProductonApplicationNameChange(*[UserId], *, *[ApplicationName], *, *[LaxEmailAddress])(*))
        .thenReturn(Future.successful(TicketCreated))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "Legal new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe OK
      verify(underTest.applicationService).requestProductonApplicationNameChange(*[UserId], *, *[ApplicationName], *, *[LaxEmailAddress])(*)
      contentAsString(result) should include("We have received your request to change the application name to")
    }

    "show error if application name is not valid" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(Invalid(true, false)))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "HMRC - Illegal new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must not include HMRC or HM Revenue and Customs")
    }

    "show error if application name is too short" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must be between 2 and 50 characters and only use ASCII characters excluding")
    }

    "show error if application name contains non-allowed chars" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "name£")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Application name must be between 2 and 50 characters and only use ASCII characters excluding")
    }

    "show error if new application name is the same as the old one" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> approvedApplication.name.value)

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("The application already has the specified name")
    }

    "show forbidden if not an admin" in new Setup {
      givenApplicationAction(approvedApplication, devSession)

      private val request = loggedInDevRequest.withFormUrlEncodedBody("applicationName" -> "new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "changing privacy policy location for old journey applications" should {

    "display update page with url field populated" in new Setup {
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(Some(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", privacyPolicyUrl)
      elementExistsById(document, "privacyPolicyInDesktop") shouldBe false
      elementExistsById(document, "privacyPolicyHasUrl") shouldBe false
    }

    "display update page with url field empty if app has no privacy policy" in new Setup {
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(None)
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", "")
      elementExistsById(document, "privacyPolicyInDesktop") shouldBe false
      elementExistsById(document, "privacyPolicyHasUrl") shouldBe false
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val newPrivacyPolicyUrl  = "http://example.com/new-priv-policy"
      val appWithPrivPolicyUrl = legacyAppWithPrivacyPolicyLocation(Some(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)
      when(applicationServiceMock.updatePrivacyPolicyLocation(eqTo(appWithPrivPolicyUrl), *[UserId], eqTo(PrivacyPolicyLocations.Url(newPrivacyPolicyUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> newPrivacyPolicyUrl, "isInDesktop" -> "false", "isNewJourney" -> "false")

      val result = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyUrl.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appWithPrivPolicyUrl.id).url)
    }
  }

  "changing privacy policy location for new journey applications" should {

    "display update page with 'in desktop' radio selected" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe true
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe false
    }

    "display update page with 'url' radio selected" in new Setup {
      val appWithPrivPolicyUrl = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.Url(privacyPolicyUrl))
      givenApplicationAction(appWithPrivPolicyUrl, adminSession)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "privacyPolicyInDesktop", "checked") shouldBe false
      elementExistsByIdWithAttr(document, "privacyPolicyHasUrl", "checked") shouldBe true
      elementIdentifiedByIdContainsValue(document, "privacyPolicyUrl", privacyPolicyUrl)
    }

    "return Not Found error if application cannot be retrieved" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationActionReturnsNotFound(appWithPrivPolicyInDesktop.id)

      val result = addToken(underTest.updatePrivacyPolicyLocation(appWithPrivPolicyInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return bad request if privacy policy location has not changed" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> "", "isInDesktop" -> "true", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request if form data is invalid" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> "", "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val appWithPrivPolicyInDesktop = appWithPrivacyPolicyLocation(PrivacyPolicyLocations.InDesktopSoftware)
      givenApplicationAction(appWithPrivPolicyInDesktop, adminSession)
      when(applicationServiceMock.updatePrivacyPolicyLocation(eqTo(appWithPrivPolicyInDesktop), *[UserId], eqTo(PrivacyPolicyLocations.Url(privacyPolicyUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("privacyPolicyUrl" -> privacyPolicyUrl, "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithPrivPolicyInDesktop.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appWithPrivPolicyInDesktop.id).url)
    }
  }

  "changing terms and conditions location for old journey applications" should {
    "display update page with url field populated" in new Setup {
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(Some(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", termsAndConditionsUrl)
      elementExistsById(document, "termsAndConditionsInDesktop") shouldBe false
      elementExistsById(document, "termsAndConditionsHasUrl") shouldBe false
    }

    "display update page with url field empty if app has no terms and conditions" in new Setup {
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(None)
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", "")
      elementExistsById(document, "termsAndConditionsInDesktop") shouldBe false
      elementExistsById(document, "termsAndConditionsHasUrl") shouldBe false
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val newTermsAndConditionsUrl     = "http://example.com/new-terms-conds"
      val appWithTermsAndConditionsUrl = legacyAppWithTermsAndConditionsLocation(Some(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)
      when(applicationServiceMock.updateTermsConditionsLocation(eqTo(appWithTermsAndConditionsUrl), *[UserId], eqTo(TermsAndConditionsLocations.Url(newTermsAndConditionsUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> newTermsAndConditionsUrl, "isInDesktop" -> "false", "isNewJourney" -> "false")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsUrl.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appWithTermsAndConditionsUrl.id).url)
    }
  }

  "changing terms and conditions location for new journey applications" should {

    "display update page with 'in desktop' radio selected" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "termsAndConditionsInDesktop", "checked") shouldBe true
      elementExistsByIdWithAttr(document, "termsAndConditionsHasUrl", "checked") shouldBe false
    }

    "display update page with 'url' radio selected" in new Setup {
      val appWithTermsAndConditionsUrl = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.Url(termsAndConditionsUrl))
      givenApplicationAction(appWithTermsAndConditionsUrl, adminSession)

      val result = addToken(underTest.updateTermsAndConditionsLocation(appWithTermsAndConditionsUrl.id))(loggedInAdminRequest)

      status(result) shouldBe OK
      val document = Jsoup.parse(contentAsString(result))
      elementExistsByIdWithAttr(document, "termsAndConditionsInDesktop", "checked") shouldBe false
      elementExistsByIdWithAttr(document, "termsAndConditionsHasUrl", "checked") shouldBe true
      elementIdentifiedByIdContainsValue(document, "termsAndConditionsUrl", termsAndConditionsUrl)
    }

    "return Not Found error if application cannot be retrieved" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationActionReturnsNotFound(appWithTermsAndConditionsInDesktop.id)

      val result = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(loggedInAdminRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return bad request if terms and conditions location has not changed" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> "", "isInDesktop" -> "true", "isNewJourney" -> "true")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request if form data is invalid" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> "", "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updatePrivacyPolicyLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe BAD_REQUEST
    }

    "update location if form data is valid and return to app details page" in new Setup {
      val appWithTermsAndConditionsInDesktop = appWithTermsAndConditionsLocation(TermsAndConditionsLocations.InDesktopSoftware)
      givenApplicationAction(appWithTermsAndConditionsInDesktop, adminSession)
      when(applicationServiceMock.updateTermsConditionsLocation(eqTo(appWithTermsAndConditionsInDesktop), *[UserId], eqTo(TermsAndConditionsLocations.Url(termsAndConditionsUrl)))(*))
        .thenReturn(Future.successful(ApplicationUpdateSuccessful))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("termsAndConditionsUrl" -> termsAndConditionsUrl, "isInDesktop" -> "false", "isNewJourney" -> "true")
      val result          = addToken(underTest.updateTermsAndConditionsLocationAction(appWithTermsAndConditionsInDesktop.id))(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(appWithTermsAndConditionsInDesktop.id).url)
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseServiceMock {

    val unauthorisedAppDetailsView              = app.injector.instanceOf[UnauthorisedAppDetailsView]
    val detailsView                             = app.injector.instanceOf[DetailsView]
    val changeDetailsView                       = app.injector.instanceOf[ChangeDetailsView]
    val requestChangeOfApplicationNameView      = app.injector.instanceOf[RequestChangeOfApplicationNameView]
    val changeOfApplicationNameConfirmationView = app.injector.instanceOf[ChangeOfApplicationNameConfirmationView]
    val updatePrivacyPolicyLocationView         = app.injector.instanceOf[UpdatePrivacyPolicyLocationView]
    val updateTermsAndConditionsLocationView    = app.injector.instanceOf[UpdateTermsAndConditionsLocationView]

    val underTest = new Details(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      unauthorisedAppDetailsView,
      detailsView,
      changeDetailsView,
      requestChangeOfApplicationNameView,
      changeOfApplicationNameConfirmationView,
      updatePrivacyPolicyLocationView,
      updateTermsAndConditionsLocationView,
      fraudPreventionConfig,
      SubmissionServiceMock.aMock,
      termsOfUseServiceMock
    )

    val newName        = ApplicationName("new name")
    val newDescription = Some("new description")
    val newTermsUrl    = Some("http://example.com/new-terms")
    val newPrivacyUrl  = Some("http://example.com/new-privacy")

    val termsAndConditionsUrl = "http://example.com/terms-conds"
    val privacyPolicyUrl      = "http://example.com/priv-policy"

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(Valid))

    when(underTest.applicationService.dispatchCmd(*[ApplicationId], *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    when(underTest.applicationService.updateCheckInformation(any[ApplicationWithCollaborators], any[CheckInformation])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    def legacyAppWithTermsAndConditionsLocation(maybeTermsAndConditionsUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, maybeTermsAndConditionsUrl, None, Set.empty, None, None))

    def legacyAppWithPrivacyPolicyLocation(maybePrivacyPolicyUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, None, maybePrivacyPolicyUrl, Set.empty, None, None))

    def appWithTermsAndConditionsLocation(termsAndConditionsLocation: TermsAndConditionsLocation) = standardApp.withAccess(
      Access.Standard(
        List.empty,
        None,
        None,
        Set.empty,
        None,
        Some(
          ImportantSubmissionData(
            None,
            ResponsibleIndividual(FullName("bob example"), "bob@example.com".toLaxEmail),
            Set.empty,
            termsAndConditionsLocation,
            PrivacyPolicyLocations.InDesktopSoftware,
            List.empty
          )
        )
      )
    )

    def appWithPrivacyPolicyLocation(privacyPolicyLocation: PrivacyPolicyLocation) = standardApp.withAccess(
      Access.Standard(
        List.empty,
        None,
        None,
        Set.empty,
        None,
        Some(
          ImportantSubmissionData(
            None,
            ResponsibleIndividual(FullName("bob example"), "bob@example.com".toLaxEmail),
            Set.empty,
            TermsAndConditionsLocations.InDesktopSoftware,
            privacyPolicyLocation,
            List.empty
          )
        )
      )
    )

    def captureApplicationCmd: ApplicationCommand = {
      val captor = ArgCaptor[ApplicationCommand]
      verify(underTest.applicationService).dispatchCmd(*[ApplicationId], captor)(*)
      captor.value
    }

    def captureAllApplicationCmds: List[ApplicationCommand] = {
      val captor = ArgCaptor[ApplicationCommand]
      verify(underTest.applicationService, atLeast(1)).dispatchCmd(*[ApplicationId], captor)(*)
      captor.values
    }

    def redirectsToLogin(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    def detailsShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators, hasChangeButton: Boolean = true, hasTermsOfUseAgreement: Boolean = true) = {
      givenApplicationAction(application, userSession)

      if (hasTermsOfUseAgreement) {
        returnAgreementDetails(TermsOfUseAgreementDetails("test@example.com".toLaxEmail, Some("Timmy Test"), instant, None))
      } else {
        returnAgreementDetails()
      }

      val result = application.callDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      // APIS-5669 - temporarily removed Change link
      // linkExistsWithHref(doc, routes.Details.changeDetails(application.id).url) shouldBe hasChangeButton
      withClue("id")(elementIdentifiedByIdContainsText(doc, "applicationId", application.id.toString()) shouldBe true)
      withClue("name")(elementIdentifiedByIdContainsText(doc, "applicationName", application.name.value) shouldBe true)
      withClue("description")(elementIdentifiedByIdContainsText(doc, "description", application.details.description.getOrElse("None")) shouldBe true)
      withClue("privacy")(elementIdentifiedByIdContainsText(doc, "privacyPolicyUrl", application.privacyPolicyLocation.value.describe()) shouldBe true)
      withClue("t&c")(elementIdentifiedByIdContainsText(doc, "termsAndConditionsUrl", application.termsAndConditionsLocation.value.describe()) shouldBe true)
      withClue("terms of use")(elementExistsContainsText(doc, "p", "Timmy Test agreed to version 2 of the terms of use on") shouldBe hasTermsOfUseAgreement)
    }

    def changeDetailsShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.callChangeDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.Details.changeDetailsAction(application.id).url) shouldBe true
      linkExistsWithHref(doc, routes.Details.details(application.id).url) shouldBe true
      inputExistsWithValue(doc, "applicationId", "hidden", application.id.toString()) shouldBe true
      if (application.deployedTo == Environment.SANDBOX || application.state.name == State.TESTING) {
        inputExistsWithValue(doc, "applicationName", "text", application.details.name.value) shouldBe true
      } else {
        inputExistsWithValue(doc, "applicationName", "hidden", application.details.name.value) shouldBe true
      }
      textareaExistsWithText(doc, "description", application.details.description.getOrElse("None")) shouldBe true
      inputExistsWithValue(doc, "privacyPolicyUrl", "text", application.privacyPolicyLocation.value.describe()) shouldBe true
      inputExistsWithValue(doc, "termsAndConditionsUrl", "text", application.termsAndConditionsLocation.value.describe()) shouldBe true
    }

    def changeDetailsShouldRedirectOnSuccess(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.Details.details(application.id).url)
    }

    implicit class AppAugment(val app: ApplicationWithCollaborators) {
      final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))

      final def withTermsAndConditionsUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(termsAndConditionsUrl = url))

      final def withPrivacyPolicyUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(privacyPolicyUrl = url))
    }

    def changeDetailsShouldUpdateTheApplication(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      await(
        application
          .withName(newName)
          .withDescription(newDescription)
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

      captureAllApplicationCmds
    }

    implicit val format: OFormat[EditApplicationForm] = Json.format[EditApplicationForm]

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithCollaborators) {
      private val appAccess = app.access.asInstanceOf[Access.Standard]

      final def toForm =
        EditApplicationForm(app.id, app.details.name.value, app.details.description, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl, app.details.grantLength.show())

      final def callDetails: Future[Result] = underTest.details(app.id)(loggedInDevRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.details(app.id)(loggedOutRequest)

      final def callChangeDetails: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedInDevRequest)

      final def callChangeDetailsNotLoggedIn: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedOutRequest)

      final def callChangeDetailsAction: Future[Result] = callChangeDetailsAction(loggedInDevRequest)

      final def callChangeDetailsActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      final private def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toForm)))
      }
    }
  }
}
