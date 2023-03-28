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

import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

import views.html.editapplication.DeleteClientSecretView
import views.html.{ClientIdView, ClientSecretsGeneratedView, ClientSecretsView, CredentialsView, ServerTokenView}

import play.api.mvc.AnyContentAsEmpty
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, Collaborator}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ClientSecretLimitExceeded
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationState._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientSecret

class CredentialsSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with DeveloperBuilder
    with LocalUserIdTracker {

  val applicationId = ApplicationId.random
  val appTokens     = ApplicationToken(List(aClientSecret("secret1"), aClientSecret("secret2")), "token")

  trait ApplicationProvider {
    def createApplication(): Application
  }

  trait BasicApplicationProvider extends ApplicationProvider {

    def createApplication() =
      Application(
        applicationId,
        clientId,
        "App name 1",
        LocalDateTime.now,
        Some(LocalDateTime.now),
        None,
        grantLength,
        Environment.PRODUCTION,
        Some("Description 1"),
        Set(loggedInDeveloper.email.asAdministratorCollaborator),
        state = ApplicationState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""),
        access = Standard(
          redirectUris = List("https://red1", "https://red2"),
          termsAndConditionsUrl = Some("http://tnc-url.com")
        )
      )
  }

  def createConfiguredApplication(
      applicationId: ApplicationId,
      userRole: Collaborator.Role,
      state: ApplicationState = ApplicationState.production("", "", ""),
      access: Access = Standard(),
      environment: Environment = Environment.PRODUCTION,
      createdOn: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    ) =
    Application(
      applicationId,
      clientId,
      "app",
      createdOn,
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength,
      environment,
      collaborators = Set(loggedInDeveloper.email.asCollaborator(userRole)),
      state = state,
      access = access
    )

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock with ApplicationProvider {
    val credentialsView            = app.injector.instanceOf[CredentialsView]
    val clientIdView               = app.injector.instanceOf[ClientIdView]
    val clientSecretsView          = app.injector.instanceOf[ClientSecretsView]
    val serverTokenView            = app.injector.instanceOf[ServerTokenView]
    val deleteClientSecretView     = app.injector.instanceOf[DeleteClientSecretView]
    val clientSecretsGeneratedView = app.injector.instanceOf[ClientSecretsGeneratedView]

    val underTest = new Credentials(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      sessionServiceMock,
      mcc,
      cookieSigner,
      credentialsView,
      clientIdView,
      clientSecretsView,
      serverTokenView,
      deleteClientSecretView,
      clientSecretsGeneratedView
    )

    val application                     = createApplication()
    val applicationWithSubscriptionData = ApplicationWithSubscriptionData(application)

    implicit val hc = HeaderCarrier()

    givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper)
    fetchCredentialsReturns(application, appTokens)
    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    givenApplicationUpdateSucceeds()

    val sessionParams: Seq[(String, String)]                  = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val actor                                                 = Actors.AppCollaborator(loggedInDeveloper.email)
  }

  "The credentials page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("Your credentials are")
    }

    "inform the user that only admins can access credentials when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("You cannot view or edit production credentials because you're not an administrator")
    }
  }

  "The client ID page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Client ID")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval("", ""))

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The client secrets page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Client secrets")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval("", ""))

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The server token page" should {
    val dateBeforeCutoff = Credentials.serverTokenCutoffDate.minusDays(1)

    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = dateBeforeCutoff)

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Server token")
    }

    "return 404 for new apps" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = LocalDateTime.now(ZoneOffset.UTC))

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION, createdOn = dateBeforeCutoff)

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = dateBeforeCutoff, state = pendingGatekeeperApproval("", ""))

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "addClientSecret" should {
    "add the client secret" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)
      givenAddClientSecretReturns(application, actor)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      verify(underTest.applicationService).addClientSecret(eqTo(application), eqTo(actor))(*)
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, environment = Environment.PRODUCTION)
      givenAddClientSecretFailsWith(application, actor, new ClientSecretLimitExceeded)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, environment = Environment.SANDBOX)
      givenAddClientSecretFailsWith(application, actor, new ClientSecretLimitExceeded)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the NotFound page when the application does not exist" in new Setup with BasicApplicationProvider {
      reset(applicationActionServiceMock) // Wipe givenApplicationActionReturns
      givenApplicationActionReturnsNotFound(applicationId)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[Actors.AppCollaborator])(*)
    }

    "display the error page when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval("", ""))

      val result = (underTest.addClientSecret(applicationId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[Actors.AppCollaborator])(*)
    }

    "return to the login page when the user is not logged in" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result = underTest.addClientSecret(applicationId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[Actors.AppCollaborator])(*)
    }
  }

  "deleteClientSecret" should {
    val clientSecretToDelete: ClientSecret = appTokens.clientSecrets.last
    "return the confirmation page when the selected client secret exists" in new Setup with BasicApplicationProvider {
      val result = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Are you sure you want to delete this client secret?")
      contentAsString(result) should include("client secret ending ret2")
    }

    "return 404 when the selected client secret does not exist" in new Setup with BasicApplicationProvider {
      val result = underTest.deleteClientSecret(applicationId, "wxyz")(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval("", ""))

      val result = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "deleteClientSecretAction" should {
    val applicationId          = ApplicationId.random
    val clientSecretId: String = UUID.randomUUID().toString

    "delete the selected client secret" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      givenDeleteClientSecretSucceeds(application, actor, clientSecretId)

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${applicationId.text}/client-secrets")
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval("", ""))

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  private def aClientSecret(secretName: String) = ClientSecret(randomUUID.toString, secretName, LocalDateTime.now(ZoneOffset.UTC))

}
