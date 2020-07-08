/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import java.util.UUID.randomUUID

import connectors.ThirdPartyDeveloperConnector
import domain._
import domain.AddTeamMemberPageMode.ManageTeamMembers
import domain.Role.{ADMINISTRATOR, DEVELOPER}
import helpers.string._
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import utils.TestApplications
import play.api.mvc.Result
import views.html.checkpages.applicationcheck.team.TeamMemberAddView
import views.html.manageTeamViews.{AddTeamMemberView, ManageTeamView, RemoveTeamMemberView}

class ManageTeamSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  trait Setup extends ApplicationServiceMock with SessionServiceMock with TestApplications {
    val manageTeamView = app.injector.instanceOf[ManageTeamView]
    val addTeamMemberView = app.injector.instanceOf[AddTeamMemberView]
    val teamMemberAddView = app.injector.instanceOf[TeamMemberAddView]
    val removeTeamMemberView = app.injector.instanceOf[RemoveTeamMemberView]

    val underTest = new ManageTeam(
      sessionServiceMock,
      mock[AuditService],
      applicationServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      manageTeamView,
      addTeamMemberView,
      teamMemberAddView,
      removeTeamMemberView
    )

    implicit val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)
    given(applicationServiceMock.addTeamMember(any[Application], any[String], any[Collaborator])(any[HeaderCarrier]))
      .willReturn(AddTeamMemberResponse(registeredUser = true))
    given(applicationServiceMock.removeTeamMember(any[Application], any[String], eqTo(loggedInUser.email))(any[HeaderCarrier]))
      .willReturn(ApplicationUpdateSuccessful)

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExistWithUserRole( appId: String,
                                              userRole: Role,
                                              state: ApplicationState = ApplicationState.production("test", "test"),
                                              additionalTeamMembers: Seq[Collaborator] = Seq()) = {
      
      val collaborators = aStandardApplication.collaborators ++ additionalTeamMembers ++ Set(Collaborator(loggedInUser.email, userRole))
      val application = aStandardApplication.copy(collaborators = collaborators, createdOn = DateTime.parse("2018-04-06T09:00"), lastAccess = DateTime.parse("2018-04-06T09:00"))

      fetchByApplicationIdReturns(appId,application)
      fetchCredentialsReturns(application,tokens())
      givenApplicationHasSubs(application,Seq.empty)

      application
    }
  }

  "manageTeam" should {
    "show the add team member page when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = await(underTest.manageTeam(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = await(underTest.manageTeam(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result = await(underTest.manageTeam(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }
  }

  "addTeamMember" should {
    "show the add team member page when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = await(underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = await(underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result = await(underTest.addTeamMember(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(applicationServiceMock, never()).addTeamMember(any(), any(), any())(any())
    }
  }

  "addTeamMemberAction" should {
    val email = "user@example.com"
    val role = Role.ADMINISTRATOR

    "add a team member when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      val result = await(underTest.addTeamMemberAction(appId, ManageTeamMembers)
                        (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
      verify(applicationServiceMock).addTeamMember(eqTo(application),
        eqTo(loggedInUser.email), eqTo(Collaborator(email, role)))(any[HeaderCarrier])
    }

    "check if team member already exists on the application" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      given(applicationServiceMock.addTeamMember(eqTo(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new TeamMemberAlreadyExists))
      val result = await(underTest.addTeamMemberAction(appId, ManageTeamMembers)
                        (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe BAD_REQUEST
      verify(applicationServiceMock).addTeamMember(eqTo(application),
        eqTo(loggedInUser.email), eqTo(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "check if application exists" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      given(applicationServiceMock.addTeamMember(eqTo(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new ApplicationNotFound))
      val result = await(underTest.addTeamMemberAction(appId, ManageTeamMembers)
                      (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe NOT_FOUND
      verify(applicationServiceMock).addTeamMember(eqTo(application),
        eqTo(loggedInUser.email), eqTo(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "reject invalid email address" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      given(applicationServiceMock.addTeamMember(eqTo(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new ApplicationNotFound))
      val result = await(
        underTest.addTeamMemberAction(appId, ManageTeamMembers)
                          (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress", "role" -> role.toString)))

      status(result) shouldBe BAD_REQUEST
      verify(applicationServiceMock, never()).addTeamMember(eqTo(application),
        eqTo(loggedInUser.email), eqTo(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "return 403 Forbidden when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER)

      val result = await(
        underTest.addTeamMemberAction(appId, ManageTeamMembers)
                      (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe FORBIDDEN
      verify(applicationServiceMock, never()).addTeamMember(any(), any(), any())(any())
    }

    "redirect to login page when logged out" in new Setup {
      val result = await(
        underTest.addTeamMemberAction(appId, ManageTeamMembers)
                    (loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(applicationServiceMock, never()).addTeamMember(any(), any(), any())(any())
    }
  }

  "removeTeamMember" should {
    val teamMemberEmail = "teamMemberToDelete@example.com"
    val teamMemberEmailHash = teamMemberEmail.toSha256
    val additionalTeamMembers = Seq(
      Collaborator("email1@example.com", DEVELOPER),
      Collaborator("email2@example.com", DEVELOPER),
      Collaborator("email3@example.com", DEVELOPER),
      Collaborator(teamMemberEmail, DEVELOPER)
    )

    "show the remove team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include(teamMemberEmail)
    }

    "show the remove team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include(teamMemberEmail)
    }

    "redirect to login page when logged out" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    "reject invalid email address" in new Setup {
      val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)
      val result = await(underTest.addTeamMemberAction(appId, ManageTeamMembers)
                        (loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress")))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "removeTeamMemberAction" when {
    val teamMemberEmail = "teamMember@test.com"

    "logged in as an admin" should {
      "remove a team member when given the correct email and confirmation is 'Yes'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(applicationServiceMock).removeTeamMember(eqTo(application),
          eqTo(teamMemberEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
      }

      "redirect to the team members page without removing a team member when the confirmation in 'No'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "No")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(applicationServiceMock, never()).removeTeamMember(eqTo(application),
          eqTo(teamMemberEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
      }

      "return 400 Bad Request when no confirmation is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail)))

        status(result) shouldBe BAD_REQUEST
        verify(applicationServiceMock, never()).removeTeamMember(eqTo(application),
          eqTo(teamMemberEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
      }

      "show 400 Bad Request when no email is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("confirm" -> "Yes")))

        status(result) shouldBe BAD_REQUEST
        verify(applicationServiceMock, never()).removeTeamMember(eqTo(application),
          eqTo(teamMemberEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
      }
    }

    "logged in as a developer" should {
      "return 403 Forbidden" in new Setup {
        givenTheApplicationExistWithUserRole(appId, DEVELOPER)

        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe FORBIDDEN
        verify(applicationServiceMock, never()).removeTeamMember(any[Application], anyString(), anyString())(any[HeaderCarrier])
      }
    }

    "not logged in" should {
      "redirect to the login page" in new Setup {
        givenTheApplicationExistWithUserRole(appId, DEVELOPER)

        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(applicationServiceMock, never()).removeTeamMember(any[Application], anyString(), anyString())(any[HeaderCarrier])
      }
    }
  }
    
  "ManageTeam" when {
    "using an application pending approval" should {

      trait PendingApprovalReturnsBadRequest extends Setup {
        def executeAction: Result
        
        val pageNumber = 1
        
        val apiVersion = exampleSubscriptionWithFields("api1", 1)
        val subsData = Seq(
          apiVersion
        )

        val app = aStandardPendingApprovalApplication(developer.email)

        fetchByApplicationIdReturns(app)          
        givenApplicationHasSubs(app, subsData)
        
        val result : Result = executeAction

        status(result) shouldBe NOT_FOUND
      }

      "return a bad request for manageTeam action" in new PendingApprovalReturnsBadRequest {

        def executeAction = {
          await(underTest.manageTeam(app.id)(loggedInRequest))
        }
      }

      "return a bad request for addTeamMember action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          await(underTest.addTeamMember(app.id)(loggedInRequest))
        }
      }

      "return a bad request for addTeamMemberAction action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          val requestWithForm = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "thirdpartydeveloper@example.com", "role" -> "DEVELOPER")

          await(addToken(underTest.addTeamMemberAction(app.id, AddTeamMemberPageMode.ApplicationCheck))(requestWithForm))
        }
      }
      
      "return a bad request for removeTeamMember action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          await(underTest.removeTeamMember(app.id, "fake-hash")(loggedInRequest))
        }
      }
      
      "return a bad request for removeTeamMemberAction action" in new PendingApprovalReturnsBadRequest {
        def executeAction = {
          await(underTest.removeTeamMemberAction(app.id)(loggedInRequest))
        }
      }
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
