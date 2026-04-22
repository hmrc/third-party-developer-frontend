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
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView
import views.html.manageapplication._

import play.api.mvc.Result
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models.FieldDefinitionType
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.Agreement
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{TermsOfUseInvitation, TermsOfUseInvitationState}
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

  val principalApplication   = standardApp.withAccess(standardAccessOne).modify(_.copy(description = Some("Some App Description")))
  val subordinateApplication = principalApplication.inSandbox()

  val prodAppWithRespIndAndV1TermsOfUse = appWithSubsFieldsOne.withAccess(standardAccessOne).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description"), checkInformation = Some(checkInformationOne)))

  val prodAppWithRespIndAndV2TermsOfUse = appWithSubsFieldsOne.withAccess(standardAccessWithSubmission).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description")))

  val prodAppWithRespIndWithV1AndV2TermsOfUse = appWithSubsFieldsOne.withAccess(standardAccessWithSubmission).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description"), checkInformation = Some(checkInformationOne)))

  val v1Agreement: TermsOfUseAgreementDetails = prodAppWithRespIndAndV1TermsOfUse.details.checkInformation.map((checkInfo: CheckInformation) =>
    checkInfo.termsOfUseAgreements.map((toua: TermsOfUseAgreement) =>
      TermsOfUseAgreementDetails(toua.emailAddress, None, toua.timeStamp, Some(toua.version))
    )
  ).get.head

  val v1AgreementWording: String =
    s"${v1Agreement.name.getOrElse(v1Agreement.emailAddress)} agreed to version 1 of the terms of use on ${DateFormatter.formatTwoDigitDay(v1Agreement.date)}."

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
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForDeveloper(devSession)(approvedApplication)
      }
      "return the view for a standard production app with V1 terms of use" in new Setup {
        returnAgreementDetails(v1Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForDeveloper(devSession, v1TOUWording = Some(v1AgreementWording))(prodAppWithRespIndAndV1TermsOfUse)
      }
      "return the view for a standard production app with V2 terms of use" in new Setup {
        returnAgreementDetails(v2Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForDeveloper(devSession, v2TOUWording = Some(v2AgreementWording))(prodAppWithRespIndAndV2TermsOfUse)
      }
      "return the view for a standard sandbox app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForAdminOrSandbox(devSession)(sandboxApplication)
      }
      "return the view for a Privileged production app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForDeveloper(devSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForDeveloper(devSession)(productionRopcApplication)
      }
    }

    "logged in as an Administrator on an application" should {
      "return the view for a standard production app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(approvedApplication)
      }
      "return the view for a standard production app with link to view V1 terms of use" in new Setup {
        returnAgreementDetails(v1Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()
        detailsShouldRenderThePageForAdminOrSandbox(adminSession, v1TOUWording = Some(v1AgreementWording))(prodAppWithRespIndAndV1TermsOfUse)
      }
      "return the view for a Privileged production app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)
        detailsShouldRenderThePageForAdminOrSandbox(adminSession)(productionPrivApplication)
      }
      "return the view for a ROPC production app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
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

  "buildTermsOfUseViewModel for production standard apps" when {

    "V2 not started" should {
      "returns ViewModel showing V2 terms not yet started with deadline when invitation sent but no submission begun" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)

        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

        givenApplicationAction(approvedApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe false
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State shouldBe Some(NotStarted(Some(dueBy)))

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
      }

      "returns ViewModel showing V2 terms not started when no invitation exists" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

        givenApplicationAction(approvedApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe false
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State shouldBe Some(NotStarted(Some(instant)))

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
      }
    }

    "V2 not started with V1 agreement" should {
      "returns ViewModel showing both V1 agreement and V2 uplift invitation when upgrading from V1 to V2" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(prodAppWithRespIndAndV1TermsOfUse.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)

        returnAgreementDetails(v1Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

        givenApplicationAction(prodAppWithRespIndAndV1TermsOfUse, adminSession)

        val result = underTestWithMockView.applicationDetails(prodAppWithRespIndAndV1TermsOfUse.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe true
        viewModel.agreement shouldBe defined
        viewModel.agreement.get.who shouldBe v1Agreement.name.getOrElse(v1Agreement.emailAddress.text)
        viewModel.termsOfUseV2State shouldBe Some(NotStarted(Some(dueBy)))

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
      }
    }

    "V2 started" should {
      "returns ViewModel showing who started V2 terms and deadline when submission is created but not yet in progress" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
        val submission = createdSubmission

        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)
        ProfileServiceMock.LookupDeveloperName.thenReturns(Some("Bob Example"))

        givenApplicationAction(approvedApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe false
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State.get shouldBe Started("Bob Example", dueBy)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        verify(ProfileServiceMock.aMock).lookupDeveloperName(eqTo(LaxEmailAddress("bob@example.com")))(*)
      }

      "returns ViewModel showing who started V2 terms and deadline when user is actively answering questions" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
        val submission = answeringSubmission

        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)
        ProfileServiceMock.LookupDeveloperName.thenReturns(Some("Bob Example"))

        givenApplicationAction(approvedApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State.get shouldBe Started("Bob Example", dueBy)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        verify(ProfileServiceMock.aMock).lookupDeveloperName(eqTo(LaxEmailAddress("bob@example.com")))(*)
      }
    }

    "V2 started with V1 agreement" should {
      "returns ViewModel showing V1 historical agreement alongside who started the V2 process" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(prodAppWithRespIndAndV1TermsOfUse.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
        val submission = answeringSubmission

        returnAgreementDetails(v1Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)
        ProfileServiceMock.LookupDeveloperName.thenReturns(Some("Bob Example"))

        givenApplicationAction(prodAppWithRespIndAndV1TermsOfUse, adminSession)

        val result = underTestWithMockView.applicationDetails(prodAppWithRespIndAndV1TermsOfUse.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe true
        viewModel.agreement should contain(Agreement(v1Agreement.emailAddress.text, v1Agreement.date))
        viewModel.termsOfUseV2State.get shouldBe Started("Bob Example", dueBy)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
        verify(ProfileServiceMock.aMock).lookupDeveloperName(eqTo(LaxEmailAddress("bob@example.com")))(*)
      }
    }

    "V2 submitted" should {
      "returns ViewModel showing who submitted V2 terms and when, pending approval" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
        val submission = submittedSubmission

        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)
        ProfileServiceMock.LookupDeveloperName.thenReturns(Some("Bob Example"))

        givenApplicationAction(approvedApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State.get shouldBe Submitted("Bob Example", submission.status.timestamp)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        verify(ProfileServiceMock.aMock).lookupDeveloperName(eqTo(LaxEmailAddress("bob@example.com")))(*)
      }
    }

    "V2 submitted with V1 agreement" should {
      "returns ViewModel showing V1 historical agreement alongside the pending V2 submission details" in new Setup {
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(prodAppWithRespIndAndV1TermsOfUse.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
        val submission = submittedSubmission

        returnAgreementDetails(v1Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)
        ProfileServiceMock.LookupDeveloperName.thenReturns(Some("Bob Example"))

        givenApplicationAction(prodAppWithRespIndAndV1TermsOfUse, adminSession)

        val result = underTestWithMockView.applicationDetails(prodAppWithRespIndAndV1TermsOfUse.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe true
        viewModel.agreement should contain(Agreement(v1Agreement.emailAddress.text, v1Agreement.date))
        viewModel.termsOfUseV2State.get shouldBe Submitted("Bob Example", submission.status.timestamp)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(prodAppWithRespIndAndV1TermsOfUse.id))(*)
        verify(ProfileServiceMock.aMock).lookupDeveloperName(eqTo(LaxEmailAddress("bob@example.com")))(*)
      }
    }

    "V2 approved" should {
      "returns ViewModel showing V2 terms agreed when submission approved" in new Setup {
        val submission = grantedSubmission
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(prodAppWithRespIndAndV2TermsOfUse.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)

        returnAgreementDetails(v2Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

        givenApplicationAction(prodAppWithRespIndAndV2TermsOfUse, adminSession)

        val result = underTestWithMockView.applicationDetails(prodAppWithRespIndAndV2TermsOfUse.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe false
        viewModel.agreement should contain(Agreement(v2Agreement.name.get, v2Agreement.date))
        val submittedStatus =
          submission.latestInstance.statusHistory.toList.find(_.isSubmitted).get.asInstanceOf[uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.Status.Submitted]
        viewModel.termsOfUseV2State.get shouldBe Approved("bob@example.com", submittedStatus.timestamp)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(prodAppWithRespIndAndV2TermsOfUse.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(prodAppWithRespIndAndV2TermsOfUse.id))(*)
      }
    }

    "V2 approved replacing V1" should {
      "returns ViewModel showing only V2 agreement, V1 no longer displayed after V2 approval" in new Setup {
        val submission = grantedSubmission
        val dueBy      = instant.plusSeconds(86400 * 30)
        val invitation = TermsOfUseInvitation(prodAppWithRespIndAndV2TermsOfUse.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)

        returnAgreementDetails(v2Agreement)
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
        SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

        givenApplicationAction(prodAppWithRespIndWithV1AndV2TermsOfUse, adminSession)

        val result = underTestWithMockView.applicationDetails(prodAppWithRespIndAndV2TermsOfUse.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe true
        viewModel.appUsesOldVersion shouldBe false
        viewModel.agreement should contain(Agreement(v2Agreement.name.get, v2Agreement.date))
        val submittedStatus =
          submission.latestInstance.statusHistory.toList.find(_.isSubmitted).get.asInstanceOf[uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.Status.Submitted]
        viewModel.termsOfUseV2State.get shouldBe Approved("bob@example.com", submittedStatus.timestamp)

        verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(prodAppWithRespIndWithV1AndV2TermsOfUse.id))(*)
        verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(prodAppWithRespIndWithV1AndV2TermsOfUse.id))(*)
      }

      "when V2 failed" should {
        "returns ViewModel showing submission in Submitted state when V2 submission has failed" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = failedSubmission

          returnAgreementDetails()
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.agreement shouldBe None
          viewModel.termsOfUseV2State should contain(Submitted("bob@example.com", submission.status.timestamp))

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }

      "when V2 failed with V1 agreement" should {
        "returns ViewModel showing V1 agreement and V2 Submitted state" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = failedSubmission

          returnAgreementDetails(v1Agreement)
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.appUsesOldVersion shouldBe true
          viewModel.agreement shouldBe Some(Agreement(v1Agreement.emailAddress.text, v1Agreement.date))
          viewModel.termsOfUseV2State.get shouldBe Submitted("bob@example.com", submission.status.timestamp)

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }

      "when V2 in warning state" should {
        "returns ViewModel showing V2 submission in Submitted state" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = warningsSubmission

          returnAgreementDetails()
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.agreement shouldBe None
          viewModel.termsOfUseV2State should contain(Submitted("bob@example.com", submission.status.timestamp))

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }

      "when V2 in warning state with V1 agreement" should {
        "returns ViewModel showing V1 agreement and V2 in Submitted state" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = warningsSubmission

          returnAgreementDetails(v1Agreement)
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.appUsesOldVersion shouldBe true
          viewModel.agreement shouldBe Some(Agreement(v1Agreement.emailAddress.text, v1Agreement.date))
          viewModel.termsOfUseV2State.get shouldBe Submitted("bob@example.com", submission.status.timestamp)

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }

      "when V2 in GrantedWithWarning state" should {
        "returns ViewModel showing V2 submission in Submitted state" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = grantedWithWarningsSubmission

          returnAgreementDetails()
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.agreement shouldBe None
          viewModel.termsOfUseV2State should contain(Submitted("bob@example.com", submission.status.timestamp))

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }

      "when V2 in GrantedWithWarning state with V1 agreement" should {
        "returns ViewModel showing V1 agreement and V2 in Submitted state" in new Setup {
          val dueBy      = instant.plusSeconds(86400 * 30)
          val invitation = TermsOfUseInvitation(approvedApplication.id, instant, instant, dueBy, None, TermsOfUseInvitationState.EMAIL_SENT)
          val submission = grantedWithWarningsSubmission

          returnAgreementDetails(v1Agreement)
          TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnWith(invitation)
          SubmissionServiceMock.FetchLatestSubmission.thenReturns(submission)

          givenApplicationAction(approvedApplication, adminSession)

          val result = underTestWithMockView.applicationDetails(approvedApplication.id)(loggedInAdminRequest)
          status(result) shouldBe OK

          val viewModel = captureTermsOfUseViewModel()

          viewModel.required shouldBe true
          viewModel.appUsesOldVersion shouldBe true
          viewModel.agreement shouldBe Some(Agreement(v1Agreement.emailAddress.text, v1Agreement.date))
          viewModel.termsOfUseV2State.get shouldBe Submitted("bob@example.com", submission.status.timestamp)

          verify(TermsOfUseInvitationServiceMock.aMock).fetchTermsOfUseInvitation(eqTo(approvedApplication.id))(*)
          verify(SubmissionServiceMock.aMock).fetchLatestSubmission(eqTo(approvedApplication.id))(*)
        }
      }
    }

    "sandbox and non-standard apps" should {
      "returns ViewModel indicating no terms of use required for sandbox app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

        givenApplicationAction(sandboxApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(sandboxApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe false
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State shouldBe None
      }

      "returns ViewModel indicating no terms of use required for privileged app" in new Setup {
        returnAgreementDetails()
        TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()
        SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

        givenApplicationAction(productionPrivApplication, adminSession)

        val result = underTestWithMockView.applicationDetails(productionPrivApplication.id)(loggedInAdminRequest)
        status(result) shouldBe OK

        val viewModel = captureTermsOfUseViewModel()

        viewModel.required shouldBe false
        viewModel.agreement shouldBe None
        viewModel.termsOfUseV2State shouldBe None
      }
    }
  }

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseInvitationServiceMockModule
      with TermsOfUseServiceMock
      with ProfileServiceMock {

    val mockDetailsView                              = mock[ApplicationDetailsView]
    val detailsView                                  = app.injector.instanceOf[ApplicationDetailsView]
    val unauthorisedAppDetailsView                   = app.injector.instanceOf[UnauthorisedAppDetailsView]
    def fraudPreventionConfig: FraudPreventionConfig = FraudPreventionConfig(enabled = true, List(ServiceName("ppns-api")), "/")

    val underTest = new ManageApplicationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      fraudPreventionConfig,
      termsOfUseServiceMock,
      SubmissionServiceMock.aMock,
      TermsOfUseInvitationServiceMock.aMock,
      ProfileServiceMock.aMock,
      unauthorisedAppDetailsView,
      mcc,
      cookieSigner,
      clock,
      detailsView
    )

    // Controller with mocked view for capturing ViewModel
    val underTestWithMockView = new ManageApplicationController(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      fraudPreventionConfig,
      termsOfUseServiceMock,
      SubmissionServiceMock.aMock,
      TermsOfUseInvitationServiceMock.aMock,
      ProfileServiceMock.aMock,
      unauthorisedAppDetailsView,
      mcc,
      cookieSigner,
      clock,
      mockDetailsView
    )

    when(mockDetailsView.apply(*, *, *, *)(*, *, *, *, *)).thenReturn(play.twirl.api.HtmlFormat.empty)

    when(underTest.applicationService.isApplicationNameValid(*, *, *)(*))
      .thenReturn(Future.successful(ApplicationNameValidationResult.Valid))

    when(underTest.applicationService.dispatchCmd(*[ApplicationId], *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    ProfileServiceMock.LookupDeveloperName.thenReturns(Some("bob@example.com"))

    def captureTermsOfUseViewModel(): Details.TermsOfUseViewModel = {
      val captor = ArgCaptor[Details.TermsOfUseViewModel]
      verify(mockDetailsView).apply(*, *, *, captor)(*, *, *, *, *)
      captor.value
    }

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

    implicit class AppAugment(val app: ApplicationWithCollaborators) {
      final def withDescription(description: Option[String]): ApplicationWithCollaborators = app.modify(_.copy(description = description))
    }

    implicit class ChangeDetailsAppAugment(val app: ApplicationWithSubscriptionFields) {

      final def callDetailsDev: Future[Result] = underTest.applicationDetails(app.id)(loggedInDevRequest)

      final def callDetailsAdmin: Future[Result] = underTest.applicationDetails(app.id)(loggedInAdminRequest)

      final def callDetailsNotLoggedIn: Future[Result] = underTest.applicationDetails(app.id)(loggedOutRequest)

    }

  }
}
