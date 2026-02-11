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
import org.scalatest.Assertion
import views.html.manageapplication._

import play.api.mvc.Result
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models.FieldDefinitionType
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageApplicationControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with SubmissionsTestData
    with ApplicationWithCollaboratorsFixtures
    with ApplicationWithSubscriptionFieldsData
    with SubscriptionTestHelper {

  val approvedStandardApplication = appWithSubsFieldsOne.withAccess(standardAccessOne)
    .modify(_.copy(description = Some("Some App Description")))
    .withToken(ApplicationTokenData.one)
  val sandboxStandardApplication  = approvedStandardApplication.inSandbox()
  val productionPrivApplication   = approvedStandardApplication.withAccess(privilegedAccess)
  val productionRopcApplication   = approvedStandardApplication.withAccess(ropcAccess)

  "details" when {
    "logged in as a Developer on an application" should {
      "return the view for a standard production app with no change link" in new Setup {
        detailsShouldRenderThePageForDeveloper(devSession)(approvedStandardApplication)
      }
      "return the view for a standard sandbox app" in new Setup {
        detailsShouldRenderThePageForAdminOrSandbox(devSession)(sandboxStandardApplication)
      }
      "return the view for a Privileged production app" in new Setup {
        detailsShouldRenderThePageForDeveloper(devSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        detailsShouldRenderThePageForDeveloper(devSession)(productionRopcApplication)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(approvedStandardApplication)
      }
      "return the view for a Privileged production app" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(productionRopcApplication)
      }
    }

    "not a team member on an application" should {
      "return see other" in new Setup {
        val application = approvedStandardApplication
        givenApplicationAction(application, altDevSession)

        val result = application.callDetailsDev

        status(result) shouldBe SEE_OTHER
      }
    }

    "not logged in" should {
      "redirect to login" in new Setup {
        val application = approvedStandardApplication
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

    val detailsView                                  = app.injector.instanceOf[ApplicationDetailsView]
    def fraudPreventionConfig: FraudPreventionConfig = FraudPreventionConfig(enabled = true, List(ServiceName("ppns-api")), "/")

    val underTest = new ManageApplicationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      fraudPreventionConfig,
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

    private def assertCommonAppDetails(application: ApplicationWithSubscriptionFields, doc: Document) = {
      withClue("name")(elementIdentifiedByIdContainsText(doc, "applicationName", application.name.value) shouldBe true)
      withClue("environment")(elementIdentifiedByIdContainsText(doc, "environment", application.details.deployedTo.displayText) shouldBe true)
      withClue("description")(elementIdentifiedByIdContainsText(doc, "description", application.details.description.getOrElse("None")) shouldBe true)
      withClue("ipAllowList")(elementIdentifiedByIdContainsText(
        doc,
        "ipAllowListText",
        s"${application.details.ipAllowlist.allowlist.toList.size} IP addresses added"
      ) shouldBe true)
      withClue("teamMembers")(elementIdentifiedByIdContainsText(doc, "teamMembers", s"${application.collaborators.size.toString} team members") shouldBe true)
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
      withClue("apiSetupHeading")(elementIdentifiedByIdContainsText(doc, "apiSetupHeading", "API setup") shouldBe true)
      withClue("apiSetupHint")(elementIdentifiedByIdContainsText(
        doc,
        "apiSetupHint",
        "Some of the REST APIs you have added to this application need to be set up before you can use their endpoints."
      ) shouldBe true)
      withClue("apiConfiguration")(elementIdentifiedByIdContainsText(doc, "apiConfiguration", "API configuration") shouldBe true)

      if (application.isStandard) {
        val redirectUriWording = application.access match {
          case Access.Standard(redirectUris, _, _, _, _, _, _) => s"${redirectUris.size} of 5 URIs added"
          case _                                               => "None added"
        }
        withClue("redirectUris")(elementIdentifiedByIdContainsText(doc, "redirectUrisText", redirectUriWording) shouldBe true)
        withClue("delete")(elementIdentifiedByIdContainsText(doc, "delete-link", "Delete application") shouldBe true)
      }

      if (application.isProduction) {
        withClue("fraudPrevention")(elementIdentifiedByIdContainsText(doc, "fraudPrevention", "Fraud prevention") shouldBe true)
        withClue("changePrivacyPolicy")(elementIdentifiedByIdContainsText(doc, "changePrivacyPolicy", "Change") shouldBe true)
        withClue("changeTermsAndConditions")(elementIdentifiedByIdContainsText(doc, "changeTermsAndConditions", "Change") shouldBe true)
      }
    }

    def detailsShouldRenderThePageForDeveloper(userSession: UserSession)(application: ApplicationWithSubscriptionFields): Any = {

      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields(application.id, application.clientId)("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = FieldDefinitionType.PPNS_FIELD)))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(application, userSession, subsData)
      returnAgreementDetails()

      val result = application.callDetailsDev
      status(result) shouldBe OK
      val doc    = Jsoup.parse(contentAsString(result))

      assertCommonAppDetails(application, doc)
    }

    def detailsShouldRenderThePageForAdminOrSandbox(userSession: UserSession)(application: ApplicationWithSubscriptionFields): Assertion = {

      val subscriptionStatus: APISubscriptionStatus                     = exampleSubscriptionWithFields(application.id, application.clientId)("ppns", 1)
      val newFields: List[ApiSubscriptionFields.SubscriptionFieldValue] = subscriptionStatus.fields.fields
        .map(fieldValue => fieldValue.copy(definition = fieldValue.definition.copy(`type` = FieldDefinitionType.PPNS_FIELD)))
      val subsData                                                      = List(subscriptionStatus.copy(fields = subscriptionStatus.fields.copy(fields = newFields)))

      givenApplicationAction(application, userSession, subsData)

      val result = application.callDetailsAdmin
      status(result) shouldBe OK
      val doc    = Jsoup.parse(contentAsString(result))

      assertCommonAppDetails(application, doc)

      withClue("clientId")(elementIdentifiedByIdContainsText(doc, "clientId", application.details.token.clientId.value) shouldBe true)
      withClue("createClientSecrets")(elementIdentifiedByIdContainsText(doc, "createClientSecrets", s"${application.details.token.clientSecrets.size} of 5 created") shouldBe true)
      withClue("pushSecret")(elementIdentifiedByIdContainsText(doc, "pushSecret", "Push secret") shouldBe true)
    }

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithSubscriptionFields) {

      final def callDetailsDev: Future[Result] = underTest.applicationDetails(app.id)(loggedInDevRequest)

      final def callDetailsAdmin: Future[Result] = underTest.applicationDetails(app.id)(loggedInAdminRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.applicationDetails(app.id)(loggedOutRequest)
    }
  }
}
