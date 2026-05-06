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

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommand
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class UpdateTCAndPrivPolicyURLControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures {

  val approvedApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val sandboxApplication  = approvedApplication.inSandbox()

  "changeDetails" should {
    "return forbidden for a developer on a standard production app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeDetails

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden for an admin on a standard production app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, adminSession)

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

    "return see other when not a teamMember on the app" in new Setup {
      val application = approvedApplication
      givenApplicationAction(application, altDevSession)

      val result = application.callChangeDetails

      status(result) shouldBe SEE_OTHER
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
    "pass with valid privacy policy URL" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withPrivacyPolicyUrl(Some("http://example.com/privacy")).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
    }

    "pass with valid terms and conditions URL" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withTermsAndConditionsUrl(Some("http://example.com/terms")).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
    }

    "fail with invalid privacy policy URL" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withPrivacyPolicyUrl(Some("not-a-url")).callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }

    "fail with invalid terms and conditions URL" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      val result = application.withTermsAndConditionsUrl(Some("not-a-url")).callChangeDetailsAction

      status(result) shouldBe BAD_REQUEST
    }
  }

  "changeDetailsAction for production app in testing state" should {

    "return not found due to not being in a state of production" in new Setup {
      val application = sandboxApplication.withState(appStateTesting)
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("")).callChangeDetailsAction

      status(result) shouldBe NOT_FOUND
    }

    "return see other when not a teamMember on the app" in new Setup {
      val application = sandboxApplication.withCollaborators()
      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeDetailsAction

      status(result) shouldBe SEE_OTHER
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

    "update privacy policy and terms & conditions for an admin" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, adminSession)

      await(
        application
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

      val cmds = captureAllApplicationCmds
      cmds.size shouldBe 2
    }

    "update privacy policy and terms & conditions for a developer" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, devSession)

      await(
        application
          .withTermsAndConditionsUrl(newTermsUrl)
          .withPrivacyPolicyUrl(newPrivacyUrl)
          .callChangeDetailsAction
      )

      val cmds = captureAllApplicationCmds
      cmds.size shouldBe 2
    }

    "dispatch no commands when values unchanged" in new Setup {
      val application = sandboxApplication
      givenApplicationAction(application, adminSession)

      await(application.callChangeDetailsAction)

      verify(underTest.applicationService, times(0)).dispatchCmd(*[ApplicationId], *)(*)
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {

    val updateTCAndPrivPolicyURLView = app.injector.instanceOf[UpdateTCAndPrivPolicyURLView]

    val underTest = new UpdateTCAndPrivPolicyURLController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      updateTCAndPrivPolicyURLView
    )

    val newDescription = Some("new description")
    val newTermsUrl    = Some("http://example.com/new-terms")
    val newPrivacyUrl  = Some("http://example.com/new-privacy")

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(ApplicationNameValidationResult.Valid))

    when(underTest.applicationService.dispatchCmd(*[ApplicationId], *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

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
      formExistsWithAction(doc, routes.UpdateTCAndPrivPolicyURLController.changeDetailsAction(application.id).url) shouldBe true
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

    implicit val editApplicationFormFormat: OFormat[EditApplicationForm] = Json.format[EditApplicationForm]

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithCollaborators) {
      private val appAccess = app.access.asInstanceOf[Access.Standard]

      final def toEditApplicationForm =
        EditApplicationForm(app.id, appAccess.privacyPolicyUrl, appAccess.termsAndConditionsUrl)

      final def callChangeDetails: Future[Result] = addToken(underTest.changeDetails(app.id))(loggedInDevRequest)

      final def callChangeDetailsAction: Future[Result] = callChangeDetailsAction(loggedInDevRequest)

      final def callChangeDetailsActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      final private def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeDetailsAction(app.id))(request.withJsonBody(Json.toJson(app.toEditApplicationForm)))
      }
    }
  }
}
