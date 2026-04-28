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

class ChangeAppNameAndDescControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures {

  val approvedApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val sandboxApplication  = approvedApplication.inSandbox()
  val inTestingApp        = approvedApplication.withState(appStateTesting)

  val principalApplication   = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val subordinateApplication = principalApplication.inSandbox()

  "changeAppNameAndDesc" should {
    "return forbidden for an admin on a standard production app" in new Setup {
      val application = principalApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeAppNameAndDescWithToken

      status(result) shouldBe FORBIDDEN
    }

    "return the view for a developer on a sandbox app" in new Setup {
      changeAppNameAndDescShouldRenderThePage(devSession)(
        subordinateApplication
      )
    }

    "return the view for an admin on a sandbox app" in new Setup {
      changeAppNameAndDescShouldRenderThePage(adminSession)(
        subordinateApplication
      )
    }

    "return forbidden for a developer on a standard production app" in new Setup {
      val application = principalApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeAppNameAndDescWithToken

      status(result) shouldBe FORBIDDEN
    }

    "return see other when not a teamMember on the app" in new Setup {
      val application = principalApplication
      givenApplicationAction(application, altDevSession)

      val result = application.callChangeAppNameAndDescWithToken

      status(result) shouldBe SEE_OTHER
    }

    "redirect to login when not logged in" in new Setup {
      val application = subordinateApplication
      givenApplicationAction(application, devSession)

      val result = application.callChangeAppNameAndDescNotLoggedIn

      redirectsToLogin(result)
    }

    "return bad request for an ROPC application" in new Setup {
      val application = ropcApp
      givenApplicationAction(application, devSession)

      val result = underTest.changeAppNameAndDesc(application.id)(loggedInDevRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request for a privileged application" in new Setup {
      val application = privilegedApp
      givenApplicationAction(application, devSession)

      val result = underTest.changeAppNameAndDesc(application.id)(loggedInDevRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "changeAppNameAndDescAction validation" should {
    "not pass when application is updated with empty name" in new Setup {
      val application = subordinateApplication
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("")).callChangeAppNameAndDescAction

      status(result) shouldBe BAD_REQUEST
    }

    "not pass when application is updated with invalid name" in new Setup {
      val application = subordinateApplication
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("a")).callChangeAppNameAndDescAction

      status(result) shouldBe BAD_REQUEST
    }

    "update name which contains HMRC should fail" in new Setup {
      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
        .thenReturn(Future.successful(ApplicationNameValidationResult.Invalid))

      val application = subordinateApplication
      givenApplicationAction(application, adminSession)

      val result = application.withName(ApplicationName("my invalid HMRC application name")).callChangeAppNameAndDescAction

      status(result) shouldBe BAD_REQUEST

      verify(underTest.applicationService).isApplicationNameValid(eqTo("my invalid HMRC application name"), eqTo(application.deployedTo), eqTo(Some(application.id)))(
        *
      )
    }
  }

  "changeAppNameAndDescAction for production app in testing state" should {

    "return not found due to not being in a state of production" in new Setup {
      val application = subordinateApplication.withState(appStateTesting)
      givenApplicationAction(application, devSession)

      val result = application.withName(ApplicationName("")).callChangeAppNameAndDescAction

      status(result) shouldBe NOT_FOUND
    }

    "return see other when not a teamMember on the app" in new Setup {
      val application = subordinateApplication.withCollaborators()
      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction

      status(result) shouldBe SEE_OTHER
    }

    "redirect to login when not logged in" in new Setup {
      val application = principalApplication
      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeAppNameAndDescActionNotLoggedIn

      redirectsToLogin(result)
    }
  }

  "changeAppNameAndDescAction for production app in uplifted state" should {

    "return forbidden for a developer" in new Setup {
      val application = principalApplication

      givenApplicationAction(application, devSession)

      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden for an admin" in new Setup {
      val application = principalApplication

      givenApplicationAction(application, adminSession)

      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeAppNameAndDescAction for sandbox app" should {

    "redirect to the details page on success for an admin" in new Setup {
      changeAppNameAndDescShouldRedirectOnSuccess(adminSession)(subordinateApplication)
    }

    "redirect to the details page on success for a developer" in new Setup {
      changeAppNameAndDescShouldRedirectOnSuccess(devSession)(subordinateApplication)
    }

    "update all fields for an admin" in new Setup {
      changeAppNameAndDescShouldUpdateTheApplication(adminSession)(subordinateApplication)
    }

    "update all fields for a developer" in new Setup {
      changeAppNameAndDescShouldUpdateTheApplication(adminSession)(subordinateApplication)
    }

    "update the app but not the check information" in new Setup {
      val application = subordinateApplication
      givenApplicationAction(application, adminSession)

      await(application.withName(newName).callChangeAppNameAndDescAction)

      verify(underTest.applicationService, times(1)).dispatchCmd(*[ApplicationId], *)(*)
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseServiceMock {

    val changeAppNameAndDescView                = app.injector.instanceOf[ChangeAppNameAndDescView]

    val underTest = new ChangeAppNameAndDescController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      clock,
      changeAppNameAndDescView
    )

    val newName        = ApplicationName("new name")
    val newDescription = Some("new description")

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

    implicit class AppAugment(val app: ApplicationWithCollaborators) {
      final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))
    }

    implicit val changeAppNameAndDescFormFormat: OFormat[ChangeAppNameAndDescForm] = Json.format[ChangeAppNameAndDescForm]

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
  }
}
