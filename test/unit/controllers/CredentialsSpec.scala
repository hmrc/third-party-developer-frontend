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
import domain.Environment.SANDBOX
import domain.Role.{ADMINISTRATOR, DEVELOPER}
import domain._
import org.joda.time.DateTimeZone
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.{never, verify, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction.{LoginFailedDueToInvalidPassword, LoginFailedDueToLockedAccount}
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class CredentialsSpec extends BaseControllerSpec with SubscriptionTestHelperSugar {

  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe", loggedInState = LoggedInState.LOGGED_IN)
  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN)
  val appId = "1234"
  val clientId = "clientId123"
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new Credentials(
      mock[ApplicationService],
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mock[SessionService],
      mockErrorHandler,
      messagesApi,
      mock[ApplicationConfig]
    )


    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(successful(application))
    given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens)

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExistWithUserRole(appId: String,
                                             userRole: Role,
                                             state: ApplicationState = ApplicationState.testing,
                                             access: Access = Standard(),
                                             environment: Environment = Environment.PRODUCTION) = {
      val application = Application(appId, clientId, "app", DateTimeUtils.now, environment,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), state = state, access = access)

      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(appId))(any[HeaderCarrier])).willReturn(tokens)
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty)
    }
  }

  "addClientSecret" should {
    val appId = "1234"
    val updatedTokens = ApplicationTokens(
      EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

    "add the client secret" in new Setup {

      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      given(underTest.applicationService.addClientSecret(mockEq(appId))(any[HeaderCarrier])).willReturn(updatedTokens)

      val result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/credentials")
      verify(underTest.applicationService).addClientSecret(mockEq(appId))(any[HeaderCarrier])
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup {

      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, environment = Environment.PRODUCTION)
      when(underTest.applicationService.addClientSecret(mockEq(appId))(any[HeaderCarrier]))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      val result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/credentials?error=client.secret.limit.exceeded")
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup {

      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, environment = Environment.SANDBOX)
      when(underTest.applicationService.addClientSecret(mockEq(appId))(any[HeaderCarrier]))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      val result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/credentials?error=client.secret.limit.exceeded")
    }

    "display the NotFound page when the application does not exist" in new Setup {

      when(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier]))
        .thenReturn(successful(application))
      when(underTest.applicationService.addClientSecret(mockEq(appId))(any[HeaderCarrier]))
        .thenReturn(failed(new ApplicationNotFound))

      val result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup {

      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe FORBIDDEN
      verify(underTest.applicationService, never()).addClientSecret(any[String])(any[HeaderCarrier])
    }

    "return to the login page when the user is not logged in" in new Setup {

      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)

      val result = await(underTest.addClientSecret(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      verify(underTest.applicationService, never()).addClientSecret(any[String])(any[HeaderCarrier])
    }
  }

  "getProductionClientSecret" should {

    "return the client secret when the password is valid" in new Setup {
      val password = "aPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(VerifyPasswordSuccessful)

      val result = await(underTest.getProductionClientSecret(application.id, 1)(loggedInRequest.withHeaders("password" -> password)))

      status(result) shouldBe OK
      jsonBodyOf(result) shouldBe Json.toJson(ClientSecretResponse(tokens.production.clientSecrets(1).secret))
    }

    "return password required when the password is not set" in new Setup {

      val result = await(underTest.getProductionClientSecret(application.id, 1)(loggedInRequest.withHeaders("password" -> "  ")))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.toJson(Error(ErrorCode.PASSWORD_REQUIRED, "Password is required"))
    }

    "return Unauthorized when the password is not valid" in new Setup {
      val invalidPassword = "invalidPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, invalidPassword)))(any[HeaderCarrier]))
        .willReturn(failed(new InvalidCredentials))

      val result = await(underTest.getProductionClientSecret(application.id, 0)(loggedInRequest.withHeaders("password" -> invalidPassword)))

      status(result) shouldBe UNAUTHORIZED
      jsonBodyOf(result) shouldBe Json.toJson(Error(ErrorCode.INVALID_PASSWORD, "Invalid password"))
    }

    "return Locked when the account is locked" in new Setup {
      val invalidPassword = "invalidPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, invalidPassword)))(any[HeaderCarrier]))
        .willReturn(failed(new LockedAccount))

      val result = await(underTest.getProductionClientSecret(application.id, 0)(loggedInRequest.withHeaders("password" -> invalidPassword)))

      status(result) shouldBe LOCKED
      jsonBodyOf(result) shouldBe Json.toJson(Error(ErrorCode.LOCKED_ACCOUNT, "Locked Account"))
    }

    "return Forbidden when the user is not an admin" in new Setup {
      val password = "aPassword"
      val applicationWithoutAdminRights = application.copy(collaborators = Set(Collaborator(loggedInUser.email, Role.DEVELOPER)))

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(VerifyPasswordSuccessful)
      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(applicationWithoutAdminRights)

      val result = await(underTest.getProductionClientSecret(application.id, 0)(loggedInRequest.withHeaders("password" -> password)))

      status(result) shouldBe FORBIDDEN
    }

    "return BadRequest when the client secret does not exist for the index" in new Setup {
      val password = "aPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(VerifyPasswordSuccessful)

      val result = await(underTest.getProductionClientSecret(application.id, 2)(loggedInRequest.withHeaders("password" -> password)))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.toJson(Error(ErrorCode.BAD_REQUEST, "Bad Request"))
    }

    "return BadRequest when the application is not in Production state" in new Setup {
      val password = "aPassword"
      val testingApplication = application.copy(state = ApplicationState.testing)

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(VerifyPasswordSuccessful)
      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(testingApplication)


      val result = await(underTest.getProductionClientSecret(testingApplication.id, 0)(loggedInRequest.withHeaders("password" -> password)))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe Json.toJson(Error(ErrorCode.BAD_REQUEST, "Bad Request"))
    }

  }

  "select client secrets to delete" should {
    "return the select client secrets to delete page when the correct password is entered" in new Setup {
      val password = "aPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(Future.successful(VerifyPasswordSuccessful))

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> password)

      val result = await(underTest.selectClientSecretsToDelete(appId)(requestWithFormBody))

      status(result) shouldBe OK
      bodyOf(result) should include("Choose which client secrets to delete")
      bodyOf(result) should include("secret")
      bodyOf(result) should include("secret2")
    }

    "return the select client secrets to delete page for an admin on a sandbox app without password" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, environment = SANDBOX)
      val result = await(underTest.selectClientSecretsToDelete(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("Choose which client secrets to delete")
      bodyOf(result) should include("secret")
      bodyOf(result) should include("secret2")
    }

    "return the select client secrets to delete page for a developer on a sandbox app without password" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = SANDBOX)

      val result = await(underTest.selectClientSecretsToDelete(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("Choose which client secrets to delete")
      bodyOf(result) should include("secret")
      bodyOf(result) should include("secret2")
    }

    "display the appropriate error message and create an audit when the incorrect password is entered once" in new Setup {
      val incorrectPassword = "anIncorrectPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, incorrectPassword)))(any[HeaderCarrier]))
        .willReturn(Future.failed(new InvalidCredentials))

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> incorrectPassword)

      val result = await(underTest.selectClientSecretsToDelete(appId)(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("Invalid password")
      verify(underTest.auditService).audit(mockEq(LoginFailedDueToInvalidPassword), any())(any[HeaderCarrier])
    }

    "display the appropriate error message when nothing is entered" in new Setup {
      val emptyPassword = ""

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> emptyPassword)

      val result = await(underTest.selectClientSecretsToDelete(appId)(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("Provide your password")
    }

    "redirect the user to the application locked page and create an audit when the incorrect password is entered a fifth time" in new Setup {
      val incorrectPassword = "anIncorrectPassword"

      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, incorrectPassword)))(any[HeaderCarrier]))
        .willReturn(Future.failed(new LockedAccount))

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> incorrectPassword)

      val result = await(underTest.selectClientSecretsToDelete(appId)(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/locked")
      verify(underTest.auditService).audit(mockEq(LoginFailedDueToLockedAccount), any())(any[HeaderCarrier])
    }

    "not show the select client secrets to delete page to a developer on a production app" in new Setup {
      val password = "aPassword"

      givenTheApplicationExistWithUserRole(appId, DEVELOPER)
      given(underTest.developerConnector.checkPassword(mockEq(PasswordCheckRequest(loggedInUser.email, password)))(any[HeaderCarrier]))
        .willReturn(Future.successful(VerifyPasswordSuccessful))

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> password)

      val result = await(underTest.selectClientSecretsToDelete(appId)(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "display privileged page when it is a privileged app" in new Setup {
      val privilegedAppId = "privAppId"
      val password = "aPassword"

      givenTheApplicationExistWithUserRole(privilegedAppId, ADMINISTRATOR, access = Privileged())

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> password)

      val result = await(underTest.selectClientSecretsToDelete(privilegedAppId)(requestWithFormBody))

      status(result) shouldBe OK
      bodyOf(result) should include("This application is a privileged application.")
    }

    "display ROPC page when it is a ROPC app" in new Setup {
      val ROPCAppId = "ROPCAppId"
      val password = "aPassword"

      givenTheApplicationExistWithUserRole(ROPCAppId, ADMINISTRATOR, access = ROPC())

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("password" -> password)

      val result = await(underTest.selectClientSecretsToDelete(ROPCAppId)(requestWithFormBody))

      status(result) shouldBe OK
      bodyOf(result) should include("This application is a ROPC application.")
    }
  }

  "select client secrets to delete action" should {
    "return the confirmation page when an appropriate amount of client secrets selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("client-secret[]" -> "secret")

      val result = await(underTest.selectClientSecretsToDeleteAction(appId)(requestWithFormBody))

      status(result) shouldBe OK
      bodyOf(result) should include("Are you sure you want us to delete these client secrets?")
      bodyOf(result) should include("secret")
    }

    "display error when no client secrets selected" in new Setup {
      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody()

      val result = await(underTest.selectClientSecretsToDeleteAction(appId)(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("Choose one or more client secrets")
    }

    "display error when all client secrets selected" in new Setup {
      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("client-secret[]" -> "secret", "client-secret[]" -> "secret2")

      val result = await(underTest.selectClientSecretsToDeleteAction(appId)(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("You must keep at least one client secret")

    }
  }

  "confirm delete client secrets" should {
    "return the complete page when Yes is selected" in new Setup {
      val secretsToDelete = "secret"

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("deleteConfirm" -> "Yes", "clientSecretsToDelete" -> secretsToDelete)

      given(underTest.applicationService.deleteClientSecrets(mockEq(appId), mockEq(Seq(secretsToDelete)))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      val result = await(underTest.deleteClientSecretsAction(appId)(requestWithFormBody))

      status(result) shouldBe OK
      bodyOf(result) should include("Client secrets deleted")
      bodyOf(result) should include("Finish")
    }

    "redirect to the credentials page when No is selected" in new Setup {
      val secretsToDelete = "secret"

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("deleteConfirm" -> "No", "clientSecretsToDelete" -> secretsToDelete)

      val result = await(underTest.deleteClientSecretsAction(appId)(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/credentials")
      verify(underTest.applicationService, never()).deleteClientSecrets(any[String], any[Seq[String]])(any[HeaderCarrier])
    }

    "display error when neither Yes or No are selected" in new Setup {
      val secretsToDelete = "secret"

      val requestWithFormBody = loggedInRequest.withCSRFToken.withFormUrlEncodedBody("clientSecretsToDelete" -> secretsToDelete)

      val result = await(underTest.deleteClientSecretsAction(appId)(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      bodyOf(result) should include("Tell us if you want us to delete your client secrets")

    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
