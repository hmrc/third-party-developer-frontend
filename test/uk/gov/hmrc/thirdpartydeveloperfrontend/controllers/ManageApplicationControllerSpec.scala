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
import org.jsoup.nodes.Document
import org.mockito.captor.ArgCaptor
import org.scalatest.Assertion
import play.api.libs.json.{Json, OFormat}
import views.html.manageapplication._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommand
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models.FieldDefinitionType
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageApplicationControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures
    with ApplicationWithSubscriptionFieldsData
    with CheckInformationFixtures
    with ResponsibleIndividualFixtures
    with SubscriptionTestHelper {

  val approvedApplication       = appWithSubsFieldsOne
    .withAccess(standardAccessOne)
    .withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description")))
  val sandboxApplication        = approvedApplication.inSandbox()
  val inTestingApp              = approvedApplication.withState(appStateTesting)
  val productionPrivApplication = approvedApplication.withAccess(privilegedAccess)
  val productionRopcApplication = approvedApplication.withAccess(ropcAccess)

  val principalApplication = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val subordinateApplication  = principalApplication.inSandbox()

  val prodAppWithRespIndAndV1TermsOfUse = appWithSubsFieldsOne.withAccess(standardAccessOne).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description"), checkInformation = Some(checkInformationOne)))

  val prodAppWithRespIndAndV2TermsOfUse = appWithSubsFieldsOne.withAccess(standardAccessWithSubmission).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description")))

  val v1Agreement: TermsOfUseAgreementDetails = prodAppWithRespIndAndV1TermsOfUse.details.checkInformation.map((checkInfo: CheckInformation) =>
    checkInfo.termsOfUseAgreements.map((toua: TermsOfUseAgreement) =>
      TermsOfUseAgreementDetails(toua.emailAddress, None, toua.timeStamp, Some(toua.version))
    )
  ).get.head
  val v1AgreementWording: String              = s"Agreed by ${v1Agreement.name.getOrElse(v1Agreement.emailAddress)} on ${DateFormatter.formatTwoDigitDay(v1Agreement.date)}"

  val v2Agreement = TermsOfUseAgreementDetails(
    TermsOfUseAcceptanceData.one.responsibleIndividual.emailAddress,
    Some(TermsOfUseAcceptanceData.one.responsibleIndividual.fullName.value),
    TermsOfUseAcceptanceData.one.dateTime,
    None
  )

  val v2AgreementWording =
    s"${v2Agreement.name.getOrElse(v2Agreement.emailAddress)} agreed to version 2 of the terms of use on ${DateFormatter.formatTwoDigitDay(v2Agreement.date)}"

  "details" when {
    "logged in as a Developer on an application" should {
      "return the view for a standard production app with no change link" in new Setup {
        returnAgreementDetails()
        detailsShouldRenderThePageForDeveloper(devSession)(approvedApplication)
      }
      "return the view for a standard production app with V1 terms of use" in new Setup {
        returnAgreementDetails(v1Agreement)
        detailsShouldRenderThePageForDeveloper(devSession, v1TOUWording = Some(v1AgreementWording))(prodAppWithRespIndAndV1TermsOfUse)
      }
      "return the view for a standard production app with V2 terms of use" in new Setup {
        returnAgreementDetails(v2Agreement)
        detailsShouldRenderThePageForDeveloper(devSession, v2TOUWording = Some(v2AgreementWording))(prodAppWithRespIndAndV2TermsOfUse)
      }
      "return the view for a standard sandbox app" in new Setup {
        returnAgreementDetails()
        detailsShouldRenderThePageForAdminOrSandbox(devSession)(sandboxApplication)
      }
      "return the view for a Privileged production app" in new Setup {
        returnAgreementDetails()
        detailsShouldRenderThePageForDeveloper(devSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        returnAgreementDetails()
        detailsShouldRenderThePageForDeveloper(devSession)(productionRopcApplication)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        returnAgreementDetails()
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(approvedApplication)
      }
      "return the view for a standard production app with link to view V1 terms of use" in new Setup {
        returnAgreementDetails(v1Agreement)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession, v1TOUWording = Some(v1AgreementWording))(prodAppWithRespIndAndV1TermsOfUse)
      }
      "return the view for a Privileged production app" in new Setup {
        returnAgreementDetails()
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        returnAgreementDetails()
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(productionRopcApplication)
      }
    }

    "not a team member on an application" should {
      "return see other" in new Setup {
        val application = approvedApplication
        givenApplicationAction(application, altDevSession)

        val result = application.callDetailsDev

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

//  "changeAppNameAndDescActionAction validation" should {
//    "not pass when application is updated with empty name" in new Setup {
//      val application = subordinateApplication
//      givenApplicationAction(application, devSession)
//
//      val result = application.withName(ApplicationName("")).callChangeAppNameAndDescAction
//
//      status(result) shouldBe BAD_REQUEST
//    }
//
//    "not pass when application is updated with invalid name" in new Setup {
//      val application = subordinateApplication
//      givenApplicationAction(application, devSession)
//
//      val result = application.withName(ApplicationName("a")).callChangeAppNameAndDescAction
//
//      status(result) shouldBe BAD_REQUEST
//    }
//
//    "update name which contain HMRC should fail" in new Setup {
//      when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
//        .thenReturn(Future.successful(ApplicationNameValidationResult.Invalid))
//
//      val application = subordinateApplication
//      givenApplicationAction(application, adminSession)
//
//      val result = application.withName(ApplicationName("my invalid HMRC application name")).callChangeAppNameAndDescAction
//
//      status(result) shouldBe BAD_REQUEST
//
//      verify(underTest.applicationService).isApplicationNameValid(eqTo("my invalid HMRC application name"), eqTo(application.deployedTo), eqTo(Some(application.id)))(
//        *
//      )
//    }
//  }
//
//  "changeDetailsAction for production app in testing state" should {
//
//    "return not found due to not being in a state of production" in new Setup {
//      val application = subordinateApplication.withState(appStateTesting)
//      givenApplicationAction(application, devSession)
//
//      val result = application.withName(ApplicationName("")).callChangeAppNameAndDescAction
//
//      status(result) shouldBe NOT_FOUND
//    }
//
//    "return see other when not a teamMember on the app" in new Setup {
//      val application = subordinateApplication.withCollaborators()
//      givenApplicationAction(application, devSession)
//
//      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction
//
//      status(result) shouldBe SEE_OTHER
//    }
//
//    "redirect to login when not logged in" in new Setup {
//      val application = principalApplication
//      givenApplicationAction(application, devSession)
//
//      val result = application.withDescription(newDescription).callChangeAppNameAndDescActionNotLoggedIn
//
//      redirectsToLogin(result)
//    }
//  }
//
//  "changeDetailsAction for production app in uplifted state" should {
//
//    "return forbidden for a developer" in new Setup {
//      val application = principalApplication
//
//      givenApplicationAction(application, devSession)
//
//      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction
//
//      status(result) shouldBe FORBIDDEN
//    }
//
//    "return forbidden for an admin" in new Setup {
//      val application = principalApplication
//
//      givenApplicationAction(application, adminSession)
//
//      val result = application.withDescription(newDescription).callChangeAppNameAndDescAction
//
//      status(result) shouldBe FORBIDDEN
//    }
//  }
//
//  "changeDetailsAction for sandbox app" should {
//
//    "redirect to the details page on success for an admin" in new Setup {
//      changeAppNameAndDescShouldRedirectOnSuccess(adminSession)(subordinateApplication)
//    }
//
//    "redirect to the details page on success for a developer" in new Setup {
//      changeAppNameAndDescShouldRedirectOnSuccess(devSession)(subordinateApplication)
//    }
//
//    "update all fields for an admin" in new Setup {
//      changeAppNameAndDescShouldUpdateTheApplication(adminSession)(subordinateApplication)
//    }
//
//    "update all fields for a developer" in new Setup {
//      changeAppNameAndDescShouldUpdateTheApplication(adminSession)(subordinateApplication)
//    }
//
//    "update the app but not the check information" in new Setup {
//      val application = subordinateApplication
//      givenApplicationAction(application, adminSession)
//
//      await(application.withName(newName).callChangeAppNameAndDescAction)
//
//      verify(underTest.applicationService, times(1)).dispatchCmd(*[ApplicationId], *)(*)
//    }
//  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseServiceMock {

    val detailsView                                  = app.injector.instanceOf[ApplicationDetailsView]
    val changeAppNameAndDescView                     = app.injector.instanceOf[ChangeAppNameAndDescView]
    def fraudPreventionConfig: FraudPreventionConfig = FraudPreventionConfig(enabled = true, List(ServiceName("ppns-api")), "/")
    val newName        = ApplicationName("new name")
    val newDescription = Some("new description")

    val underTest = new ManageApplicationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      fraudPreventionConfig,
      termsOfUseServiceMock,
      changeAppNameAndDescView,
      mcc,
      cookieSigner,
      clock,
      detailsView
    )

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(ApplicationNameValidationResult.Valid))

    when(underTest.applicationService.dispatchCmd(*[ApplicationId], *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    def redirectsToLogin(result: Future[Result]): Assertion = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    private def assertCommonAppDetails(application: ApplicationWithSubscriptionFields, doc: Document, v1TOUWording: Option[String] = None, v2TOUWording: Option[String] = None) = {
      withClue("name")(elementIdentifiedByIdContainsText(doc, "applicationName", application.name.value) shouldBe true)
      withClue("environment")(elementIdentifiedByIdContainsText(doc, "environment", application.details.deployedTo.displayText) shouldBe true)
      withClue("description")(elementIdentifiedByIdContainsText(doc, "description", application.details.description.getOrElse("None")) shouldBe true)
      if (application.isStandard) {

        val redirectUriWording = application.access match {
          case Access.Standard(redirectUris, _, _, _, _, _, _) => s"${redirectUris.size} of 5 URIs added"
          case _                                               => "None added"
        }
        withClue("redirectUris")(elementIdentifiedByIdContainsText(doc, "redirectUrisText", redirectUriWording) shouldBe true)
        withClue("delete")(elementIdentifiedByIdContainsText(doc, "delete-link", "Delete application") shouldBe true)
      }
      withClue("ipAllowList")(elementIdentifiedByIdContainsText(
        doc,
        "ipAllowListText",
        s"${application.details.ipAllowlist.allowlist.toList.size} IP address added"
      ) shouldBe true)
      withClue("teamMembers")(elementIdentifiedByIdContainsText(doc, "teamMembers", s"${application.collaborators.size.toString} team members") shouldBe true)
      withClue("grantLength")(elementIdentifiedByIdContainsText(doc, "grantLength", application.details.grantLength.show()) shouldBe true)
      withClue("subscription")(elementIdentifiedByIdContainsText(doc, "manage-subscriptions", "Change APIs") shouldBe true)
      withClue("apiSetupHeading")(elementIdentifiedByIdContainsText(doc, "apiSetupHeading", "API setup") shouldBe true)
      withClue("apiSetupHint")(elementIdentifiedByIdContainsText(
        doc,
        "apiSetupHint",
        "Some of the REST APIs you have added to this application need to be set up before you can use their endpoints."
      ) shouldBe true)
      withClue("apiConfiguration")(elementIdentifiedByIdContainsText(doc, "apiConfiguration", "API configuration") shouldBe true)

      if (application.isProduction) {
        withClue("fraudPrevention")(elementIdentifiedByIdContainsText(doc, "fraudPrevention", "Fraud prevention") shouldBe true)

        v1TOUWording match {
          case Some(wording) =>
            withClue("termsOfUse")(elementIdentifiedByIdContainsText(doc, "termsOfUse", "Terms of use") shouldBe true)
            withClue("termsOfUseAgreementV1")(elementIdentifiedByIdContainsText(doc, "termsOfUseAgreementV1", wording) shouldBe true)
          case _             => succeed
        }
        v2TOUWording match {
          case Some(wording) =>
            withClue("termsOfUse")(elementIdentifiedByIdContainsText(doc, "termsOfUse", "Terms of use") shouldBe true)
            withClue("termsOfUseAgreementV2")(elementIdentifiedByIdContainsText(doc, "termsOfUseAgreementV2", wording) shouldBe true)
            withClue("termsOfUseLinkV2")(elementIdentifiedByIdContainsText(doc, "termsOfUseLinkV2", "View") shouldBe true)
          case _             => succeed
        }
      }

    }

    def detailsShouldRenderThePageForDeveloper(
        userSession: UserSession,
        v1TOUWording: Option[String] = None,
        v2TOUWording: Option[String] = None
      )(
        application: ApplicationWithSubscriptionFields
      ): Any = {

      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields(application.id, application.clientId)("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = FieldDefinitionType.PPNS_FIELD)))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(application, userSession, subsData)

      val result = application.callDetailsDev
      status(result) shouldBe OK
      val doc    = Jsoup.parse(contentAsString(result))

      assertCommonAppDetails(application, doc)
    }

    def detailsShouldRenderThePageForAdminOrSandbox(
        userSession: UserSession,
        v1TOUWording: Option[String] = None,
        v2TOUWording: Option[String] = None
      )(
        application: ApplicationWithSubscriptionFields
      ): Assertion = {

      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields(application.id, application.clientId)("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = FieldDefinitionType.PPNS_FIELD)))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(application, userSession, subsData)

      val result = application.callDetailsAdmin
      status(result) shouldBe OK
      val doc    = Jsoup.parse(contentAsString(result))

      assertCommonAppDetails(application, doc, v1TOUWording, v2TOUWording)

      withClue("clientId")(elementIdentifiedByIdContainsText(doc, "clientId", application.details.token.clientId.value) shouldBe true)
      withClue("createClientSecrets")(elementIdentifiedByIdContainsText(
        doc,
        "createClientSecrets",
        s"${application.details.token.clientSecrets.size} of 5 client secrets created"
      ) shouldBe true)
      withClue("pushSecret")(elementIdentifiedByIdContainsText(doc, "pushSecret", "Push secret") shouldBe true)

      v1TOUWording match {
        case Some(_) =>
          withClue("termsOfUseLinkV1")(elementIdentifiedByIdContainsText(doc, "termsOfUseLinkV1", "View") shouldBe true)
        case _       => succeed
      }
    }

    def changeAppNameAndDescShouldRenderThePage(userSession: UserSession)(application: ApplicationWithCollaborators) = {
      givenApplicationAction(application, userSession)

      val result = application.callChangeAppNameAndDescWithToken

      status(result) shouldBe OK
      val doc = Jsoup.parse(contentAsString(result))
      formExistsWithAction(doc, routes.ManageApplicationController.changeAppNameAndDescAction(application.id).url) shouldBe true
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
      redirectLocation(result) shouldBe Some(routes.Details.details(application.id).url)
    }

    implicit class AppAugment(val app: ApplicationWithCollaborators) {
      final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))
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

    def captureAllApplicationCmds: List[ApplicationCommand] = {
      val captor = ArgCaptor[ApplicationCommand]
      verify(underTest.applicationService, atLeast(1)).dispatchCmd(*[ApplicationId], captor)(*)
      captor.values
    }

    implicit val format: OFormat[ChangeAppNameAndDescForm] = Json.format[ChangeAppNameAndDescForm]

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithSubscriptionFields) {

      final def callDetailsDev: Future[Result] = underTest.applicationDetails(app.id)(loggedInDevRequest)

      final def callDetailsAdmin: Future[Result] = underTest.applicationDetails(app.id)(loggedInAdminRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.applicationDetails(app.id)(loggedOutRequest)

    }

    implicit class ChangeAppNameAndDescAppAugment(val app: ApplicationWithCollaborators) {

      final def toForm =
        ChangeAppNameAndDescForm(app.details.name.value, app.details.description)

      final def callChangeAppNameAndDesc: Future[Result] = underTest.changeAppNameAndDesc(app.id)(loggedInDevRequest)

      final def callChangeAppNameAndDescNotLoggedIn: Future[Result] = underTest.changeAppNameAndDesc(app.id)(loggedOutRequest)

      final def callChangeAppNameAndDescWithToken: Future[Result] = addToken(underTest.changeAppNameAndDesc(app.id))(loggedInDevRequest)

      final def callChangeAppNameAndDescNotLoggedInWithToken: Future[Result] = addToken(underTest.changeAppNameAndDesc(app.id))(loggedOutRequest)

      final def callChangeAppNameAndDescAction: Future[Result] = callChangeDetailsAction(loggedInDevRequest)

      final def callChangeAppNameAndDescActionNotLoggedIn: Future[Result] = callChangeDetailsAction(loggedOutRequest)

      final private def callChangeDetailsAction[T](request: FakeRequest[T]): Future[Result] = {
        addToken(underTest.changeAppNameAndDescAction(app.id))(request.withJsonBody(Json.toJson(app.toForm)))
      }
    }
  }
}
