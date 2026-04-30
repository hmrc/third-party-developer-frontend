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

import org.jsoup.Jsoup
import org.mockito.captor.ArgCaptor
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommand
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment, LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.html._
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView
import views.html.manageapplication.ChangeAppNameAndDescView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class RequestChangeOfApplicationNameControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures {

  val approvedApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val sandboxApplication  = approvedApplication.inSandbox()
  val inTestingApp        = approvedApplication.withState(appStateTesting)

  val principalApplication   = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val subordinateApplication = principalApplication.inSandbox()

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
        .thenReturn(Future.successful(Some("ref")))

      private val request = loggedInAdminRequest.withFormUrlEncodedBody("applicationName" -> "Legal new app name")

      val result = addToken(underTest.requestChangeOfAppNameAction(approvedApplication.id))(request)

      status(result) shouldBe OK
      verify(underTest.applicationService).requestProductonApplicationNameChange(*[UserId], *, *[ApplicationName], *, *[LaxEmailAddress])(*)
      contentAsString(result) should include("We have received your request to change the application name to")
    }

    "show error if application name is not valid" in new Setup {
      givenApplicationAction(approvedApplication, adminSession)
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(ApplicationNameValidationResult.Invalid))

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


  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseServiceMock {

    val unauthorisedAppDetailsView              = app.injector.instanceOf[UnauthorisedAppDetailsView]
    val changeAppNameAndDescView                = app.injector.instanceOf[ChangeAppNameAndDescView]
    val changeDetailsView                       = app.injector.instanceOf[ChangeDetailsView]
    val requestChangeOfApplicationNameView      = app.injector.instanceOf[RequestChangeOfApplicationNameView]
    val changeOfApplicationNameConfirmationView = app.injector.instanceOf[ChangeOfApplicationNameConfirmationView]
    val updatePrivacyPolicyLocationView         = app.injector.instanceOf[UpdatePrivacyPolicyLocationView]
    val updateTermsAndConditionsLocationView    = app.injector.instanceOf[UpdateTermsAndConditionsLocationView]

    val underTest = new RequestChangeOfApplicationNameController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      requestChangeOfApplicationNameView,
      changeOfApplicationNameConfirmationView
    )

    val newName        = ApplicationName("new name")
    val newDescription = Some("new description")
    val newTermsUrl    = Some("http://example.com/new-terms")
    val newPrivacyUrl  = Some("http://example.com/new-privacy")

    val termsAndConditionsUrl = "http://example.com/terms-conds"
    val privacyPolicyUrl      = "http://example.com/priv-policy"

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(ApplicationNameValidationResult.Valid))

    when(underTest.applicationService.dispatchCmd(*[ApplicationId], *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    def changeAppNameAndDescShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.callChangeAppNameAndDescWithToken

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.ChangeAppNameAndDescController.changeAppNameAndDescAction(application.id).url) shouldBe true
      if (application.deployedTo == Environment.SANDBOX || application.state.name == State.TESTING) {
        inputExistsWithValue(doc, "applicationName", "text", application.details.name.value) shouldBe true
      } else {
        inputExistsWithValue(doc, "applicationName", "hidden", application.details.name.value) shouldBe true
      }
      textareaExistsWithText(doc, "description", application.details.description.getOrElse("None")) shouldBe true
    }

    def changeAppNameAndDescShouldRedirectOnSuccess(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(application.id).url)
    }

    def changeAppNameAndDescShouldUpdateTheApplication(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      await(
        application
          .withName(newName)
          .withDescription(newDescription)
          .callChangeAppNameAndDescAction
      )

      captureAllApplicationCmds
    }

    def legacyAppWithTermsAndConditionsLocation(maybeTermsAndConditionsUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, List.empty, maybeTermsAndConditionsUrl, None, Set.empty, None, None))

    def legacyAppWithPrivacyPolicyLocation(maybePrivacyPolicyUrl: Option[String]) =
      standardApp.withAccess(Access.Standard(List.empty, List.empty, None, maybePrivacyPolicyUrl, Set.empty, None, None))

    def appWithTermsAndConditionsLocation(termsAndConditionsLocation: TermsAndConditionsLocation) = standardApp.withAccess(
      Access.Standard(
        List.empty,
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

    def changeDetailsShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.callChangeDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.ApplicationDetailsSectionsController.changeDetailsAction(application.id).url) shouldBe true
      linkExistsWithHref(doc, routes.MainApplicationDetailsController.applicationDetails(application.id).url) shouldBe true
      inputExistsWithValue(doc, "applicationId", "hidden", application.id.toString()) shouldBe true
      inputExistsWithValue(doc, "privacyPolicyUrl", "text", application.privacyPolicyLocation.value.describe()) shouldBe true
      inputExistsWithValue(doc, "termsAndConditionsUrl", "text", application.termsAndConditionsLocation.value.describe()) shouldBe true
    }

    def changeDetailsShouldRedirectOnSuccess(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.MainApplicationDetailsController.applicationDetails(application.id).url)
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
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

      captureAllApplicationCmds
    }

    implicit val changeAppNameAndDescFormFormat: OFormat[ChangeAppNameAndDescForm] = Json.format[ChangeAppNameAndDescForm]

    implicit val editApplicationFormFormat: OFormat[EditApplicationForm] = Json.format[EditApplicationForm]

    implicit class ChangeAppNameAndDescAppAugment(val app: ApplicationWithCollaborators) {

      final def toChangeAppNameAndDescForm =
        ChangeAppNameAndDescForm(app.details.name.value, app.details.description)

      final def callChangeAppNameAndDesc: Future[Result] = underTest.changeAppNameAndDesc(app.id)(loggedInDevRequest)

      final def callChangeAppNameAndDescNotLoggedIn: Future[Result] = underTest.changeAppNameAndDesc(app.id)(loggedOutRequest)

      final def callChangeAppNameAndDescWithToken: Future[Result] = addToken(underTest.changeAppNameAndDesc(app.id))(loggedInDevRequest)

      final def callChangeAppNameAndDescNotLoggedInWithToken: Future[Result] = addToken(underTest.changeAppNameAndDesc(app.id))(loggedOutRequest)

      final def callChangeAppNameAndDescAction: Future[Result] = callChangeAppNameAndDescAction(loggedInDevRequest)

      final def callChangeAppNameAndDescActionNotLoggedIn: Future[Result] = callChangeAppNameAndDescAction(loggedOutRequest)

      final private def callChangeAppNameAndDescAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeAppNameAndDescAction(app.id))(request.withJsonBody(Json.toJson(app.toChangeAppNameAndDescForm)))
      }
    }

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithCollaborators) {
      private val appAccess = app.access.asInstanceOf[Access.Standard]

      final def toEditApplicationForm =
        EditApplicationForm(app.id, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl)

      final def callChangeDetails: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedInDevRequest)

      final def callChangeDetailsNotLoggedIn: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedOutRequest)

      final def callChangeDetailsAction: Future[Result] = callChangeDetailsAction(loggedInDevRequest)

      final def callChangeDetailsActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      final private def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toEditApplicationForm)))
      }
    }
  }
}
