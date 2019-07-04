/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.controllers

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain.Role.{ADMINISTRATOR, DEVELOPER}
import domain._
import helpers.string._
import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, anyString, eq => mockEq}
import org.mockito.Mockito.{never, verify}
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ManageTeamSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val appId = "1234"
  val clientId = "clientId123"
  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInUser)
  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new ManageTeam(
      mock[SessionService],
      mock[AuditService],
      mock[ThirdPartyDeveloperConnector],
      mock[ApplicationService],
      mockErrorHandler,
      fakeApplication.injector.instanceOf[MessagesApi],
      mock[ApplicationConfig]
    )

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))
    given(underTest.applicationService.addTeamMember(any[Application], any[String], any[Collaborator])(any[HeaderCarrier]))
      .willReturn(AddTeamMemberResponse(registeredUser = true))
    given(underTest.applicationService.removeTeamMember(any[Application], any[String], mockEq(loggedInUser.email))(any[HeaderCarrier]))
      .willReturn(ApplicationUpdateSuccessful)

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
  }


  "manageTeam" should {
    "show the add team member page when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
      val result = await(underTest.manageTeam(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER)

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
      val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
      val result = await(underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER)

      val result = await(underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result = await(underTest.addTeamMember(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(underTest.applicationService, never()).addTeamMember(any(), any(), any())(any())
    }
  }

  "addTeamMemberAction" should {
    val email = "user@example.com"
    val role = Role.ADMINISTRATOR

    "add a team member when logged in as an admin" in new Setup {
      val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
      val result = await(underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
      verify(underTest.applicationService).addTeamMember(mockEq(application),
        mockEq(loggedInUser.email), mockEq(Collaborator(email, role)))(any[HeaderCarrier])
    }

    "check if team member already exists on the application" in new Setup {
      val application = givenTheApplicationExistWithUserRole(
        underTest.applicationService, appId, ADMINISTRATOR)
      given(underTest.applicationService.addTeamMember(mockEq(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new TeamMemberAlreadyExists))
      val result = await(underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService).addTeamMember(mockEq(application),
        mockEq(loggedInUser.email), mockEq(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "check if application exists" in new Setup {
      val application = givenTheApplicationExistWithUserRole(
        underTest.applicationService, appId, ADMINISTRATOR)
      given(underTest.applicationService.addTeamMember(mockEq(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new ApplicationNotFound))
      val result = await(underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe NOT_FOUND
      verify(underTest.applicationService).addTeamMember(mockEq(application),
        mockEq(loggedInUser.email), mockEq(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "reject invalid email address" in new Setup {
      val application = givenTheApplicationExistWithUserRole(
        underTest.applicationService, appId, ADMINISTRATOR)
      given(underTest.applicationService.addTeamMember(mockEq(application), anyString(), any[Collaborator])(any[HeaderCarrier]))
        .willReturn(Future.failed(new ApplicationNotFound))
      val result = await(
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress", "role" -> role.toString)))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never()).addTeamMember(mockEq(application),
        mockEq(loggedInUser.email), mockEq(Collaborator(email, role)))(any[HeaderCarrier])

    }

    "return 403 Forbidden when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER)

      val result = await(
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe FORBIDDEN
      verify(underTest.applicationService, never()).addTeamMember(any(), any(), any())(any())
    }

    "redirect to login page when logged out" in new Setup {
      val result = await(
        underTest.addTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email, "role" -> role.toString)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      verify(underTest.applicationService, never()).addTeamMember(any(), any(), any())(any())
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
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include(teamMemberEmail)
    }

    "show the remove team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include(teamMemberEmail)
    }

    "redirect to login page when logged out" in new Setup {
      givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result =
        await(underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail)))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    "reject invalid email address" in new Setup {
      val application = givenTheApplicationExistWithUserRole(
        underTest.applicationService, appId, ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)
      val result = await(underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress")))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "removeTeamMemberAction" when {
    val teamMemberEmail = "teamMember@test.com"

    "logged in as an admin" should {
      "remove a team member when given the correct email and confirmation is 'Yes'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(underTest.applicationService).removeTeamMember(mockEq(application),
          mockEq(teamMemberEmail), mockEq(loggedInUser.email))(any[HeaderCarrier])
      }

      "redirect to the team members page without removing a team member when the confirmation in 'No'" in new Setup {
        val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "No")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)
        verify(underTest.applicationService, never()).removeTeamMember(mockEq(application),
          mockEq(teamMemberEmail), mockEq(loggedInUser.email))(any[HeaderCarrier])
      }

      "return 400 Bad Request when no confirmation is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail)))

        status(result) shouldBe BAD_REQUEST
        verify(underTest.applicationService, never()).removeTeamMember(mockEq(application),
          mockEq(teamMemberEmail), mockEq(loggedInUser.email))(any[HeaderCarrier])
      }

      "show 400 Bad Request when no email is given" in new Setup {
        val application = givenTheApplicationExistWithUserRole(underTest.applicationService, appId, ADMINISTRATOR)
        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("confirm" -> "Yes")))

        status(result) shouldBe BAD_REQUEST
        verify(underTest.applicationService, never()).removeTeamMember(mockEq(application),
          mockEq(teamMemberEmail), mockEq(loggedInUser.email))(any[HeaderCarrier])
      }
    }

    "logged in as a developer" should {
      "return 403 Forbidden" in new Setup {
        givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER)

        val result = await(underTest.removeTeamMemberAction(appId)(loggedInRequest.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe FORBIDDEN
        verify(underTest.applicationService, never()).removeTeamMember(any[Application], anyString(), anyString())(any[HeaderCarrier])
      }
    }

    "not logged in" should {
      "redirect to the login page" in new Setup {
        givenTheApplicationExistWithUserRole(underTest.applicationService, appId, DEVELOPER)

        val result = await(
          underTest.removeTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail, "confirm" -> "Yes")))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        verify(underTest.applicationService, never()).removeTeamMember(any[Application], anyString(), anyString())(any[HeaderCarrier])
      }
    }
  }

  private def givenTheApplicationExistWithUserRole(applicationService: ApplicationService,
                                                   appId: String,
                                                   userRole: Role,
                                                   state: ApplicationState = ApplicationState.testing,
                                                   additionalTeamMembers: Seq[Collaborator] = Seq()) = {
    val application = Application(appId, clientId, "app", DateTime.parse("2018-04-06T09:00"), Environment.PRODUCTION,
      collaborators = Set(Collaborator(loggedInUser.email, userRole)) ++ additionalTeamMembers, state = state)

    given(applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
    given(applicationService.fetchCredentials(mockEq(appId))(any[HeaderCarrier])).willReturn(tokens)
    given(applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty)

    application
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
