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

package unit.controllers

import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain.Role.{ADMINISTRATOR, DEVELOPER}
import domain._
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify, when}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithLoggedInSession._

import scala.concurrent.Future
import scala.concurrent.Future._

class CredentialsSpec extends BaseControllerSpec with SubscriptionTestHelperSugar {

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val appId = "1234"
  val clientId = "clientId123"
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token")

  trait Setup {
    val underTest = new Credentials(
      mock[ApplicationService],
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mock[SessionService],
      mockErrorHandler,
      messagesApi
    )


    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(successful(application))
    given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens)

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest,implicitly)(sessionId).withSession(sessionParams: _*)

    def givenTheApplicationExistWithUserRole(appId: String,
                                             userRole: Role,
                                             state: ApplicationState = ApplicationState.production("", ""),
                                             access: Access = Standard(),
                                             environment: Environment = Environment.PRODUCTION)
                                                : BDDMockito.BDDMyOngoingStubbing[Future[Seq[APISubscriptionStatus]]] = {

      val application = Application(appId, clientId, "app", DateTimeUtils.now, DateTimeUtils.now, environment,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), state = state, access = access)

      given(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(appId))(any[HeaderCarrier])).willReturn(tokens)
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty)
    }
  }

  "The credentials page" should {
    "be displayed for an app" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)

      val result: Result = await(underTest.credentials(appId)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Credentials")
      bodyOf(result) should include("Your credentials are")
    }

    "inform the user that only admins can access credentials when the user has the developer role and the app is in PROD" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.credentials(appId)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Credentials")
      bodyOf(result) should include("You cannot view or edit production credentials because you're not an administrator")
    }

    "inform the user about the state of the application when it has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.credentials(appId)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Credentials")
      bodyOf(result) should include("Production credentials have been requested")
    }
  }

  "The client ID page" should {
    "be displayed for an app" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)

      val result: Result = await(underTest.clientId(appId)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Client ID")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.clientId(appId)(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.clientId(appId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The client secrets page" should {
    "be displayed for an app" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)

      val result: Result = await(underTest.clientSecrets(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("Client secrets")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.clientSecrets(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.clientSecrets(appId)(loggedInRequest.withCSRFToken))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "addClientSecret" should {
    val appId = "1234"
    val updatedTokens = ApplicationToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token")

    "add the client secret" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)
      given(underTest.applicationService.addClientSecret(mockEq(appId), mockEq(loggedInUser.email))(any[HeaderCarrier])).willReturn(updatedTokens)

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/client-secrets")
      verify(underTest.applicationService).addClientSecret(mockEq(appId), mockEq(loggedInUser.email))(any[HeaderCarrier])
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, environment = Environment.PRODUCTION)
      when(underTest.applicationService.addClientSecret(mockEq(appId), mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, environment = Environment.SANDBOX)
      when(underTest.applicationService.addClientSecret(mockEq(appId), mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .thenReturn(failed(new ClientSecretLimitExceeded))

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the NotFound page when the application does not exist" in new Setup {
      when(underTest.applicationService.fetchByApplicationId(mockEq(appId))(any[HeaderCarrier]))
        .thenReturn(successful(application))
      when(underTest.applicationService.addClientSecret(mockEq(appId), mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .thenReturn(failed(new ApplicationNotFound))

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe FORBIDDEN
      verify(underTest.applicationService, never()).addClientSecret(any[String], any[String])(any[HeaderCarrier])
    }

    "display the error page when the application has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.addClientSecret(appId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never()).addClientSecret(any[String])(any[HeaderCarrier])
    }

    "return to the login page when the user is not logged in" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR)

      val result: Result = await(underTest.addClientSecret(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      verify(underTest.applicationService, never()).addClientSecret(any[String], any[String])(any[HeaderCarrier])
    }
  }

  "deleteClientSecret" should {
    "return the confirmation page when the selected client secret exists" in new Setup {
      val result: Result = await(underTest.deleteClientSecret(appId, "ret2")(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      bodyOf(result) should include("Are you sure you want to delete this client secret?")
      bodyOf(result) should include("client secret ending ret2")
    }

    "return 404 when the selected client secret does not exist" in new Setup {
      val result: Result = await(underTest.deleteClientSecret(appId, "wxyz")(loggedInRequest.withCSRFToken))

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.deleteClientSecret(appId, "ret2")(loggedInRequest.withCSRFToken))

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.deleteClientSecret(appId, "ret2")(loggedInRequest.withCSRFToken))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "deleteClientSecretAction" should {
    "delete the selected client secret" in new Setup {
      val clientSecretToDelete: String = tokens.production.clientSecrets.last.secret
      val clientSecretLastChars: String = clientSecretToDelete.takeRight(4) // scalastyle:ignore magic.number
      given(underTest.applicationService.deleteClientSecrets(mockEq(appId), mockEq(Seq(clientSecretToDelete)))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      val result: Result = await(underTest.deleteClientSecretAction(appId, clientSecretLastChars)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/client-secrets")
    }

    "return 404 when the client secret to delete does not exist" in new Setup {
      val clientSecretToDelete: String = "1234wxyz"
      val clientSecretLastChars: String = "wxyz"
      given(underTest.applicationService.deleteClientSecrets(mockEq(appId), mockEq(Seq(clientSecretToDelete)))(any[HeaderCarrier]))
        .willReturn(successful(ApplicationUpdateSuccessful))

      val result: Result = await(underTest.deleteClientSecretAction(appId, clientSecretLastChars)(loggedInRequest))

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup {
      givenTheApplicationExistWithUserRole(appId, DEVELOPER, environment = Environment.PRODUCTION)

      val result: Result = await(underTest.deleteClientSecretAction(appId, "ret2")(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup {
      givenTheApplicationExistWithUserRole(appId, ADMINISTRATOR, state = ApplicationState.pendingGatekeeperApproval(""))

      val result: Result = await(underTest.deleteClientSecretAction(appId, "ret2")(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

}
