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

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.{ADMINISTRATOR, DEVELOPER}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.AddTeamMemberPageMode.ManageTeamMembers
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApplicationNotFound, ApplicationUpdateSuccessful, TeamMemberAlreadyExists}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.AddTeamMemberPageMode
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{TestApplications, WithCSRFAddToken}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html.checkpages.applicationcheck.team.TeamMemberAddView
import views.html.manageTeamViews.{AddTeamMemberView, ManageTeamView, RemoveTeamMemberView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._

import java.time.LocalDateTime

class ManageTeamSpec 
    extends BaseControllerSpec 
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar 
    with WithCSRFAddToken
    with TestApplications 
    with DeveloperBuilder
    with LocalUserIdTracker {
  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]
    val addTeamMemberView = app.injector.instanceOf[AddTeamMemberView]
    val teamMemberAddView = app.injector.instanceOf[TeamMemberAddView]
    val removeTeamMemberView = app.injector.instanceOf[RemoveTeamMemberView]

    val underTest = new ManageTeam(
      sessionServiceMock,
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mcc,
      cookieSigner,
      manageTeamView,
      addTeamMemberView,
      teamMemberAddView,
      removeTeamMemberView,
      fraudPreventionConfig
    )

    implicit val hc = HeaderCarrier()

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    when(applicationServiceMock.addTeamMember(*,*,*)(*))
      .thenReturn(successful(()))
    when(applicationServiceMock.removeTeamMember(*,*, eqTo(loggedInDeveloper.email))(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExistWithUserRole(
        appId: ApplicationId,
        userRole: CollaboratorRole,
        state: ApplicationState = ApplicationState.production("test", "test"),
        additionalTeamMembers: Seq[Collaborator] = Seq()
    ) = {

      val developerSession = DeveloperSession(session)

      val collaborators = aStandardApplication.collaborators ++ additionalTeamMembers ++ Set(developerSession.email.asCollaborator(userRole))
      val application = aStandardApplication.copy(id = appId, collaborators = collaborators, createdOn = LocalDateTime.parse("2018-04-06T09:00"), lastAccess = Some(LocalDateTime.parse("2018-04-06T09:00")))

      givenApplicationAction(application, developerSession)
      fetchCredentialsReturns(application, tokens())

      application
    }
  }

  "manageTeam" should {
    "show the add team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = underTest.manageTeam(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = underTest.manageTeam(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result = underTest.manageTeam(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }
  }

  "addTeamMember" should {
    "show the add team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result = underTest.addTeamMember(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(applicationServiceMock, never).addTeamMember(*, *, *)(*)
    }
  }

  "addTeamMemberAction" should {
    val email = "user@example.com"
    val role = CollaboratorRole.ADMINISTRATOR

    "add a team member when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
      verify(applicationServiceMock).addTeamMember(eqTo(application), eqTo(loggedInDeveloper.email), *)(*)
    }

    "check if team member already exists on the application" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      when(applicationServiceMock.addTeamMember(eqTo(application), *, *)(*))
        .thenReturn(failed(new TeamMemberAlreadyExists))
      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString))

      status(result) shouldBe BAD_REQUEST
      verify(applicationServiceMock).addTeamMember(eqTo(application), eqTo(loggedInDeveloper.email), *)(*)

    }

    "check if application exists" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      when(applicationServiceMock.addTeamMember(eqTo(application), *, *)(*))
        .thenReturn(failed(new ApplicationNotFound))
      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString))

      status(result) shouldBe NOT_FOUND
      verify(applicationServiceMock).addTeamMember(eqTo(application), eqTo(loggedInDeveloper.email), *)(*)

    }

    "reject invalid email address" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      when(applicationServiceMock.addTeamMember(eqTo(application), *, *)(*))
        .thenReturn(failed(new ApplicationNotFound))
      val result =
        underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress", "role" -> role.toString))

      status(result) shouldBe BAD_REQUEST
      verify(applicationServiceMock, never).addTeamMember(eqTo(application), eqTo(loggedInDeveloper.email), *)(*)

    }

    "return 403 Forbidden when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString))

      status(result) shouldBe FORBIDDEN
      verify(applicationServiceMock, never).addTeamMember(*, *, *)(*)
    }

    "redirect to login page when logged out" in new Setup {
      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(applicationServiceMock, never).addTeamMember(*, *, *)(*)
    }
  }

  "removeTeamMember" should {
    val teamMemberEmail = "teamMemberToDelete@example.com"
    val teamMemberEmailHash = teamMemberEmail.toSha256
    val additionalTeamMembers = Seq(
      "email1@example.com".asDeveloperCollaborator,
      "email2@example.com".asDeveloperCollaborator,
      "email3@example.com".asDeveloperCollaborator,
      teamMemberEmail.asDeveloperCollaborator
    )

    "show the remove team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)

      val result =
        underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include(teamMemberEmail)
    }

    "show the remove team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result =
        underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include(teamMemberEmail)
    }

    "redirect to login page when logged out" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result = underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    "reject invalid email address" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)
      val result = underTest.addTeamMemberAction(appId, ManageTeamMembers)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress"))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "removeTeamMemberAction" when {
    val teamMemberEmail = "teamMember@test.com"

    "logged in as an admin" should {
      "remove a team member when given the correct email and confirmation is 'Yes'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(applicationServiceMock).removeTeamMember(eqTo(application), eqTo(teamMemberEmail), eqTo(loggedInDeveloper.email))(*)
      }

      "redirect to the team members page without removing a team member when the confirmation in 'No'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result =
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "No"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(applicationServiceMock, never).removeTeamMember(eqTo(application), eqTo(teamMemberEmail), eqTo(loggedInDeveloper.email))(*)
      }

      "return 400 Bad Request when no confirmation is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail))

        status(result) shouldBe BAD_REQUEST
        verify(applicationServiceMock, never).removeTeamMember(eqTo(application), eqTo(teamMemberEmail), eqTo(loggedInDeveloper.email))(*)
      }

      "show 400 Bad Request when no email is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("confirm" -> "Yes"))

        status(result) shouldBe BAD_REQUEST
        verify(applicationServiceMock, never).removeTeamMember(eqTo(application), eqTo(teamMemberEmail), eqTo(loggedInDeveloper.email))(*)
      }
    }

    "logged in as a developer" should {
      "return 403 Forbidden" in new Setup {
        givenTheApplicationExistWithUserRole(appId, DEVELOPER)

        val result = underTest.removeTeamMemberAction(appId)(loggedInRequest.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes"))

        status(result) shouldBe FORBIDDEN
        verify(applicationServiceMock, never).removeTeamMember(any[Application], *, *)(*)
      }
    }

    "not logged in" should {
      "redirect to the login page" in new Setup {
        givenTheApplicationExistWithUserRole(appId, DEVELOPER)

        val result =
          underTest.removeTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(applicationServiceMock, never).removeTeamMember(any[Application], *, *)(*)
      }
    }
  }

  "ManageTeam" when {
    "using an application pending approval" should {

      trait PendingApprovalReturnsBadRequest extends Setup {
        def executeAction: Future[Result]

        val pageNumber = 1

        val apiVersion = exampleSubscriptionWithFields("api1", 1)
        val subsData = Seq(
          apiVersion
        )

        val app = aStandardPendingApprovalApplication(developer.email)

        givenApplicationAction(app, loggedInDeveloper)

        val result = executeAction

        status(result) shouldBe NOT_FOUND
      }

      "return a bad request for manageTeam action" in new PendingApprovalReturnsBadRequest {

        def executeAction = {
          underTest.manageTeam(app.id)(loggedInRequest)
        }
      }

      "return a bad request for addTeamMember action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          underTest.addTeamMember(app.id)(loggedInRequest)
        }
      }

      "return a bad request for addTeamMemberAction action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          val requestWithForm = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "thirdpartydeveloper@example.com", "role" -> "DEVELOPER")

          addToken(underTest.addTeamMemberAction(app.id, AddTeamMemberPageMode.ApplicationCheck))(requestWithForm)
        }
      }

      "return a bad request for removeTeamMember action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          underTest.removeTeamMember(app.id, "fake-hash")(loggedInRequest)
        }
      }

      "return a bad request for removeTeamMemberAction action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          underTest.removeTeamMemberAction(app.id)(loggedInRequest)
        }
      }
    }
  }
}
