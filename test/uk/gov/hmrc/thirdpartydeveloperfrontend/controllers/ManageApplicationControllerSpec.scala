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
import views.html.manageapplication._

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Result
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommand
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageApplicationControllerSpec
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
        detailsShouldRenderThePage(devSession)(approvedApplication)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePage(adminSession)(approvedApplication)
      }
    }

    "not a team member on an application" should {
      "return see other" in new Setup {
        val application = approvedApplication
        givenApplicationAction(application, altDevSession)

        val result = application.callDetails

        status(result) shouldBe SEE_OTHER
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

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseServiceMock {

    val detailsView = app.injector.instanceOf[ApplicationDetailsView]

    val underTest = new ManageApplicationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      detailsView
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

    def detailsShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      returnAgreementDetails()

      val result = application.callDetails

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      withClue("name")(elementIdentifiedByIdContainsText(doc, "applicationName", application.name.value) shouldBe true)
      withClue("environment")(elementIdentifiedByIdContainsText(doc, "environment", application.details.deployedTo.displayText) shouldBe true)
      withClue("description")(elementIdentifiedByIdContainsText(doc, "description", application.details.description.getOrElse("None")) shouldBe true)
      withClue("clientId")(elementIdentifiedByIdContainsText(doc, "clientId", application.details.token.clientId.value) shouldBe true)
      withClue("privacyPolicy")(elementIdentifiedByIdContainsText(
        doc,
        "privacyPolicy",
        application.details.privacyPolicyLocation.getOrElse(PrivacyPolicyLocations.NoneProvided).describe()
      ) shouldBe true)
      withClue("termsAndConditions")(elementIdentifiedByIdContainsText(
        doc,
        "termsAndConditions",
        application.details.termsAndConditionsLocation.getOrElse(TermsAndConditionsLocations.NoneProvided).describe()
      ) shouldBe true)
      withClue("grantLength")(elementIdentifiedByIdContainsText(doc, "grantLength", application.details.grantLength.show()) shouldBe true)
      withClue("subscription")(elementIdentifiedByIdContainsText(doc, "manage-subscriptions", "Change APIs") shouldBe true)
      withClue("delete")(elementIdentifiedByIdContainsText(doc, "delete-link", "Request application deletion") shouldBe true)
    }

    implicit class AppAugment(val app: ApplicationWithCollaborators) {
      final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))

      final def withTermsAndConditionsUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(termsAndConditionsUrl = url))

      final def withPrivacyPolicyUrl(url: Option[String]): ApplicationWithCollaborators = app.withAccess(standardAccess.copy(privacyPolicyUrl = url))
    }

    implicit val format: OFormat[EditApplicationForm] = Json.format[EditApplicationForm]

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithCollaborators) {
      private val appAccess = app.access.asInstanceOf[Access.Standard]

      final def toForm =
        EditApplicationForm(app.id, app.details.name.value, app.details.description, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl, app.details.grantLength.show())

      final def callDetails: Future[Result] = underTest.applicationDetails(app.id)(loggedInDevRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.applicationDetails(app.id)(loggedOutRequest)
    }
  }
}
