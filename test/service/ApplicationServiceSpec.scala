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

package service

import java.util.UUID
import java.util.UUID.randomUUID

import cats.data.NonEmptyList
import config.ApplicationConfig
import connectors._
import controllers.EditApplicationForm
import domain.APIStatus._
import domain.ApiSubscriptionFields._
import domain._
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.OK
import service.AuditAction.{Remove2SVRequested, UserLogoutSurveyCompleted}
import service.SubscriptionFieldsService.{DefinitionsByApiVersion, SubscriptionFieldsConnector}
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier, HttpResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.util.Random

class ApplicationServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]
    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val mockProductionSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]
    val mockSandboxSubscriptionFieldsConnector: SubscriptionFieldsConnector = mock[SubscriptionFieldsConnector]

    val mockDeveloperConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]

    val mockAuditService: AuditService = mock[AuditService]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockSandboxSubscriptionFieldsConnector,
      mockProductionSubscriptionFieldsConnector,
      mockAppConfig
    )

    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]

    val applicationService = new ApplicationService(
      connectorsWrapper,
      mockSubscriptionFieldsService,
      mockDeskproConnector,
      mockAppConfig,
      mockDeveloperConnector,
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mockAuditService
    )

    def theProductionConnectorWillReturnTheApplication(applicationId: String, application: Application): Unit = {
      given(mockProductionApplicationConnector.fetchApplicationById(applicationId))
        .willReturn(Future.successful(Some(application)))
      given(mockSandboxApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
    }

    def theSandboxConnectorWillReturnTheApplication(applicationId: String, application: Application): Unit = {
      given(mockProductionApplicationConnector.fetchApplicationById(applicationId)).willReturn(Future.successful(None))
      given(mockSandboxApplicationConnector.fetchApplicationById(applicationId))
        .willReturn(Future.successful(Some(application)))
    }

    def theSubscriptionFieldsServiceValuesWillReturn(
        fields: Seq[ApiSubscriptionFields.SubscriptionFieldValue]
    ): Unit = {
      given(mockSubscriptionFieldsService.fetchFieldsValues(any[Application], any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(fields))
    }

    def theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(allFields: DefinitionsByApiVersion): Unit = {
      given(mockSubscriptionFieldsService.getAllFieldDefinitions(any())(any[HeaderCarrier]))
        .willReturn(successful(allFields))
    }
  }

  def version(version: String, status: APIStatus, subscribed: Boolean): VersionSubscription =
    VersionSubscription(APIVersion(version, status), subscribed)

  def api(name: String, context: String, requiresTrust: Option[Boolean], versions: VersionSubscription*): APISubscription =
    APISubscription(name, name, context, versions, requiresTrust)

  val productionApplicationId = "Application ID"
  val productionClientId = s"client-id-${randomUUID().toString}"
  val productionApplication: Application =
    Application(productionApplicationId, productionClientId, "name", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("description"), Set())
  val sandboxApplicationId = "Application ID"
  val sandboxClientId = "Client ID"
  val sandboxApplication: Application = Application(sandboxApplicationId, sandboxClientId, "name", DateTimeUtils.now, DateTimeUtils.now, Environment.SANDBOX, Some("description"))

  def subStatusWithoutFieldValues(
      appId: String,
      clientId: String,
      name: String,
      context: String,
      version: String,
      status: APIStatus = STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false
  ): APISubscriptionStatus =
    APISubscriptionStatus(
      name = name,
      serviceName = name,
      context = context,
      apiVersion = APIVersion(version, status),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = None
    )

  def subStatus(
      appId: String,
      clientId: String,
      name: String,
      context: String,
      version: String,
      status: APIStatus = STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      subscriptionFieldWithValues: Seq[SubscriptionFieldValue] = Seq.empty
  ): APISubscriptionStatus = {
    val nelFields = NonEmptyList.fromList(subscriptionFieldWithValues.toList)
    APISubscriptionStatus(
      name = name,
      serviceName = name,
      context = context,
      apiVersion = APIVersion(version, status),
      subscribed = subscribed,
      requiresTrust = requiresTrust,
      fields = nelFields.map(fs => SubscriptionFieldsWrapper(appId, clientId, context, version, fs))
    )
  }

  "Fetch by teamMember email" should {
    val emailAddress = "user@example.com"
    val app1 = Application("id1", "cl-id1", "zapplication", DateTime.now, DateTime.now, Environment.PRODUCTION)
    val app2 = Application("id2", "cl-id2", "application", DateTime.now, DateTime.now, Environment.SANDBOX)
    val app3 = Application("id3", "cl-id3", "4pplication", DateTime.now, DateTime.now, Environment.PRODUCTION)

    val productionApps = Seq(app1, app3)
    val sandboxApps = Seq(app2)

    "sort the returned applications by name" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(successful(productionApps))

      when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(successful(sandboxApps))

      private val result = await(applicationService.fetchByTeamMemberEmail(emailAddress))
      result shouldBe Seq(app3, app2, app1)
    }

    "tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(successful(productionApps))
      when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(Future.failed(Upstream5xxResponse("Expected exception", 504, 504)))

      private val result = await(applicationService.fetchByTeamMemberEmail(emailAddress))
      result shouldBe Seq(app3, app1)
    }

    "not tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(Future.failed(Upstream5xxResponse("Expected exception", 504, 504)))
      when(mockSandboxApplicationConnector.fetchByTeamMemberEmail(emailAddress))
        .thenReturn(successful(sandboxApps))

      intercept[Upstream5xxResponse] {
        await(applicationService.fetchByTeamMemberEmail(emailAddress))
      }
    }
  }

  "Fetch by ID" should {
    "return the application fetched from the production connector when it exists there" in new Setup {
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      private val result = await(applicationService.fetchByApplicationId(productionApplicationId))
      result shouldBe productionApplication
    }

    "return the application fetched from the sandbox connector when it exists there" in new Setup {
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      private val result = await(applicationService.fetchByApplicationId(sandboxApplicationId))
      result shouldBe sandboxApplication
    }
  }

  "Fetch api subscriptions" should {

    "identify subscribed apis from available definitions" in new Setup {
      private val apiIdentifier1 = APIIdentifier("api-1/ctx", "1.0")

      val apis = Seq(
        api("api-1", apiIdentifier1.context, None, version(apiIdentifier1.version, STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true), version("1.0-RC", STABLE, subscribed = false))
      )

      private val subscriptionFieldDefinition1 = SubscriptionFieldDefinition("question1", "description1", "short-description-1", "hint1", "STRING")
      private val subscriptionFieldDefinition2 = SubscriptionFieldDefinition("question2", "description2", "short-description-2", "hint2", "STRING")

      val subscriptionFieldDefinitions = Seq(subscriptionFieldDefinition1, subscriptionFieldDefinition2)
      val subscriptionFieldsWithValue = Seq(
        SubscriptionFieldValue(subscriptionFieldDefinition1, "value1"),
        SubscriptionFieldValue(subscriptionFieldDefinition2, "")
      )

      private val fieldDefinitionsResponse = Seq(subscriptionFieldDefinitions)
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)

      theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(Map(apiIdentifier1 -> subscriptionFieldDefinitions))

      given(mockSubscriptionFieldsService.fetchFieldsValues(any[Application], any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Seq.empty))

      given(mockSubscriptionFieldsService.fetchFieldsValues(eqTo(productionApplication), eqTo(subscriptionFieldDefinitions), eqTo(apiIdentifier1))(any[HeaderCarrier]))
        .willReturn(Future.successful(subscriptionFieldsWithValue))

      private val result = await(applicationService.apisWithSubscriptions(productionApplication))

      result.size shouldBe 4

      result should contain inOrder (
        subStatus(productionApplicationId, productionClientId, "api-1", apiIdentifier1.context, "2.0", BETA),
        subStatus(
          productionApplicationId,
          productionClientId,
          "api-1",
          apiIdentifier1.context,
          apiIdentifier1.version,
          STABLE,
          subscribed = true,
          subscriptionFieldWithValues = subscriptionFieldsWithValue
        ),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0-RC", STABLE),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
      )
    }

    "include deprecated apis with subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", DEPRECATED, subscribed = true), version("1.0", STABLE, subscribed = false), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true))
      )

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)

      theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(Map.empty)
      theSubscriptionFieldsServiceValuesWillReturn(Seq.empty)

      private val result = await(applicationService.apisWithSubscriptions(productionApplication))

      result.size shouldBe 4

      result should contain inOrder (
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "0.1", DEPRECATED, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
      )
    }

    "filter out deprecated apis with no subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", DEPRECATED, subscribed = false), version("1.0", STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true))
      )

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)

      theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(Map.empty)
      theSubscriptionFieldsServiceValuesWillReturn(Seq.empty)

      private val result = await(applicationService.apisWithSubscriptions(productionApplication))

      result.size shouldBe 3

      result should contain inOrder (
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
      )
    }

    "filter out retired apis with no subscriptions" in new Setup {
      val apis = Seq(
        api("api-1", "api-1", None, version("0.1", RETIRED, subscribed = false), version("1.0", STABLE, subscribed = true), version("2.0", BETA, subscribed = false)),
        api("api-2", "api-2/ctx", None, version("1.0", BETA, subscribed = true))
      )

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(apis)

      theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(Map.empty)
      theSubscriptionFieldsServiceValuesWillReturn(Seq.empty)

      private val result = await(applicationService.apisWithSubscriptions(productionApplication))

      result.size shouldBe 3

      result should contain inOrder (
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "2.0", BETA),
        subStatus(productionApplicationId, productionClientId, "api-1", "api-1", "1.0", STABLE, subscribed = true),
        subStatus(productionApplicationId, productionClientId, "api-2", "api-2/ctx", "1.0", BETA, subscribed = true)
      )
    }

    "return empty sequence when the connector returns empty sequence" in new Setup {
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.fetchSubscriptions(productionApplicationId)).willReturn(Seq.empty)

      theSubscriptionFieldsServiceGetAllDefinitionsWillReturn(Map.empty)
      theSubscriptionFieldsServiceValuesWillReturn(Seq.empty)

      private val result = await(applicationService.apisWithSubscriptions(productionApplication))

      result shouldBe empty
    }
  }

  "Subscribe to API" should {
    "with no subscription fields definitions" in new Setup {

      private val context = "api1"
      private val version = "1.0"

      private val subscription = APIIdentifier(context, version)

      private val fieldDefinitions = Seq.empty

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

      given(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(any[HeaderCarrier]))
        .willReturn(Future.successful(fieldDefinitions))

      given(mockProductionApplicationConnector.subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(any[HeaderCarrier]))
        .willReturn(Future.successful(ApplicationUpdateSuccessful))

      await(applicationService.subscribeToApi(productionApplication, context, version)) shouldBe ApplicationUpdateSuccessful

      verify(mockProductionApplicationConnector).subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(any[HeaderCarrier])
      verify(mockSubscriptionFieldsService, never()).saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier])
    }

    "with subscription fields definitions" should {
      "but no values" in new Setup {
        private val context = "api1"
        private val version = "1.0"

        private val subscription = APIIdentifier(context, version)

        private val fieldDefinitions =
          Seq(SubscriptionFieldDefinition("name", "description", "short-description", "hint", "type"))

        private val fieldDefinitionsWithoutValues = fieldDefinitions.map(d => SubscriptionFieldValue(d, ""))

        private val fields: Fields = fieldDefinitions.map(definition => (definition.name, "")).toMap

        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

        given(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(any[HeaderCarrier]))
          .willReturn(Future.successful(fieldDefinitions))
        given(mockSubscriptionFieldsService.fetchFieldsValues(eqTo(productionApplication), eqTo(fieldDefinitions), eqTo(subscription))(any[HeaderCarrier]))
          .willReturn(Future.successful(fieldDefinitionsWithoutValues))

        given(mockProductionApplicationConnector.subscribeToApi(eqTo(productionApplicationId), any())(any[HeaderCarrier]))
          .willReturn(Future.successful(ApplicationUpdateSuccessful))
        given(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier]))
          .willReturn(Future.successful(mock[SaveSubscriptionFieldsResponse]))

        await(applicationService.subscribeToApi(productionApplication, context, version)) shouldBe ApplicationUpdateSuccessful

        verify(mockProductionApplicationConnector).subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(
          any[HeaderCarrier]
        )
        verify(mockSubscriptionFieldsService).saveFieldValues(eqTo(productionApplicationId), eqTo(context), eqTo(version), eqTo(fields))(any[HeaderCarrier])
      }

      "with values" in new Setup {
        private val context = "api1"
        private val version = "1.0"

        private val subscription = APIIdentifier(context, version)

        private val fieldDefinitions =
          Seq(SubscriptionFieldDefinition("name", "description", "short-description", "hint", "type"))

        private val fieldDefinitionsWithValues =
          fieldDefinitions.map(d => SubscriptionFieldValue(d, Random.nextString(10)))

        theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

        given(mockSubscriptionFieldsService.getFieldDefinitions(eqTo(productionApplication), eqTo(subscription))(any[HeaderCarrier]))
          .willReturn(Future.successful(fieldDefinitions))
        given(mockSubscriptionFieldsService.fetchFieldsValues(eqTo(productionApplication), eqTo(fieldDefinitions), eqTo(subscription))(any[HeaderCarrier]))
          .willReturn(Future.successful(fieldDefinitionsWithValues))

        given(mockProductionApplicationConnector.subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(any[HeaderCarrier]))
          .willReturn(Future.successful(ApplicationUpdateSuccessful))

        await(applicationService.subscribeToApi(productionApplication, context, version)) shouldBe ApplicationUpdateSuccessful

        verify(mockProductionApplicationConnector).subscribeToApi(eqTo(productionApplicationId), eqTo(subscription))(any[HeaderCarrier])
        verify(mockSubscriptionFieldsService, never()).saveFieldValues(any[String], any[String], any[String], any[Fields])(any[HeaderCarrier])
      }
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {
      private val context = "api1"
      private val version = "1.0"

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.unsubscribeFromApi(productionApplicationId, context, version))
        .willReturn(Future.successful(ApplicationUpdateSuccessful))
      given(mockProductionSubscriptionFieldsConnector.deleteFieldValues(productionApplicationId, context, version))
        .willReturn(Future.successful(FieldsDeleteSuccessResult))

      await(applicationService.unsubscribeFromApi(productionApplication, context, version)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "Update" should {
    val applicationId = "applicationId"
    val clientId = "clientId"
    val applicationName = "applicationName"
    val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, None)

    "truncate the description to 250 characters on update request" in new Setup {
      private val longDescription = "abcde" * 100
      private val editApplicationForm = EditApplicationForm("applicationId", "name", Some(longDescription))

      UpdateApplicationRequest.from(editApplicationForm, application).description.get.length shouldBe 250
    }

    "update application" in new Setup {
      private val editApplicationForm = EditApplicationForm(applicationId, "name")
      given(mockProductionApplicationConnector.update(eqTo(applicationId), any[UpdateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(Future.successful(ApplicationUpdateSuccessful))

      private val updateApplicationRequest = UpdateApplicationRequest.from(editApplicationForm, application)

      private val result = await(applicationService.update(updateApplicationRequest))
      result shouldBe ApplicationUpdateSuccessful
    }
  }

  "addClientSecret" should {
    val newClientSecretId = UUID.randomUUID().toString
    val newClientSecret = UUID.randomUUID().toString
    val actorEmailAddress = "john.requestor@example.com"

    "add a client secret for app in production environment" in new Setup {

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

      given(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actorEmailAddress)))
        .willReturn((newClientSecretId, newClientSecret))

      private val updatedToken = await(applicationService.addClientSecret(productionApplicationId, actorEmailAddress))

      updatedToken._1 shouldBe newClientSecretId
      updatedToken._2 shouldBe newClientSecret
    }

    "propagate exceptions from connector" in new Setup {

      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)

      when(mockProductionApplicationConnector.addClientSecrets(productionApplicationId, ClientSecretRequest(actorEmailAddress)))
        .thenReturn(Future.failed(new ClientSecretLimitExceeded))

      intercept[ClientSecretLimitExceeded] {
        await(applicationService.addClientSecret(productionApplicationId, actorEmailAddress))
      }
    }
  }

  "deleteClientSecret" should {
    val applicationId = UUID.randomUUID()
    val actorEmailAddress = "john.requestor@example.com"
    val secretToDelete = UUID.randomUUID().toString

    "delete a client secret" in new Setup {

      theProductionConnectorWillReturnTheApplication(applicationId.toString, productionApplication.copy(id = applicationId.toString))

      given(mockProductionApplicationConnector.deleteClientSecret(eqTo(applicationId), eqTo(secretToDelete), eqTo(actorEmailAddress))(any[HeaderCarrier]))
        .willReturn(ApplicationUpdateSuccessful)

      await(applicationService.deleteClientSecret(applicationId, secretToDelete, actorEmailAddress)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "requestUplift" should {
    val applicationId = "applicationId"
    val applicationName = "applicationName"

    val user =
      utils.DeveloperSession("Firstname", "Lastname", "email@example.com", loggedInState = LoggedInState.LOGGED_IN)

    "request uplift" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc))).willReturn(TicketCreated)
      given(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .willReturn(ApplicationUpliftSuccessful)
      await(applicationService.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "don't propagate error if failed to create deskpro ticket" in new Setup {
      val testError = new scala.RuntimeException("deskpro error")
      given(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .willReturn(ApplicationUpliftSuccessful)
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc))).willReturn(Future.failed(testError))

      await(applicationService.requestUplift(applicationId, applicationName, user)) shouldBe ApplicationUpliftSuccessful
    }

    "propagate ApplicationAlreadyExistsResponse from connector" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .willReturn(Future.failed(new ApplicationAlreadyExists))

      intercept[ApplicationAlreadyExists] {
        await(applicationService.requestUplift(applicationId, applicationName, user))
      }

      verifyZeroInteractions(mockDeskproConnector)
    }

    "propagate ApplicationNotFound from connector" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockProductionApplicationConnector.requestUplift(applicationId, UpliftRequest(applicationName, user.email)))
        .willReturn(Future.failed(new ApplicationNotFound))

      intercept[ApplicationNotFound] {
        await(applicationService.requestUplift(applicationId, applicationName, user))
      }

      verifyZeroInteractions(mockDeskproConnector)
    }
  }

  "verifyUplift" should {
    val verificationCode = "aVerificationCode"

    "verify an uplift successful" in new Setup {
      given(mockProductionApplicationConnector.verify(verificationCode)).willReturn(ApplicationVerificationSuccessful)
      await(applicationService.verify(verificationCode)) shouldBe ApplicationVerificationSuccessful
    }

    "verify an uplift with failure" in new Setup {
      given(mockProductionApplicationConnector.verify(verificationCode))
        .willReturn(Future.failed(new ApplicationVerificationFailed(verificationCode)))

      intercept[ApplicationVerificationFailed](await(applicationService.verify(verificationCode)))
    }
  }

  "add teamMember" should {
    val email = "email@testuser.com"
    val teamMember = Collaborator(email, Role.ADMINISTRATOR)
    val developer = Developer(teamMember.emailAddress, "name", "surname")
    val adminEmail = "admin.email@example.com"
    val adminsToEmail = Set.empty[String]
    val request = AddTeamMemberRequest(adminEmail, teamMember, isRegistered = true, adminsToEmail)

    "add teamMember successfully in production app" in new Setup {
      private val response = AddTeamMemberResponse(registeredUser = true)

      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request)).willReturn(response)

      await(applicationService.addTeamMember(productionApplication, adminEmail, teamMember)) shouldBe response
    }

    "create unregistered user when developer is not registered" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(None)
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request.copy(isRegistered = false)))
        .willReturn(AddTeamMemberResponse(registeredUser = false))
      given(mockDeveloperConnector.createUnregisteredUser(teamMember.emailAddress)).willReturn(successful(OK))

      await(applicationService.addTeamMember(productionApplication, adminEmail, teamMember))

      verify(mockDeveloperConnector, times(1)).createUnregisteredUser(eqTo(teamMember.emailAddress))(any())
    }

    "not create unregistered user when developer is already registered" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(Developer(teamMember.emailAddress, "name", "surname"))))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request))
        .willReturn(AddTeamMemberResponse(registeredUser = true))

      await(applicationService.addTeamMember(productionApplication, adminEmail, teamMember))

      verify(mockDeveloperConnector, times(0)).createUnregisteredUser(eqTo(teamMember.emailAddress))(any())
    }

    "propagate TeamMemberAlreadyExists from connector in production app" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request))
        .willReturn(Future.failed(new TeamMemberAlreadyExists))

      intercept[TeamMemberAlreadyExists] {
        await(applicationService.addTeamMember(productionApplication, adminEmail, teamMember))
      }
    }

    "propagate ApplicationNotFound from connector in production app" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.addTeamMember(productionApplicationId, request))
        .willReturn(Future.failed(new ApplicationAlreadyExists))
      intercept[ApplicationAlreadyExists] {
        await(applicationService.addTeamMember(productionApplication, adminEmail, teamMember))
      }
    }
    "add teamMember successfully in sandbox app" in new Setup {
      private val response = AddTeamMemberResponse(registeredUser = true)

      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId, request)).willReturn(response)

      await(applicationService.addTeamMember(sandboxApplication, adminEmail, teamMember)) shouldBe response
    }

    "propagate TeamMemberAlreadyExists from connector in sandbox app" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId, request))
        .willReturn(Future.failed(new TeamMemberAlreadyExists))
      intercept[TeamMemberAlreadyExists] {
        await(applicationService.addTeamMember(sandboxApplication, adminEmail, teamMember))
      }
    }

    "propagate ApplicationNotFound from connector in sandbox app" in new Setup {
      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.addTeamMember(sandboxApplicationId, request))
        .willReturn(Future.failed(new ApplicationAlreadyExists))
      intercept[ApplicationAlreadyExists] {
        await(applicationService.addTeamMember(sandboxApplication, adminEmail, teamMember))
      }
    }

    "include correct set of admins to email" in new Setup {
      private val verifiedAdmin = Collaborator("verified@example.com", Role.ADMINISTRATOR)
      private val unverifiedAdmin = Collaborator("unverified@example.com", Role.ADMINISTRATOR)
      private val adderAdmin = Collaborator(adminEmail, Role.ADMINISTRATOR)
      private val verifiedDeveloper = Collaborator("developer@example.com", Role.DEVELOPER)
      private val nonAdderAdmins = Seq(User("verified@example.com", Some(true)), User("unverified@example.com", Some(false)))

      private val application = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, adderAdmin, verifiedDeveloper))

      private val response = AddTeamMemberResponse(registeredUser = true)

      given(mockDeveloperConnector.fetchDeveloper(email)).willReturn(successful(Some(developer)))
      given(mockDeveloperConnector.fetchByEmails(eqTo(Set("verified@example.com", "unverified@example.com")))(any()))
        .willReturn(Future.successful(nonAdderAdmins))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, application)
      given(mockProductionApplicationConnector.addTeamMember(any(), any())(any())).willReturn(response)

      await(applicationService.addTeamMember(application, adderAdmin.emailAddress, teamMember)) shouldBe response
      verify(mockProductionApplicationConnector).addTeamMember(eqTo(productionApplicationId), eqTo(request.copy(adminsToEmail = Set("verified@example.com"))))(any())
    }
  }

  "remove teamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Seq.empty[String]

    "remove teamMember successfully from production" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .willReturn(ApplicationUpdateSuccessful)
      await(applicationService.removeTeamMember(productionApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from production" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .willReturn(Future.failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from production" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, productionApplication)
      given(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .willReturn(Future.failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "remove teamMember successfully from sandbox" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .willReturn(ApplicationUpdateSuccessful)
      await(applicationService.removeTeamMember(sandboxApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from sandbox" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .willReturn(Future.failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from sandbox" in new Setup {
      given(mockDeveloperConnector.fetchByEmails(any())(any())).willReturn(Future.successful(Seq.empty))
      theSandboxConnectorWillReturnTheApplication(sandboxApplicationId, sandboxApplication)
      given(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .willReturn(Future.failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "include correct set of admins to email" in new Setup {

      private val verifiedAdmin = Collaborator("verified@example.com", Role.ADMINISTRATOR)
      private val unverifiedAdmin = Collaborator("unverified@example.com", Role.ADMINISTRATOR)
      private val removerAdmin = Collaborator("admin.email@example.com", Role.ADMINISTRATOR)
      private val verifiedDeveloper = Collaborator("developer@example.com", Role.DEVELOPER)
      private val teamMemberToRemove = Collaborator("to.remove@example.com", Role.ADMINISTRATOR)

      val nonRemoverAdmins = Seq(
        User("verified@example.com", Some(true)),
        User("unverified@example.com", Some(false))
      )

      private val application = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

      private val response = ApplicationUpdateSuccessful

      given(mockDeveloperConnector.fetchByEmails(eqTo(Set("verified@example.com", "unverified@example.com")))(any()))
        .willReturn(Future.successful(nonRemoverAdmins))
      theProductionConnectorWillReturnTheApplication(productionApplicationId, application)
      given(mockProductionApplicationConnector.removeTeamMember(any(), any(), any(), any())(any())).willReturn(response)

      await(applicationService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress)) shouldBe response
      verify(mockProductionApplicationConnector).removeTeamMember(
        eqTo(productionApplicationId),
        eqTo(teamMemberToRemove.emailAddress),
        eqTo(removerAdmin.emailAddress),
        eqTo(Seq(verifiedAdmin.emailAddress))
      )(any())
    }
  }

  "request application deletion" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(Collaborator(adminEmail, Role.ADMINISTRATOR), Collaborator(developerEmail, Role.DEVELOPER))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val productionApp = productionApplication.copy(collaborators = teamMembers)
    val subject = "Request to delete an application"
    val captor: ArgumentCaptor[DeskproTicket] = ArgumentCaptor.forClass(classOf[DeskproTicket])

    "create a deskpro ticket and audit record for an Admin in a Sandbox app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(adminRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "create a deskpro ticket and audit record for a Developer in a Sandbox app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(developerRequester, sandboxApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe developerEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "create a deskpro ticket and audit record for an Admin in a Production app" in new Setup {

      given(mockDeskproConnector.createTicket(captor.capture())(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.requestPrincipalApplicationDeletion(adminRequester, productionApp)) shouldBe TicketCreated
      captor.getValue.email shouldBe adminEmail
      captor.getValue.subject shouldBe subject
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
    "not create a deskpro ticket or audit record for a Developer in a Production app" in new Setup {

      intercept[ForbiddenException] {
        await(applicationService.requestPrincipalApplicationDeletion(developerRequester, productionApp))
      }
      verifyZeroInteractions(mockDeskproConnector)
      verifyZeroInteractions(mockAuditService)
    }
  }

  "delete subordinate application" should {

    val adminEmail = "admin@example.com"
    val adminRequester = utils.DeveloperSession(adminEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val developerEmail = "developer@example.com"
    val developerRequester = utils.DeveloperSession(developerEmail, "firstname", "lastname", loggedInState = LoggedInState.LOGGED_IN)
    val teamMembers = Set(Collaborator(adminEmail, Role.ADMINISTRATOR), Collaborator(developerEmail, Role.DEVELOPER))
    val sandboxApp = sandboxApplication.copy(collaborators = teamMembers)
    val invalidROPCApp = sandboxApplication.copy(collaborators = teamMembers, access = ROPC())
    val productionApp = productionApplication.copy(collaborators = teamMembers)

    val expectedMessage = "Only standard subordinate applications can be deleted by admins"

    "delete standard subordinate application when requested by an admin" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any())(any[HeaderCarrier]))
        .willReturn(Future.successful(successful(())))

      await(applicationService.deleteSubordinateApplication(adminRequester, sandboxApp))

      verify(mockSandboxApplicationConnector).deleteApplication(eqTo(sandboxApplicationId))(eqTo(hc))
    }

    "throw an exception when a subordinate application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, sandboxApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a production application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, productionApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

    "throw an exception when a ROPC application is requested to be deleted by a developer" in new Setup {

      given(mockSandboxApplicationConnector.deleteApplication(any())(any[HeaderCarrier]))
        .willReturn(Future.failed(new ForbiddenException(expectedMessage)))

      private val exception = intercept[ForbiddenException](
        await(applicationService.deleteSubordinateApplication(developerRequester, invalidROPCApp))
      )
      exception.getMessage shouldBe expectedMessage
    }

  }

  "request delete developer" should {
    val developerName = "Testy McTester"
    val developerEmail = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.requestDeveloperAccountDeletion(developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
  }

  "isSubscribedToApi" should {
    val subscriptions = Seq(
      APISubscription(
        "First API",
        "",
        "first context",
        Seq(
          VersionSubscription(APIVersion("1.0", APIStatus.STABLE), subscribed = true),
          VersionSubscription(APIVersion("2.0", APIStatus.BETA), subscribed = false)
        ),
        None
      ),
      APISubscription(
        "Second API",
        "",
        "second context",
        Seq(VersionSubscription(APIVersion("1.0", APIStatus.ALPHA), subscribed = true)),
        None
      )
    )

    "return false when the application has no subscriptions to the requested api version" in new Setup {
      val apiName = "Third API"
      val apiContext = "third context"
      val apiVersion = "3.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(eqTo(productionApplication.id))(eqTo(hc)))
        .willReturn(Future.successful(subscriptions))
      private val result =
        await(applicationService.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe false
    }

    "return false when the application has unsubscribed to the requested api version" in new Setup {
      val apiName = "First API"
      val apiContext = "first context"
      val apiVersion = "2.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(eqTo(productionApplication.id))(eqTo(hc)))
        .willReturn(Future.successful(subscriptions))
      val result: Boolean =
        await(applicationService.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe false
    }

    "return true when the application is subscribed to the requested api version" in new Setup {
      val apiName = "First API"
      val apiContext = "first context"
      val apiVersion = "1.0"

      given(mockProductionApplicationConnector.fetchSubscriptions(eqTo(productionApplication.id))(eqTo(hc)))
        .willReturn(Future.successful(subscriptions))
      private val result =
        await(applicationService.isSubscribedToApi(productionApplication, apiName, apiContext, apiVersion))

      result shouldBe true
    }
  }

  "request 2SV removal" should {

    val email = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      given(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .willReturn(Future.successful(TicketCreated))
      given(mockAuditService.audit(eqTo(Remove2SVRequested), any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.request2SVRemoval(email))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(eqTo(Remove2SVRequested), any[Map[String, String]])(eqTo(hc))
    }
  }

  "userLogoutSurveyCompleted" should {

    val email = "testy@example.com"
    val name = "John Smith"
    val rating = "5"
    val improvementSuggestions = "Test"

    "audit user logout survey" in new Setup {
      given(mockAuditService.audit(eqTo(UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc)))
        .willReturn(Future.successful(Success))

      await(applicationService.userLogoutSurveyCompleted(email, name, rating, improvementSuggestions))

      verify(mockAuditService, times(1)).audit(eqTo(UserLogoutSurveyCompleted), any[Map[String, String]])(eqTo(hc))
    }
  }

  "validate application name" should {
    "call the application connector validate method in sandbox" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = randomUUID().toString

      given(mockSandboxApplicationConnector.validateName(any(), any())(any[HeaderCarrier]))
        .willReturn(Valid)

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.SANDBOX, Some(applicationId)))

      result shouldBe Valid

      verify(mockSandboxApplicationConnector).validateName(eqTo(applicationName), eqTo(Some(applicationId)))(eqTo(hc))
    }

    "call the application connector validate method in production" in new Setup {
      private val applicationName = "applicationName"
      private val applicationId = randomUUID().toString

      given(mockProductionApplicationConnector.validateName(any(), any())(any[HeaderCarrier]))
        .willReturn(Valid)

      private val result =
        await(applicationService.isApplicationNameValid(applicationName, Environment.PRODUCTION, Some(applicationId)))

      result shouldBe Valid

      verify(mockProductionApplicationConnector).validateName(eqTo(applicationName), eqTo(Some(applicationId)))(
        eqTo(hc)
      )
    }
  }

}
