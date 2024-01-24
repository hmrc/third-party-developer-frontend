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
import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.util.ByteString
import views.html.editapplication.DeleteClientSecretView
import views.html.{ClientIdView, ClientSecretsGeneratedView, ClientSecretsView, CredentialsView, ServerTokenView}

import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.CommandFailures
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, ClientSecretHashingService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class CredentialsSpec
    extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with DeveloperBuilder
    with LocalUserIdTracker
    with FixedClock {

  val applicationId: ApplicationId = ApplicationId.random
  val appTokens: ApplicationToken  = ApplicationToken(List(aClientSecret("secret1"), aClientSecret("secret2")), "token")

  trait ApplicationProvider {
    def createApplication(): Application
  }
  val productionState: ApplicationState           = ApplicationState(State.PRODUCTION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), instant)
  val pendingGatekeeperApproval: ApplicationState = productionState.copy(name = State.PENDING_GATEKEEPER_APPROVAL)

  trait BasicApplicationProvider extends ApplicationProvider {

    def createApplication(): Application = {
      Application(
        applicationId,
        clientId,
        "App name 1",
        instant,
        Some(instant),
        None,
        grantLength,
        Environment.PRODUCTION,
        Some("Description 1"),
        Set(loggedInDeveloper.email.asAdministratorCollaborator),
        state = productionState,
        access = Access.Standard(
          redirectUris = List(RedirectUri.unsafeApply("https://red1"), RedirectUri.unsafeApply("https://red2")),
          termsAndConditionsUrl = Some("http://tnc-url.com")
        )
      )
    }
  }

  def createConfiguredApplication(
      applicationId: ApplicationId,
      userRole: Collaborator.Role,
      state: ApplicationState = productionState,
      access: Access = Access.Standard(),
      environment: Environment = Environment.PRODUCTION,
      createdOn: Instant = instant
    ): Application =
    Application(
      applicationId,
      clientId,
      "app",
      createdOn,
      Some(instant),
      None,
      grantLength,
      environment,
      collaborators = Set(loggedInDeveloper.email.asCollaborator(userRole)),
      state = state,
      access = access
    )

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock with ApplicationProvider with ApplicationCommandConnectorMockModule {
    val credentialsView: CredentialsView                       = app.injector.instanceOf[CredentialsView]
    val clientIdView: ClientIdView                             = app.injector.instanceOf[ClientIdView]
    val clientSecretsView: ClientSecretsView                   = app.injector.instanceOf[ClientSecretsView]
    val serverTokenView: ServerTokenView                       = app.injector.instanceOf[ServerTokenView]
    val deleteClientSecretView: DeleteClientSecretView         = app.injector.instanceOf[DeleteClientSecretView]
    val clientSecretsGeneratedView: ClientSecretsGeneratedView = app.injector.instanceOf[ClientSecretsGeneratedView]
    val clientSecretHashingService: ClientSecretHashingService = app.injector.instanceOf[ClientSecretHashingService]

    val underTest = new Credentials(
      mockErrorHandler,
      applicationServiceMock,
      clientSecretHashingService,
      ApplicationCommandConnectorMock.aMock,
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
      clientSecretsGeneratedView,
      FixedClock.clock
    )

    val application: Application                                         = createApplication()
    val applicationWithSubscriptionData: ApplicationWithSubscriptionData = ApplicationWithSubscriptionData(application)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    givenApplicationAction(applicationWithSubscriptionData, loggedInDeveloper)
    fetchCredentialsReturns(application, appTokens)
    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
    givenApplicationUpdateSucceeds()

    val sessionParams: Seq[(String, String)]                  = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val actor: Actors.AppCollaborator                         = Actors.AppCollaborator(loggedInDeveloper.email)
  }

  "The credentials page" should {
    "be displayed for an app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result: Future[Result] = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("Your credentials are")
    }

    "inform the user that only admins can access credentials when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Future[Result] = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("You cannot view or edit production credentials because you're not an administrator")
    }
  }

  "The client ID page" should {
    "be displayed for an app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Client ID")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval)

      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The client secrets page" should {
    "be displayed for an app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Client secrets")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval)

      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The server token page" should {
    val dateBeforeCutoff = Credentials.serverTokenCutoffDate.minus(1, DAYS)

    "be displayed for an app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = dateBeforeCutoff)

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Server token")
    }

    "return 404 for new apps" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = instant)

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication(): Application =
        createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION, createdOn = dateBeforeCutoff)

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication(): Application =
        createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, createdOn = dateBeforeCutoff, state = pendingGatekeeperApproval)

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "addClientSecret" should {
    "add the client secret" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(application)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe OK
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, environment = Environment.PRODUCTION)
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.ClientSecretLimitExceeded)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, environment = Environment.SANDBOX)
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.ClientSecretLimitExceeded)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the NotFound page when the application does not exist" in new Setup with BasicApplicationProvider {
      reset(applicationActionServiceMock) // Wipe givenApplicationActionReturns
      givenApplicationActionReturnsNotFound(applicationId)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }

    "display the error page when the application has not reached production state" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval)

      val result: Future[Result] = (underTest.addClientSecret(applicationId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }

    "return to the login page when the user is not logged in" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }
  }

  "deleteClientSecret" should {
    val clientSecretToDelete: ClientSecretResponse = appTokens.clientSecrets.last
    val nonExistantClientSecretId: ClientSecret.Id = ClientSecret.Id.random

    "return the confirmation page when the selected client secret exists" in new Setup with BasicApplicationProvider {
      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Are you sure you want to delete this client secret?")
      contentAsString(result) should include("client secret ending ret2")
    }

    "return 404 when the selected client secret does not exist" in new Setup with BasicApplicationProvider {
      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, nonExistantClientSecretId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval)

      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "deleteClientSecretAction" should {
    val applicationId  = ApplicationId.random
    val clientSecretId = ClientSecret.Id.random

    "delete the selected client secret" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR)

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(application)
      // givenDeleteClientSecretSucceeds(application, actor, clientSecretId)

      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${applicationId}/client-secrets")
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.DEVELOPER, environment = Environment.PRODUCTION)

      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication(): Application = createConfiguredApplication(applicationId, Collaborator.Roles.ADMINISTRATOR, state = pendingGatekeeperApproval)

      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  private def aClientSecret(secretName: String) = ClientSecretResponse(ClientSecret.Id.random, secretName, instant)

}
