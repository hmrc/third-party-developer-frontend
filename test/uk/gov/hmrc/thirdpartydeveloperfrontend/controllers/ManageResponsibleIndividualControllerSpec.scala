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

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.ArgumentCaptor
import views.html.manageResponsibleIndividual._

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividual, TermsOfUseAcceptance, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageResponsibleIndividualControllerSpec
    extends BaseControllerSpec
    with ApplicationWithCollaboratorsFixtures
    with WithCSRFAddToken {

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {
    val responsibleIndividualDetailsView = mock[ResponsibleIndividualDetailsView]
    when(responsibleIndividualDetailsView.apply(*, *)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val responsibleIndividualChangeToSelfOrOtherView = mock[ResponsibleIndividualChangeToSelfOrOtherView]
    when(responsibleIndividualChangeToSelfOrOtherView.apply(*, *)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val responsibleIndividualChangeToSelfView = mock[ResponsibleIndividualChangeToSelfView]
    when(responsibleIndividualChangeToSelfView.apply(*)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val responsibleIndividualChangeToSelfConfirmedView = mock[ResponsibleIndividualChangeToSelfConfirmedView]
    when(responsibleIndividualChangeToSelfConfirmedView.apply(*)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val responsibleIndividualChangeToOtherView = mock[ResponsibleIndividualChangeToOtherView]
    when(responsibleIndividualChangeToOtherView.apply(*, *)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val responsibleIndividualChangeToOtherRequestedView = mock[ResponsibleIndividualChangeToOtherRequestedView]
    when(responsibleIndividualChangeToOtherRequestedView.apply(*, *)(*, *, *, *)).thenReturn(HtmlFormat.empty)

    val underTest             = new ManageResponsibleIndividualController(
      sessionServiceMock,
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mcc,
      cookieSigner,
      responsibleIndividualDetailsView,
      responsibleIndividualChangeToSelfOrOtherView,
      responsibleIndividualChangeToSelfView,
      responsibleIndividualChangeToSelfConfirmedView,
      responsibleIndividualChangeToOtherView,
      responsibleIndividualChangeToOtherRequestedView
    )
    val loggedOutRequest      = FakeRequest().withSession(sessionParams: _*)
    val responsibleIndividual = ResponsibleIndividual(FullName("Bob Responsible"), "bob@example.com".toLaxEmail)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val appId = standardApp.id

    def givenTheApplicationExistWithUserRole(userSession: UserSession, touAcceptances: List[TermsOfUseAcceptance]) = {
      val application = standardApp
        .withAccess(
          standardAccessOne.copy(
            importantSubmissionData = Some(
              ImportantSubmissionData(
                None,
                responsibleIndividual,
                Set.empty,
                TermsAndConditionsLocations.InDesktopSoftware,
                PrivacyPolicyLocations.InDesktopSoftware,
                touAcceptances
              )
            )
          )
        )
      givenApplicationAction(application, userSession)
      fetchCredentialsReturns(application, tokens())

      application
    }
  }

  "showResponsibleIndividualDetails" should {
    "show the manage RI page with all correct details if user is a team member" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])

      givenTheApplicationExistWithUserRole(
        adminSession,
        List(
          TermsOfUseAcceptance(ResponsibleIndividual(FullName("Old RI"), "oldri@example.com".toLaxEmail), Instant.parse("2022-05-01T12:00:00Z"), SubmissionId.random, 0),
          TermsOfUseAcceptance(responsibleIndividual, Instant.parse("2022-07-01T12:00:00Z"), SubmissionId.random, 0)
        )
      )
      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.environment shouldBe "Production"
      viewModel.responsibleIndividualName shouldBe responsibleIndividual.fullName.value
      viewModel.adminEmails should contain(adminEmail.text)
      viewModel.history shouldBe List(
        ResponsibleIndividualHistoryItem(responsibleIndividual.fullName.value, "1 July 2022", "Present"),
        ResponsibleIndividualHistoryItem("Old RI", "1 May 2022", "1 July 2022")
      )
    }

    "allow changes if user is an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])

      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe true
    }

    "don't allow changes if user is not an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])

      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe false
    }

    "return a redirect if user is not a team member" in new Setup {
      givenTheApplicationExistWithUserRole(altDevSession, List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInAltDevRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
    }
  }

  "showResponsibleIndividualChangeToSelfOrOther" should {
    "return success if user is an admin" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfOrOther(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
    }
    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfOrOther(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToSelfOrOtherAction" should {
    "redirect to correct page if user selects 'self'" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/self")
    }

    "redirect to correct page if user selects 'other'" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "other")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/other")
    }

    "return error if no choice selected" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }
    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val request = loggedInDevRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToSelf" should {
    "return success if user is an admin" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelf(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val request = loggedInDevRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.showResponsibleIndividualChangeToSelf(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToSelfAction" should {
    "save current users details as the RI" in new Setup {
      when(applicationServiceMock.updateResponsibleIndividual(*, *[UserId], *, *[LaxEmailAddress])(*)).thenReturn(successful(
        ApplicationUpdateSuccessful
      ))
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.responsibleIndividualChangeToSelfAction(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/self/confirmed")
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val request = loggedInDevRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfAction(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToSelfConfirmed" should {
    "return success if user is an admin" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfConfirmed(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfConfirmed(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToOther" should {
    "return success if user is an admin" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToOther(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToOther(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToOtherAction" should {
    "update responsible individual with new details correctly" in new Setup {
      val name          = "bob"
      val email         = "bob@example.com".toLaxEmail
      val requesterName = adminSession.developer.displayedName
      when(applicationServiceMock.verifyResponsibleIndividual(*, *[UserId], eqTo(requesterName), eqTo(name), eqTo(email))(*)).thenReturn(successful(
        ApplicationUpdateSuccessful
      ))
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("name" -> name, "email" -> email.text)
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/other/requested")
    }

    "return an error if responsible individual details are not new" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken.withFormUrlEncodedBody("name" -> responsibleIndividual.fullName.value, "email" -> responsibleIndividual.emailAddress.text)
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return an error if responsible individual details are missing" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val request = loggedInAdminRequest.withCSRFToken
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.responsibleIndividualChangeToOtherAction(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToOtherRequested" should {
    "return success if user is an admin" in new Setup {
      givenTheApplicationExistWithUserRole(adminSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToOtherRequested(appId)(loggedInAdminRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      givenTheApplicationExistWithUserRole(devSession, List.empty)

      val result = underTest.showResponsibleIndividualChangeToOtherRequested(appId)(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

  }
}
