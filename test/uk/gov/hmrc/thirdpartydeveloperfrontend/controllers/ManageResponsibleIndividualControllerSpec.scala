/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.{ADMINISTRATOR, DEVELOPER}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken}
import views.html.manageResponsibleIndividual.ResponsibleIndividualDetailsView
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global

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

    val underTest = new ManageResponsibleIndividualController(
      sessionServiceMock,
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mcc,
      cookieSigner,
      responsibleIndividualDetailsView
    )
    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val developerSession = DeveloperSession(session)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val responsibleIndividual = ResponsibleIndividual.build("Bob Responsible", "bob@example.com")

    implicit val hc = HeaderCarrier()

    def givenTheApplicationExistWithUserRole(teamMembers: Seq[Collaborator], touAcceptances: List[TermsOfUseAcceptance]) = {
      val application = aStandardApplication.copy(
        id = appId,
        access = standardAccess().copy(importantSubmissionData = Some(ImportantSubmissionData(
          None, responsibleIndividual, Set.empty, TermsAndConditionsLocation.InDesktopSoftware, PrivacyPolicyLocation.InDesktopSoftware, touAcceptances))),
        collaborators = teamMembers.toSet,
        createdOn = LocalDateTime.parse("2018-04-06T09:00"),
        lastAccess = Some(LocalDateTime.parse("2018-04-06T09:00"))
      )

      givenApplicationAction(application, developerSession)
      fetchCredentialsReturns(application, tokens())

      application
    }
  }

  "showResponsibleIndividualDetails" should {
    "show the manage RI page with all correct details if user is a team member" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user = developerSession.email.asCollaborator(ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List(
        TermsOfUseAcceptance(ResponsibleIndividual.build("Old RI", "oldri@example.com"), LocalDateTime.parse("2022-05-01T12:00:00"), Submission.Id.random, 0),
        TermsOfUseAcceptance(responsibleIndividual, LocalDateTime.parse("2022-07-01T12:00:00"), Submission.Id.random, 0),
      ))

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*,*,*,*)
      val viewModel = captor.getValue
      viewModel.environment shouldBe "Production"
      viewModel.responsibleIndividualName shouldBe responsibleIndividual.fullName.value
      viewModel.adminEmails shouldBe List(developer.email)
      viewModel.history shouldBe List(
        ResponsibleIndividualHistoryItem(responsibleIndividual.fullName.value, "1 July 2022", "Present"),
        ResponsibleIndividualHistoryItem("Old RI", "1 May 2022", "1 July 2022")
      )
    }

    "allow changes if user is an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user = developerSession.email.asCollaborator(ADMINISTRATOR)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*,*,*,*)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe true
    }

    "don't allow changes if user is not an admin" in new Setup {
      val captor: ArgumentCaptor[ViewModel] = ArgumentCaptor.forClass(classOf[ViewModel])
      val user = developerSession.email.asCollaborator(DEVELOPER)

      givenTheApplicationExistWithUserRole(List(user), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      verify(responsibleIndividualDetailsView).apply(*, captor.capture())(*,*,*,*)
      val viewModel = captor.getValue
      viewModel.allowChanges shouldBe false
    }

    "return an error if user is not a team member" in new Setup {
      givenTheApplicationExistWithUserRole(List("other@example.com".asCollaborator(ADMINISTRATOR)), List.empty)

      val result = underTest.showResponsibleIndividualDetails(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }
}
