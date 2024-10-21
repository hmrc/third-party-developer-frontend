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

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

import org.jsoup.Jsoup
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions.{EditApiMetadataFieldView, EditApiMetadataView, ListApiSubscriptionsView}

import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator.Roles
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, SubscriptionFieldsService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageSubscriptionsSpec
    extends BaseControllerSpec
    with SubscriptionTestHelper
    with ApplicationWithCollaboratorsFixtures
    with WithCSRFAddToken {

  val failedNoApp: Future[Nothing] = failed(new ApplicationNotFound)

  val apiContext: ApiContext    = ApiContext("test")
  val apiVersion: ApiVersionNbr = ApiVersionNbr("1.0")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val role: Roles.ADMINISTRATOR.type = Collaborator.Roles.ADMINISTRATOR

  val application: ApplicationWithCollaborators =
    standardApp
      .withName(ApplicationName("App name 1"))
      .inSandbox()
      .withAccess(standardAccess.copy(
        redirectUris = List(RedirectUri.unsafeApply("https://red1"), RedirectUri.unsafeApply("https://red2")),
        termsAndConditionsUrl = Some("http://tnc-url.com")
      ))

  val productionApplication: ApplicationWithCollaborators = application.withEnvironment(Environment.PRODUCTION)

  val privilegedApplication: ApplicationWithCollaborators = application.withAccess(Access.Privileged())

  val tokens: ApplicationToken =
    ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  trait ManageSubscriptionsSetup extends AppsByTeamMemberServiceMock with ApplicationServiceMock with ApplicationActionServiceMock {
    val mockAuditService: AuditService                           = mock[AuditService]
    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockErrorHandler: ErrorHandler                           = app.injector.instanceOf[ErrorHandler]

    // Views from Guice
    val listApiSubscriptionsView: ListApiSubscriptionsView                           = app.injector.instanceOf[ListApiSubscriptionsView]
    val editApiMetadataView: EditApiMetadataView                                     = app.injector.instanceOf[EditApiMetadataView]
    val editApiMetadataFieldView: EditApiMetadataFieldView                           = app.injector.instanceOf[EditApiMetadataFieldView]
    val subscriptionConfigurationStartView: SubscriptionConfigurationStartView       = app.injector.instanceOf[SubscriptionConfigurationStartView]
    val subscriptionConfigurationPageView: SubscriptionConfigurationPageView         = app.injector.instanceOf[SubscriptionConfigurationPageView]
    val subscriptionConfigurationStepPageView: SubscriptionConfigurationStepPageView = app.injector.instanceOf[SubscriptionConfigurationStepPageView]

    val manageSubscriptionController = new ManageSubscriptions(
      sessionServiceMock,
      mockAuditService,
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mcc,
      mockSubscriptionFieldsService,
      cookieSigner,
      listApiSubscriptionsView,
      editApiMetadataView,
      editApiMetadataFieldView,
      subscriptionConfigurationStartView,
      subscriptionConfigurationPageView,
      subscriptionConfigurationStepPageView
    )

    FetchSessionById.succeedsWith(devSession.sessionId, devSession)
    UpdateUserFlowSessions.succeedsWith(devSession.sessionId)
    FetchSessionById.succeedsWith(adminSession.sessionId, adminSession)
    UpdateUserFlowSessions.succeedsWith(adminSession.sessionId)

    val loggedInRequest = loggedInAdminRequest
    val userSession     = adminSession
    val appId           = standardApp.id
    val clientId        = standardApp.clientId

    fetchAppsByTeamMemberReturns(Environment.PRODUCTION)(Seq(application.withSubscriptions(Set.empty)))

    def editFormPostRequest(fieldName: FieldName, fieldValue: FieldValue): FakeRequest[AnyContentAsFormUrlEncoded] = {
      loggedInAdminRequest.withFormUrlEncodedBody(fieldName.value -> fieldValue.value)
    }

    def assertCommonEditFormFields(result: Future[Result], apiSubscriptionStatus: APISubscriptionStatus): Unit = {
      status(result) shouldBe OK

      contentAsString(result) should include(apiSubscriptionStatus.name)
      contentAsString(result) should include(apiSubscriptionStatus.apiVersion.versionNbr.value)

      val fields = apiSubscriptionStatus.fields.fields.toList

      for (field <- fields) {
        if (field.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.Admininstator)) {
          contentAsString(result) should include(field.definition.description)
          contentAsString(result) should include(field.definition.hint)
        } else {
          contentAsString(result) should not include (field.definition.description)
          contentAsString(result) should not include (field.definition.hint)

        }
      }
    }

    def assertIsApiConfigureEditPage(result: Future[Result]): Unit = {
      contentAsString(result) should include("Subscription configuration")
      contentAsString(result) should include("Environment")
      contentAsString(result) should include(application.name.value)
    }

    def assertIsSandboxJourneyApiConfigureEditPage(result: Future[Result]): Unit = {
      contentAsString(result) should not include "Subscription configuration"
      contentAsString(result) should not include "Environment"
      contentAsString(result) should not include application.name.value
    }
  }

  "ManageSubscriptions" when {
    "using an application pending approval" should {

      trait PendingApprovalReturnsBadRequest extends ManageSubscriptionsSetup {
        def executeAction: () => Future[Result]

        val pageNumber = 1

        val app: ApplicationWithCollaborators = standardApp.withState(appStatePendingGatekeeperApproval)

        val subsFields: APISubscriptionStatus     = exampleSubscriptionWithFields(app.id, app.clientId)("api1", 1)
        val subsData: List[APISubscriptionStatus] = List(
          subsFields
        )

        givenApplicationAction(app.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        val result: Future[Result] = executeAction()

        status(result) shouldBe BAD_REQUEST
      }

      "return a bad request for subscriptionConfigurationStart action" in new PendingApprovalReturnsBadRequest {
        def executeAction: () => Future[Result] = () => manageSubscriptionController.subscriptionConfigurationStart(app.id)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction: () => Future[Result] = () => manageSubscriptionController.subscriptionConfigurationPage(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationPagePost action" in new PendingApprovalReturnsBadRequest {
        def executeAction: () => Future[Result] = () => manageSubscriptionController.subscriptionConfigurationPagePost(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationStepPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction: () => Future[Result] = () => manageSubscriptionController.subscriptionConfigurationStepPage(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for listApiSubscriptions action" in new PendingApprovalReturnsBadRequest {
        def executeAction: () => Future[Result] = () => manageSubscriptionController.listApiSubscriptions(app.id)(loggedInRequest)
      }
    }

    "a user is logged in" when {

      "the subscriptions list action is called it" should {
        "return the list subscription configuration page with no subscriptions and therefore no subscription field definitions" in new ManageSubscriptionsSetup {
          givenApplicationAction(application, userSession)

          private val result =
            manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions without subscription configuration" in new ManageSubscriptionsSetup {

          val subsData: List[APISubscriptionStatus] = List(exampleSubscriptionWithoutFields(appId, clientId)("api1"), exampleSubscriptionWithoutFields(appId, clientId)("api2"))

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions, some with subscription configuration" in new ManageSubscriptionsSetup {

          val subsData: List[APISubscriptionStatus] = List(
            exampleSubscriptionWithFields(appId, clientId)("api1", 3),
            exampleSubscriptionWithFields(appId, clientId)("api2", 1),
            exampleSubscriptionWithoutFields(appId, clientId)("api3"),
            exampleSubscriptionWithFields(appId, clientId)("api4", 1).copy(subscribed = false)
          )

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe OK
          contentAsString(result) should include(userSession.developer.displayedName)
          contentAsString(result) should include("Sign out")
          contentAsString(result) should include(
            "Edit the configuration for these APIs you have subscribed to."
          )

          contentAsString(result) should include(generateName("api1"))
          contentAsString(result) should include("Stable")
          contentAsString(result) should include(generateValueName("api1", 1))
          contentAsString(result) should include(generateValueName("api1", 2))
          contentAsString(result) should include(generateValueName("api1", 3))
          contentAsString(result) should not include generateValueName("api1", 4)

          contentAsString(result) should include(generateName("api2"))
          contentAsString(result) should include(generateValueName("api2", 1))

          contentAsString(result) should not include generateName("api3")
          contentAsString(result) should not include generateName("api4")
        }

        "return not found if app has no subscription field definitions" in new ManageSubscriptionsSetup {
          givenApplicationAction(application, userSession)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "It renders the subscription configuration list page for a privileged application" in new ManageSubscriptionsSetup {
          val subsData: List[APISubscriptionStatus] = List(
            exampleSubscriptionWithFields(appId, clientId)("api1", 1)
          )

          givenApplicationAction(privilegedApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(privilegedApplication.id)(loggedInRequest)

          status(result) shouldBe OK
        }
      }

      "the editApiMetadataPage is called it" should {
        "renders the edit subscription page" in new ManageSubscriptionsSetup {
          val whoCanWrite: DevhubAccessRequirement.NoOne.type = NoOne
          val accessDenied: AccessRequirements                = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val wrapper: SubscriptionFieldsWrapper = buildSubscriptionFieldsWrapper(application, List(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1).copy(fields = wrapper)
          val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataPage(appId, ApiContext("/api1-api"), ApiVersionNbr("1.0")))(
              loggedInRequest
            )

          assertCommonEditFormFields(result, apiSubscriptionStatus)

          contentAsString(result) should include(application.name.value)
          contentAsString(result) should include("Sandbox")
        }
      }

      "the edit single subscription field page is called it" should {
        "render the page" in new ManageSubscriptionsSetup {
          val whoCanWrite: DevhubAccessRequirement.Anyone.type = Anyone
          val accessDenied: AccessRequirements                 = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val fieldName                          = "my-field-name"
          val field: SubscriptionFieldValue      = buildSubscriptionFieldValue(fieldName, Some("old-value"), accessDenied)
          val wrapper: SubscriptionFieldsWrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1).copy(fields = wrapper)
          val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(
              appId,
              ApiContext("/api1-api"),
              ApiVersionNbr("1.0"),
              fieldName
            ))(
              loggedInRequest
            )

          status(result) shouldBe OK

          contentAsString(result) should include(apiSubscriptionStatus.name)
          contentAsString(result) should include(apiSubscriptionStatus.apiVersion.versionNbr.value)

          contentAsString(result) should include(field.definition.description)
          contentAsString(result) should include(field.definition.hint)
          contentAsString(result) should include(field.value.value)
        }

        "use the description if no hint text is available" in new ManageSubscriptionsSetup {
          val whoCanWrite: DevhubAccessRequirement.Anyone.type = Anyone
          val accessDenied: AccessRequirements                 = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val fieldName                          = "my-field-name"
          val field: SubscriptionFieldValue      = buildSubscriptionFieldValue(fieldName, Some("old-value"), accessDenied, Some(""))
          val wrapper: SubscriptionFieldsWrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1).copy(fields = wrapper)
          val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(
              appId,
              ApiContext("/api1-api"),
              ApiVersionNbr("1.0"),
              fieldName
            ))(
              loggedInRequest
            )

          status(result) shouldBe OK
          private val dom = Jsoup.parse(contentAsString(result))
          dom.getElementById(s"${fieldName}-hint").html() shouldBe field.definition.description
        }
      }

      "the page mode for saveSubscriptionFields action" when {
        "LeftHandNavigation" should {
          val appId = standardApp.id
          saveSubscriptionFieldsTest(s"/developer/applications/${appId}/api-metadata")
        }

        def saveSubscriptionFieldsTest(expectedRedirectUrl: String): Unit = {
          s"save action saves valid subscription field values" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
            val newSubscriptionValue                         = "new value"
            private val subSubscriptionValue                 = apiSubscriptionStatus.fields.fields.head

            val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
            givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
              .thenReturn(successful(SaveSubscriptionFieldsSuccessResponse))

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.versionNbr))(
                loggedInWithFormValues
              )

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields: Map[FieldName, FieldValue] = Map(subSubscriptionValue.definition.name -> FieldValue(newSubscriptionValue))

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.versionNbr),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields)
              )(*)
          }

          s"save action saves valid subscription field values and with a read only field" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 2)

            private val writableSubSubscriptionValue = apiSubscriptionStatus.fields.fields(1)

            val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
            givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

            val newSubscriptionValue = "new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              writableSubSubscriptionValue.definition.name.value -> newSubscriptionValue
            )

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.versionNbr))(
                loggedInWithFormValues
              )

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields: Map[FieldName, FieldValue] = Map(writableSubSubscriptionValue.definition.name -> FieldValue(newSubscriptionValue))

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.versionNbr),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields)
              )(*)
          }

          s"save action saves valid subscription field values fails and with a read only field is passed in by a bad actor" in new ManageSubscriptionsSetup {
            val whoCanWrite: DevhubAccessRequirement.NoOne.type = NoOne
            val accessDenied: AccessRequirements                = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

            val wrapper: SubscriptionFieldsWrapper = buildSubscriptionFieldsWrapper(application, List(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))

            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1).copy(fields = wrapper)

            private val readonlySubSubscriptionValue = apiSubscriptionStatus.fields.fields(0)

            val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
            givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            val newSubscriptionValue = "illegal new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              readonlySubSubscriptionValue.definition.name.value -> newSubscriptionValue
            )

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.versionNbr))(
                loggedInWithFormValues
              )

            status(result) shouldBe FORBIDDEN
          }

          s"save action fails validation and shows error message" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
            val newSubscriptionValue                         = "my invalid value"
            val fieldErrors: Map[String, String]             = Map("apiName" -> "apiName is invalid error message")

            val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
            givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

            private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.versionNbr))(
                loggedInWithFormValues
              )

            status(result) shouldBe BAD_REQUEST

            assertIsApiConfigureEditPage(result)

            contentAsString(result) should include("apiName is invalid error message")
          }

          s"save action fails with access denied and shows error message" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
            val newSubscriptionValue                         = "my invalid value"

            val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
            givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.versionNbr))(
                loggedInWithFormValues
              )

            status(result) shouldBe FORBIDDEN
          }

          s"return to the login page when the user attempts to edit subscription configuration" in new ManageSubscriptionsSetup {

            val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

            val fakeContext: ApiContext    = ApiContext("FAKE")
            val fakeVersion: ApiVersionNbr = ApiVersionNbr("1.0")

            private val result =
              manageSubscriptionController.editApiMetadataPage(appId, fakeContext, fakeVersion)(request)

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/developer/login")
          }

          s"return not found when trying to edit api subscription configuration for an api the application is not subscribed to" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
            val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

            givenApplicationAction(application.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession, subsData)

            private val result = manageSubscriptionController.editApiMetadataPage(appId, apiContext, apiVersion)(loggedInRequest)

            status(result) shouldBe NOT_FOUND
          }
        }
      }

      "subscriptionConfigurationPagePost save action is called it" should {
        "and fails validation it should show error message and renders the add app journey page subs configuration" in new ManageSubscriptionsSetup {
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
          val newSubscriptionValue                         = "my invalid value"
          val pageNumber                                   = 1

          val fieldErrors: Map[String, String] = Map("apiName" -> "apiName is invalid error message")

          val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
            .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

          private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

          private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

          private val result = addToken(manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues)

          status(result) shouldBe BAD_REQUEST

          assertIsSandboxJourneyApiConfigureEditPage(result)

          contentAsString(result) should include("apiName is invalid error message")
        }

        "and fails with access denied and shows forbidden error message" in new ManageSubscriptionsSetup {
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
          val newSubscriptionValue                         = "my invalid value"
          val pageNumber                                   = 1

          val subsData: List[APISubscriptionStatus] = List(apiSubscriptionStatus)
          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersionNbr], *, *)(*))
            .thenReturn(successful(SaveSubscriptionFieldsAccessDeniedResponse))

          private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

          private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

          private val result = addToken(manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues)

          status(result) shouldBe FORBIDDEN
        }
      }
    }

    "a user is doing the add new sandbox app journey they" should {
      "be able to view the subscription fields start page if they have subscribed to APIs with subscription fields" in new ManageSubscriptionsSetup {
        val subsData: List[APISubscriptionStatus] = List(
          exampleSubscriptionWithFields(appId, clientId)("api1", 1),
          exampleSubscriptionWithFields(appId, clientId)("api2", 1)
        )

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("api1-name")
        contentAsString(result) should include("api2-name")
        contentAsString(result) should include("1.0")
        contentAsString(result) should include("Stable")
      }

      "edit page" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields(appId, clientId)("api1", 1)
        val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        private val result = addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest)

        assertCommonEditFormFields(result, apiSubscriptionStatus)
      }

      "return NOT_FOUND if page has no field definitions" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithoutFields(appId, clientId)("api1")
        val subsData: List[APISubscriptionStatus]        = List(apiSubscriptionStatus)

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        private val result = addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }

      "return NOT_FOUND if page number is invalid for edit page " when {
        def testEditPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
          import manageSubscriptionsSetup._

          val subsData = List(
            exampleSubscriptionWithFields(appId, clientId)("api1", count)
          )

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          val result = manageSubscriptionController.subscriptionConfigurationPage(application.id, -1)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "negative" in new ManageSubscriptionsSetup {
          testEditPageNumbers(-1, this)
        }
        "0" in new ManageSubscriptionsSetup {
          testEditPageNumbers(0, this)
        }
        "too large" in new ManageSubscriptionsSetup {
          testEditPageNumbers(100, this)
        }
      }

      "step page" in new ManageSubscriptionsSetup {
        val subsData: List[APISubscriptionStatus] = List(
          exampleSubscriptionWithFields(appId, clientId)("api1", 1),
          exampleSubscriptionWithFields(appId, clientId)("api2", 1)
        )

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(appId, 1)(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("You have completed step 1 of 2")
      }

      "step page for the last page as a redirect for sandbox" in new ManageSubscriptionsSetup {
        val subsData: List[APISubscriptionStatus] = List(
          exampleSubscriptionWithFields(appId, clientId)("api1", 1),
          exampleSubscriptionWithFields(appId, clientId)("api2", 1)
        )

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(appId, 2)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${application.id.value}/add/success")
      }

      "step page for the last page as a redirect for production" in new ManageSubscriptionsSetup {
        val subsData: List[APISubscriptionStatus] = List(
          exampleSubscriptionWithFields(appId, clientId)("api1", 1),
          exampleSubscriptionWithFields(appId, clientId)("api2", 1)
        )

        givenApplicationAction(productionApplication.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

        givenUpdateCheckInformationSucceeds(productionApplication)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(productionApplication.id, 2)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${productionApplication.id.value}/add/success")
      }

      "return NOT_FOUND if page number is invalid for step page " when {
        def testStepPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
          import manageSubscriptionsSetup._

          val subsData = List(
            exampleSubscriptionWithFields(appId, clientId)("api1", count)
          )

          givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(asFields(subsData)), userSession, subsData)

          val result = manageSubscriptionController.subscriptionConfigurationStepPage(application.id, -1)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "negative" in new ManageSubscriptionsSetup {
          testStepPageNumbers(-1, this)
        }
        "0" in new ManageSubscriptionsSetup {
          testStepPageNumbers(0, this)
        }
        "too large" in new ManageSubscriptionsSetup {
          testStepPageNumbers(100, this)
        }
      }

      "be redirected to the end of the journey of they haven't subscribed to any APIs with subscription fields" in new ManageSubscriptionsSetup {
        val subsData: List[APISubscriptionStatus] = List(exampleSubscriptionWithoutFields(appId, clientId)("api1"))

        givenApplicationAction(application.withSubscriptions(asSubscriptions(subsData)).withFieldValues(Map.empty), userSession, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/add/success")
      }
    }

    "when the user is not logged in" should {
      "return to the login page when the user attempts to list subscription configuration" in new ManageSubscriptionsSetup {

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

        private val result = manageSubscriptionController.listApiSubscriptions(appId)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }
  }

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, instant)
}
