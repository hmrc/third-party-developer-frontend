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
import play.filters.csrf.CSRF.TokenProvider
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividual, TermsOfUseAcceptance, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken}

class ManageResponsibleIndividualControllerSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with TestApplications
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock {
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

    val underTest        = new ManageResponsibleIndividualController(
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
    val developer        = buildDeveloper()
    val sessionId        = "sessionId"
    val session          = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val developerSession = DeveloperSession(session)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams         = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest      = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest       = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val responsibleIndividual = ResponsibleIndividual(FullName("Bob Responsible"), "bob@example.com".toLaxEmail)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    def givenTheApplicationExistWithUserRole(teamMembers: Seq[Collaborator], touAcceptances: List[TermsOfUseAcceptance]) = {
      val application = aStandardApplication.copy(
        id = appId,
        access = standardAccess().copy(importantSubmissionData =
          Some(ImportantSubmissionData(
            None,
            responsibleIndividual,
            Set.empty,
            TermsAndConditionsLocations.InDesktopSoftware,
            PrivacyPolicyLocations.InDesktopSoftware,
            touAcceptances
          ))
        ),
        collaborators = teamMembers.toSet,
        createdOn = Instant.parse("2018-04-06T09:00:00Z"),
        lastAccess = Some(Instant.parse("2018-04-06T09:00:00Z"))
      )

      givenApplicationAction(application, developerSession)
      fetchCredentialsReturns(application, tokens())

      application
    }
  }

  "showResponsibleIndividualDetails" should {
    "show the manage RI page with all correct details if user is a team member" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user                              = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(
        List(user),
        List(
          TermsOfUseAcceptance(ResponsibleIndividual(FullName("Old RI"), "oldri@example.com".toLaxEmail), Instant.parse("2022-05-01T12:00:00Z"), SubmissionId.random, 0),
          TermsOfUseAcceptance(responsibleIndividual, Instant.parse("2022-07-01T12:00:00Z"), SubmissionId.random, 0)
        )
      )

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.environment shouldBe "Production"
      viewModel.responsibleIndividualName shouldBe responsibleIndividual.fullName.value
      viewModel.adminEmails shouldBe List(developer.email.text)
      viewModel.history shouldBe List(
        ResponsibleIndividualHistoryItem(responsibleIndividual.fullName.value, "1 July 2022", "Present"),
        ResponsibleIndividualHistoryItem("Old RI", "1 May 2022", "1 July 2022")
      )
    }

    "allow changes if user is an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user                              = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe true
    }

    "don't allow changes if user is not an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user                              = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*, *, *, *)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe false
    }

    "return an error if user is not a team member" in new Setup {
      givenTheApplicationExistWithUserRole(List("other@example.com".toLaxEmail.asAdministratorCollaborator), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "showResponsibleIndividualChangeToSelfOrOther" should {
    "return success if user is an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfOrOther(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }
    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfOrOther(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToSelfOrOtherAction" should {
    "redirect to correct page if user selects 'self'" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/self")
    }

    "redirect to correct page if user selects 'other'" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "other")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/other")
    }

    "return error if no choice selected" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }
    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfOrOtherAction(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToSelf" should {
    "return success if user is an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelf(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.showResponsibleIndividualChangeToSelf(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToSelfAction" should {
    "save current users details as the RI" in new Setup {
      when(applicationServiceMock.updateResponsibleIndividual(*[Application], *[UserId], *, *[LaxEmailAddress])(*)).thenReturn(successful(ApplicationUpdateSuccessful))
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.responsibleIndividualChangeToSelfAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/self/confirmed")
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("who" -> "self")
      val result  = underTest.responsibleIndividualChangeToSelfAction(appId)(request)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToSelfConfirmed" should {
    "return success if user is an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfConfirmed(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToSelfConfirmed(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToOther" should {
    "return success if user is an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToOther(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToOther(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "responsibleIndividualChangeToOtherAction" should {
    "update responsible individual with new details correctly" in new Setup {
      val name          = "bob"
      val email         = "bob@example.com".toLaxEmail
      val requesterName = developerSession.displayedName
      when(applicationServiceMock.verifyResponsibleIndividual(*[Application], *[UserId], eqTo(requesterName), eqTo(name), eqTo(email))(*)).thenReturn(successful(
        ApplicationUpdateSuccessful
      ))
      val user          = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("name" -> name, "email" -> email.text)
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/responsible-individual/change/other/requested")
    }

    "return an error if responsible individual details are not new" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("name" -> responsibleIndividual.fullName.value, "email" -> responsibleIndividual.emailAddress.text)
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return an error if responsible individual details are missing" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val request = loggedInRequest.withCSRFToken
      val result  = underTest.responsibleIndividualChangeToOtherAction(appId)(request)

      status(result) shouldBe BAD_REQUEST
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.responsibleIndividualChangeToOtherAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "showResponsibleIndividualChangeToOtherRequested" should {
    "return success if user is an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToOtherRequested(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return error if user is not an admin" in new Setup {
      val user = developerSession.email.asCollaborator(Collaborator.Roles.DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualChangeToOtherRequested(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

  }
}
