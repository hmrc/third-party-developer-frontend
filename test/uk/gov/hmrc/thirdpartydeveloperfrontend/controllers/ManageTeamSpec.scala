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
import scala.concurrent.Future

import views.html.manageTeamViews.{AddTeamMemberView, ManageTeamView, RemoveTeamMemberView}

import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class ManageTeamSpec
    extends BaseControllerSpec
    with SubscriptionTestSugar
    with WithCSRFAddToken {

  trait Setup
      extends ApplicationServiceMock
      with CollaboratorServiceMockModule
      with ApplicationActionServiceMock
      with ApplicationWithCollaboratorsFixtures
      with SampleUserSession
      with SampleApplication
      with ExtendedSubscriptionTestHelper
      with UserBuilder
      with LocalUserIdTracker {
    val manageTeamView: ManageTeamView             = app.injector.instanceOf[ManageTeamView]
    val addTeamMemberView: AddTeamMemberView       = app.injector.instanceOf[AddTeamMemberView]
    val removeTeamMemberView: RemoveTeamMemberView = app.injector.instanceOf[RemoveTeamMemberView]

    val underTest = new ManageTeam(
      sessionServiceMock,
      mock[AuditService],
      mockErrorHandler,
      applicationServiceMock,
      CollaboratorServiceMock.aMock,
      applicationActionServiceMock,
      mcc,
      cookieSigner,
      manageTeamView,
      addTeamMemberView,
      removeTeamMemberView,
      fraudPreventionConfig
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val developer: User      = buildTrackedUser()
    val session: UserSession = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    CollaboratorServiceMock.AddTeamMember.succeeds()
    CollaboratorServiceMock.RemoveTeamMember.succeeds(mock[ApplicationWithCollaborators])

    val sessionParams: Seq[(String, String)]                  = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExistWithUserRole(
        appId: ApplicationId,
        userRole: Collaborator.Role,
        additionalTeamMembers: Seq[Collaborator] = Seq()
      ): ApplicationWithCollaborators = {

      val collaborators = standardApp.collaborators ++ additionalTeamMembers ++ Set(session.developer.email.asCollaborator(userRole))
      val application   = standardApp.modify(_.copy(
        id = appId,
        createdOn = Instant.parse("2018-04-06T09:00:00Z"),
        lastAccess = Some(Instant.parse("2018-04-06T09:00:00Z"))
      ))
        .withCollaborators(collaborators.toList: _*)

      givenApplicationAction(application, session)
      fetchCredentialsReturns(application, application.details.token)

      application
    }
  }

  trait ExtendedSetup extends Setup {
    val teamMemberEmail     = "teammembertodelete@example.com"
    val teamMemberEmailHash = teamMemberEmail.toSha256

    val additionalTeamMembers = Seq(
      "email1@example.com".toLaxEmail.asDeveloperCollaborator,
      "email2@example.com".toLaxEmail.asDeveloperCollaborator,
      "email3@example.com".toLaxEmail.asDeveloperCollaborator,
      teamMemberEmail.toLaxEmail.asDeveloperCollaborator
    )
  }

  "manageTeam" should {
    "show the add team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)
      val result: Future[Result] = underTest.manageTeam(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER)

      val result: Future[Result] = underTest.manageTeam(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result: Future[Result] = underTest.manageTeam(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }
  }

  "addTeamMember" should {
    "show the add team member page when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)
      val result: Future[Result] = underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "show the add team member page when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER)

      val result: Future[Result] = underTest.addTeamMember(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "redirect to login page when logged out" in new Setup {
      val result: Future[Result] = underTest.addTeamMember(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      CollaboratorServiceMock.AddTeamMember.verifyNeverCalled()
    }
  }

  "addTeamMemberAction" should {
    val email = "user@example.com".toLaxEmail
    val role  = Collaborator.Roles.ADMINISTRATOR

    "add a team member when logged in as an admin" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)

      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email.text, "role" -> role.toString))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)

      CollaboratorServiceMock.AddTeamMember.verifyCalledFor(email, role, session.developer.email)
    }

    "check if team member already exists on the application" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)
      CollaboratorServiceMock.AddTeamMember.teamMemberAlreadyExists()

      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email.text, "role" -> role.toString))

      status(result) shouldBe BAD_REQUEST

      CollaboratorServiceMock.AddTeamMember.verifyCalledFor(email, role, session.developer.email)
    }

    "check if application exists" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)
      CollaboratorServiceMock.AddTeamMember.applicationNotFound()

      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email.text, "role" -> role.toString))

      status(result) shouldBe NOT_FOUND

      CollaboratorServiceMock.AddTeamMember.verifyCalledFor(email, role, session.developer.email)
    }

    "reject invalid email address" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)
      CollaboratorServiceMock.AddTeamMember.applicationNotFound()

      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress", "role" -> role.toString))

      status(result) shouldBe BAD_REQUEST

      CollaboratorServiceMock.AddTeamMember.verifyNeverCalled()
    }

    "return 403 Forbidden when logged in as a developer" in new Setup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER)

      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email.text, "role" -> role.toString))

      status(result) shouldBe FORBIDDEN
      CollaboratorServiceMock.AddTeamMember.verifyNeverCalled()
    }

    "redirect to login page when logged out" in new Setup {
      val result: Future[Result] =
        underTest.addTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> email.text, "role" -> role.toString))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
      CollaboratorServiceMock.AddTeamMember.verifyNeverCalled()
    }
  }

  "removeTeamMember" should {

    "show the remove team member page when logged in as an admin" in new ExtendedSetup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)

      val result: Future[Result] =
        underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include(teamMemberEmail)
    }

    "show the remove team member page when logged in as a developer" in new ExtendedSetup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result: Future[Result] =
        underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include(teamMemberEmail)
    }

    "redirect to login page when logged out" in new ExtendedSetup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER, additionalTeamMembers = additionalTeamMembers)

      val result: Future[Result] = underTest.removeTeamMember(appId, teamMemberEmailHash)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
    }

    "reject invalid email address" in new ExtendedSetup {
      givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR, additionalTeamMembers = additionalTeamMembers)
      val result: Future[Result] = underTest.addTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> "notAnEmailAddress"))

      status(result) shouldBe BAD_REQUEST

    }
  }

  "removeTeamMemberAction" when {
    val teamMemberEmail = "teamMember@test.com".toLaxEmail

    "logged in as an admin" should {
      "remove a team member when given the correct email and confirmation is 'Yes'" in new Setup {
        val application: ApplicationWithCollaborators = givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)

        val result: Future[Result] =
          underTest.removeTeamMemberAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail.text, "confirm" -> "Yes"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(appId, None).url)

        CollaboratorServiceMock.RemoveTeamMember.verifyCalledFor(application, teamMemberEmail, session.developer.email)
      }

      "redirect to the team members page without removing a team member when the confirmation in 'No'" in new Setup {
        val application: ApplicationWithCollaborators = givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)

        val result: Future[Result] =
          underTest.removeTeamMemberAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail.text, "confirm" -> "No"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ManageTeam.manageTeam(application.id, None).url)

        CollaboratorServiceMock.RemoveTeamMember.verifyNeverCalled()
      }

      "return 400 Bad Request when no confirmation is given" in new Setup {
        val application: ApplicationWithCollaborators = givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)

        val result: Future[Result] = underTest.removeTeamMemberAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail.text))

        status(result) shouldBe BAD_REQUEST

        CollaboratorServiceMock.RemoveTeamMember.verifyNeverCalled()
      }

      "show 400 Bad Request when no email is given" in new Setup {
        val application: ApplicationWithCollaborators = givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.ADMINISTRATOR)

        val result: Future[Result] = underTest.removeTeamMemberAction(application.id)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody("confirm" -> "Yes"))

        status(result) shouldBe BAD_REQUEST
        CollaboratorServiceMock.RemoveTeamMember.verifyNeverCalled()
      }
    }

    "logged in as a developer" should {
      "return 403 Forbidden" in new Setup {
        val application: ApplicationWithCollaborators = givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER)

        val result: Future[Result] = underTest.removeTeamMemberAction(application.id)(loggedInRequest.withFormUrlEncodedBody("email" -> teamMemberEmail.text, "confirm" -> "Yes"))

        status(result) shouldBe FORBIDDEN
        CollaboratorServiceMock.RemoveTeamMember.verifyNeverCalled()
      }
    }

    "not logged in" should {
      "redirect to the login page" in new Setup {
        givenTheApplicationExistWithUserRole(appId, Collaborator.Roles.DEVELOPER)

        val result: Future[Result] =
          underTest.removeTeamMemberAction(appId)(loggedOutRequest.withCSRFToken.withFormUrlEncodedBody("email" -> teamMemberEmail.text, "confirm" -> "Yes"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        CollaboratorServiceMock.RemoveTeamMember.verifyNeverCalled()
      }
    }
  }

  "ManageTeam" when {
    "using an application pending approval" should {

      trait PendingApprovalReturnsSeeOther extends Setup {
        def executeAction: Future[Result]

        val pageNumber = 1

        val apiVersion: APISubscriptionStatus    = exampleSubscriptionWithFields("apiA", 1)
        val subsData: Seq[APISubscriptionStatus] = Seq(
          apiVersion
        )

        val app: ApplicationWithCollaborators = standardApp.withState(appStatePendingGatekeeperApproval)

        givenApplicationAction(app, session)

        val result: Future[Result] = executeAction

        status(result) shouldBe SEE_OTHER
      }

      "return a see other for manageTeam action" in new PendingApprovalReturnsSeeOther {

        def executeAction: Future[Result] = {
          underTest.manageTeam(app.id)(loggedInRequest)
        }
      }

      "return a see other for addTeamMember action" in new PendingApprovalReturnsSeeOther {
        def executeAction: Future[Result] = {
          underTest.addTeamMember(app.id)(loggedInRequest)
        }
      }

      "return a see other for removeTeamMember action" in new PendingApprovalReturnsSeeOther {
        def executeAction: Future[Result] = {
          underTest.removeTeamMember(app.id, "fake-hash")(loggedInRequest)
        }
      }

      "return a see other for removeTeamMemberAction action" in new PendingApprovalReturnsSeeOther {
        def executeAction: Future[Result] = {
          underTest.removeTeamMemberAction(app.id)(loggedInRequest)
        }
      }
    }
  }
}
