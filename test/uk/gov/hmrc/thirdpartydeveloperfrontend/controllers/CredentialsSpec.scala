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

import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.apache.pekko.util.ByteString
import views.html.editapplication.DeleteClientSecretView
import views.html.{ClientIdView, ClientSecretsGeneratedView, ClientSecretsView, CredentialsView, ServerTokenView}

import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.CommandFailures
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApplicationCommandConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, ClientSecretHashingService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class CredentialsSpec
    extends BaseControllerSpec
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with UserBuilder
    with LocalUserIdTracker
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val appTokens: ApplicationToken  = ApplicationToken(List(aClientSecret("secret1"), aClientSecret("secret2")), "token")
  val applicationId: ApplicationId = applicationIdOne

  trait ApplicationProvider {
    def anApplication: ApplicationWithCollaborators

    def modifiers: ApplicationWithCollaborators => ApplicationWithCollaborators = (app) => app

    final def createApplication() = modifiers(anApplication)
  }
  val productionState: ApplicationState = ApplicationState(State.PRODUCTION, Some(userSession.developer.email.text), Some(userSession.developer.displayedName), Some(""), instant)
  val pendingGatekeeperApproval: ApplicationState = productionState.copy(name = State.PENDING_GATEKEEPER_APPROVAL)

  trait ApplicationProviderWithAdmin extends ApplicationProvider {
    def anApplication: ApplicationWithCollaborators = standardApp.withCollaborators(userSession.developer.email.asAdministratorCollaborator)
  }

  trait ApplicationProviderWithDev extends ApplicationProvider {
    def anApplication: ApplicationWithCollaborators = standardApp.withCollaborators(userSession.developer.email.asDeveloperCollaborator)
  }

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

    val application                                                = createApplication()
    val applicationWithSubscriptions: ApplicationWithSubscriptions = application.withSubscriptions(Set.empty)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    givenApplicationAction(applicationWithSubscriptions, userSession)
    fetchCredentialsReturns(application, appTokens)
    fetchSessionByIdReturns(sessionId, userSession)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams: Seq[(String, String)]                  = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val actor: Actors.AppCollaborator                         = Actors.AppCollaborator(userSession.developer.email)
  }

  "The credentials page" should {
    "be displayed for an app" in new Setup with ApplicationProviderWithAdmin {
      val result: Future[Result] = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("Your credentials are")
    }

    "inform the user that only admins can access credentials when the user has the developer role and the app is in PROD" in new Setup with ApplicationProviderWithDev {
      val result: Future[Result] = underTest.credentials(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Credentials")
      contentAsString(result) should include("You cannot view or edit production credentials because you're not an administrator")
    }
  }

  "The client ID page" should {
    "be displayed for an app" in new Setup with ApplicationProviderWithAdmin {
      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Client ID")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup with ApplicationProviderWithDev {
      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.withState(pendingGatekeeperApproval)

      val result: Future[Result] = underTest.clientId(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The client secrets page" should {
    "be displayed for an app" in new Setup with ApplicationProviderWithAdmin {
      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Client secrets")
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup with ApplicationProviderWithDev {
      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = (app) => app.withState(pendingGatekeeperApproval)

      val result: Accumulator[ByteString, Result] = underTest.clientSecrets(applicationId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "The server token page" should {
    val dateBeforeCutoff = Credentials.serverTokenCutoffDate.minus(1, DAYS)
    val dateAfterCutoff  = Credentials.serverTokenCutoffDate.plus(1, DAYS)

    "be displayed for an app" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.modify(_.copy(createdOn = dateBeforeCutoff))

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Server token")
    }

    "return 404 for new apps" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.modify(_.copy(createdOn = dateAfterCutoff))

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when the user has the developer role and the app is in PROD" in new Setup with ApplicationProviderWithDev {
      override def modifiers = _.modify(_.copy(createdOn = dateBeforeCutoff))

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.modify(_.copy(createdOn = dateBeforeCutoff)).withState(pendingGatekeeperApproval)

      val result: Future[Result] = underTest.serverToken(applicationId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "addClientSecret" should {
    "add the client secret" in new Setup with ApplicationProviderWithAdmin {

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(application)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe OK
    }

    "display the error when the maximum limit of secret has been exceeded in a production app" in new Setup with ApplicationProviderWithAdmin {
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.ClientSecretLimitExceeded)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the error when the maximum limit of secret has been exceeded for sandbox app" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.withEnvironment(Environment.SANDBOX)

      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.ClientSecretLimitExceeded)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe UNPROCESSABLE_ENTITY
    }

    "display the NotFound page when the application does not exist" in new Setup with ApplicationProviderWithAdmin {
      reset(applicationActionServiceMock) // Wipe givenApplicationActionReturns
      givenApplicationActionReturnsNotFound(applicationId)

      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe NOT_FOUND
    }

    "display the error page when a user with developer role tries to add production secrets" in new Setup with ApplicationProviderWithDev {
      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }

    "display the error page when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.withState(pendingGatekeeperApproval)

      val result: Future[Result] = (underTest.addClientSecret(applicationId)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }

    "return to the login page when the user is not logged in" in new Setup with ApplicationProviderWithAdmin {
      val result: Future[Result] = underTest.addClientSecret(applicationId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
      ApplicationCommandConnectorMock.Dispatch.verifyNeverCalled()
    }
  }

  "deleteClientSecret" should {
    val clientSecretToDelete: ClientSecretResponse = appTokens.clientSecrets.last
    val nonExistantClientSecretId: ClientSecret.Id = ClientSecret.Id.random

    "return the confirmation page when the selected client secret exists" in new Setup with ApplicationProviderWithAdmin {
      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Are you sure you want to delete this client secret?")
      contentAsString(result) should include("client secret ending ret2")
    }

    "return 404 when the selected client secret does not exist" in new Setup with ApplicationProviderWithAdmin {
      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, nonExistantClientSecretId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup with ApplicationProviderWithDev {
      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.withState(pendingGatekeeperApproval)

      val result: Accumulator[ByteString, Result] = underTest.deleteClientSecret(applicationId, clientSecretToDelete.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "deleteClientSecretAction" should {
    val clientSecretId = ClientSecret.Id.random

    "delete the selected client secret" in new Setup with ApplicationProviderWithAdmin {
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(application)
      // givenDeleteClientSecretSucceeds(application, actor, clientSecretId)

      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${applicationId}/client-secrets")
    }

    "return 403 when a user with developer role tries do delete production secrets" in new Setup with ApplicationProviderWithDev {
      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return 400 when the application has not reached production state" in new Setup with ApplicationProviderWithAdmin {
      override def modifiers = _.withState(pendingGatekeeperApproval)

      val result: Future[Result] = underTest.deleteClientSecretAction(applicationId, clientSecretId)(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }

  private def aClientSecret(secretName: String) = ClientSecretResponse(ClientSecret.Id.random, secretName, instant)

}
