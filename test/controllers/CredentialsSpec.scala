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

import java.util.UUID
import java.util.UUID.randomUUID

import connectors.ThirdPartyDeveloperConnector
import domain.models.applications.ApplicationState._
import domain.models.applications.Role.{ADMINISTRATOR, DEVELOPER}
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.ClientSecretLimitExceeded
import domain.models.applications._
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithLoggedInSession._
import views.html.{ClientIdView, ClientSecretsView, CredentialsView, ServerTokenView}
import views.html.editapplication.DeleteClientSecretView

import scala.concurrent.ExecutionContext.Implicits.global

class CredentialsSpec extends BaseControllerSpec with SubscriptionTestHelperSugar {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val applicationId = ApplicationId(UUID.randomUUID().toString())
  val tokens = ApplicationToken(Seq(aClientSecret("secret1"), aClientSecret("secret2")), "token")

  trait ApplicationProvider {
    def createApplication(): Application
  }

  trait BasicApplicationProvider extends ApplicationProvider {
    def createApplication() =
      Application(
        applicationId,
        clientId,
        "App name 1",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        Environment.PRODUCTION,
        Some("Description 1"),
        Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
        state = ApplicationState.production(loggedInUser.email, ""),
        access = Standard(
          redirectUris = Seq("https://red1", "https://red2"),
          termsAndConditionsUrl = Some("http://tnc-url.com")
        )
      )
  }

  def createConfiguredApplication(
      applicationId: ApplicationId,
      userRole: Role,
      state: ApplicationState = ApplicationState.production("", ""),
      access: Access = Standard(),
      environment: Environment = Environment.PRODUCTION,
      createdOn: DateTime = DateTimeUtils.now
  ) =
    Application(
      applicationId,
      clientId,
      "app",
      createdOn,
      DateTimeUtils.now,
      None,
      environment,
      collaborators = Set(Collaborator(loggedInUser.email, userRole)),
      state = state,
      access = access
    )

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationProvider {
    val credentialsView = app.injector.instanceOf[CredentialsView]
    val clientIdView = app.injector.instanceOf[ClientIdView]
    val clientSecretsView = app.injector.instanceOf[ClientSecretsView]
    val serverTokenView = app.injector.instanceOf[ServerTokenView]
    val deleteClientSecretView = app.injector.instanceOf[DeleteClientSecretView]

    val underTest = new Credentials(
      applicationServiceMock,
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      credentialsView,
      clientIdView,
      clientSecretsView,
      serverTokenView,
      deleteClientSecretView
    )

    val application = createApplication()
    val applicationWithSubscriptionData = ApplicationWithSubscriptionData(application)

    implicit val hc = HeaderCarrier()

    fetchByApplicationIdReturns(application.id, applicationWithSubscriptionData)
    givenApplicationHasNoSubs(application)
    fetchCredentialsReturns(application, tokens)
    fetchSessionByIdReturns(sessionId, session)
    givenApplicationUpdateSucceeds()

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "The credentials page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)

      val result = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("Your credentials are")
    }

    "inform the user that only admins can access credentials when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("You cannot view or edit production credentials because you're not an administrator")
    }
  }

  "The client ID page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Client ID")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, state = pendingGatekeeperApproval(""))

      val result = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The client secrets page" should {
    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Client secrets")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, state = pendingGatekeeperApproval(""))

      val result = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The server token page" should {
    val dateBeforeCutoff = Credentials.serverTokenCutoffDate.minusDays(1)

    "be displayed for an app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, createdOn = dateBeforeCutoff)

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Server token")
    }

    "return 404 for new apps" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, createdOn = DateTimeUtils.now)

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION, createdOn = dateBeforeCutoff)

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, createdOn = dateBeforeCutoff, state = pendingGatekeeperApproval(""))

      val result = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "addClientSecret" should {
    "add the client secret" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)
      givenAddClientSecretReturns(application, loggedInUser.email)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${applicationId.value}/client-secrets")
      verify(underTest.applicationService).addClientSecret(eqTo(application), eqTo(loggedInUser.email))(any[HeaderCarrier])
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, environment = Environment.PRODUCTION)
      givenAddClientSecretFailsWith(application, loggedInUser.email, new ClientSecretLimitExceeded)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, environment = Environment.SANDBOX)
      givenAddClientSecretFailsWith(application, loggedInUser.email, new ClientSecretLimitExceeded)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the NotFound page when the application does not exist" in new Setup with BasicApplicationProvider {
      fetchByApplicationIdReturnsNone(applicationId)

      val result = (underTest.addClientSecret(applicationId)(loggedInRequest))

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[String])(any[HeaderCarrier])
    }

    "display the error page when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, state = pendingGatekeeperApproval(""))

      val result = (underTest.addClientSecret(applicationId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[String])(any[HeaderCarrier])
    }

    "return to the login page when the user is not logged in" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)

      val result = underTest.addClientSecret(applicationId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      verify(underTest.applicationService, never).addClientSecret(any[Application], any[String])(any[HeaderCarrier])
    }
  }

  "deleteClientSecret" should {
    val clientSecretToDelete: ClientSecret = tokens.clientSecrets.last
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
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, state = pendingGatekeeperApproval(""))

      val result = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "deleteClientSecretAction" should {
    val applicationId = ApplicationId(UUID.randomUUID().toString())
    val clientSecretId: String = UUID.randomUUID().toString

    "delete the selected client secret" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR)

      givenDeleteClientSecretSucceeds(application, clientSecretId, loggedInUser.email)

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${applicationId.value}/client-secrets")
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      def createApplication() = createConfiguredApplication(applicationId, ADMINISTRATOR, state = pendingGatekeeperApproval(""))

      val result = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  private def aClientSecret(secretName: String) = ClientSecret(randomUUID.toString, secretName, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
