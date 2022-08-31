/*
 * Copyright 2022 HM Revenue & Customs
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

import org.jsoup.Jsoup

import java.util.UUID.randomUUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.SaveSubsFieldsPageMode
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, DevhubAccessLevel, DevhubAccessRequirements}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessRequirement._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APISubscriptionStatus, ApiContext, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{CheckInformation, Privileged, Standard, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{TestApplications, WithCSRFAddToken}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions.{EditApiMetadataFieldView, EditApiMetadataView, ListApiSubscriptionsView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldValue
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldName
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._

import java.time.{LocalDateTime, ZoneOffset}

class ManageSubscriptionsSpec 
    extends BaseControllerSpec 
    with WithCSRFAddToken
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar     
    with TestApplications
    with DeveloperBuilder
    with LocalUserIdTracker {

  val failedNoApp: Future[Nothing] = failed(new ApplicationNotFound)

  val apiContext = ApiContext("test")
  val apiVersion = ApiVersion("1.0")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val role = CollaboratorRole.ADMINISTRATOR

  val application: Application = Application(
    appId,
    clientId,
    "App name 1",
    LocalDateTime.now(ZoneOffset.UTC),
    Some(LocalDateTime.now(ZoneOffset.UTC)),
    None,
    grantLength,
    Environment.SANDBOX,
    Some("Description 1"),
    Set(loggedInDeveloper.email.asCollaborator(role)),
    state = ApplicationState.production(loggedInDeveloper.email, loggedInDeveloper.displayedName, ""),
    access = Standard(
      redirectUris = List("https://red1", "https://red2"),
      termsAndConditionsUrl = Some("http://tnc-url.com")
    )
  )

  val productionApplication = application.copy(deployedTo = Environment.PRODUCTION, id = ApplicationId(appId + "_Prod"))

  val privilegedApplication: Application = application.copy(id = ApplicationId("456"), access = Privileged())

  val tokens: ApplicationToken =
    ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  private val sessionParams = Seq(
    "csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken
  )

  trait ManageSubscriptionsSetup extends AppsByTeamMemberServiceMock with ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock {
    val mockAuditService: AuditService = mock[AuditService]
    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockErrorHandler: ErrorHandler = app.injector.instanceOf[ErrorHandler]

    // Views from Guice
    val listApiSubscriptionsView = app.injector.instanceOf[ListApiSubscriptionsView]
    val editApiMetadataView = app.injector.instanceOf[EditApiMetadataView]
    val editApiMetadataFieldView =app.injector.instanceOf[EditApiMetadataFieldView]
    val subscriptionConfigurationStartView = app.injector.instanceOf[SubscriptionConfigurationStartView]
    val subscriptionConfigurationPageView = app.injector.instanceOf[SubscriptionConfigurationPageView]
    val subscriptionConfigurationStepPageView = app.injector.instanceOf[SubscriptionConfigurationStepPageView]


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

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    fetchAppsByTeamMemberReturns(Environment.PRODUCTION)(Seq(ApplicationWithSubscriptionIds.from(application)))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)

    def editFormPostRequest(fieldName: FieldName, fieldValue: FieldValue): FakeRequest[AnyContentAsFormUrlEncoded] = {
      loggedInRequest
        .withFormUrlEncodedBody(fieldName.value -> fieldValue.value)
    }

    def assertCommonEditFormFields(result: Future[Result], apiSubscriptionStatus: APISubscriptionStatus): Unit = {
      status(result) shouldBe OK

      contentAsString(result) should include(apiSubscriptionStatus.name)
      contentAsString(result) should include(apiSubscriptionStatus.apiVersion.version.value)

      val fields = apiSubscriptionStatus.fields.fields.toList

      for (field <- fields) {
        if (field.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.Admininstator)) {
          contentAsString(result) should include(field.definition.description)
          contentAsString(result) should include(field.definition.hint)
        } else {
          contentAsString(result) should not include(field.definition.description)
          contentAsString(result) should not include(field.definition.hint)

        }
      }
    }

    def assertIsApiConfigureEditPage(result: Future[Result]): Unit = {
      contentAsString(result) should include("Subscription configuration")
      contentAsString(result) should include("Environment")
      contentAsString(result) should include(application.name)
    }

    def assertIsSandboxJourneyApiConfigureEditPage(result: Future[Result]): Unit = {
      contentAsString(result) should not include "Subscription configuration"
      contentAsString(result) should not include "Environment"
      contentAsString(result) should not include application.name
    }
  }

  "ManageSubscriptions" when {
    "using an application pending approval" should {

      trait PendingApprovalReturnsBadRequest extends ManageSubscriptionsSetup {
        def executeAction: () => Future[Result]

        val pageNumber = 1

        val apiVersion = exampleSubscriptionWithFields("api1", 1)
        val subsData = List(
          apiVersion
        )

        val app = aStandardPendingApprovalApplication(developer.email)

        givenApplicationAction(ApplicationWithSubscriptionData(app, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        val result = executeAction()

        status(result) shouldBe BAD_REQUEST
      }

      "return a bad request for subscriptionConfigurationStart action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => manageSubscriptionController.subscriptionConfigurationStart(app.id)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => manageSubscriptionController.subscriptionConfigurationPage(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationPagePost action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => manageSubscriptionController.subscriptionConfigurationPagePost(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for subscriptionConfigurationStepPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => manageSubscriptionController.subscriptionConfigurationStepPage(app.id, pageNumber)(loggedInRequest)
      }

      "return a bad request for listApiSubscriptions action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => manageSubscriptionController.listApiSubscriptions(app.id)(loggedInRequest)
      }

      "return a bad request for editApiMetadataPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction =
          () =>
            manageSubscriptionController.editApiMetadataPage(app.id, apiVersion.context, apiVersion.apiVersion.version, SaveSubsFieldsPageMode.CheckYourAnswers)(loggedInRequest)
      }

      "return a bad request for saveSubscriptionFields action" in new PendingApprovalReturnsBadRequest {
        def executeAction =
          () =>
            manageSubscriptionController.saveSubscriptionFields(app.id, apiVersion.context, apiVersion.apiVersion.version, SaveSubsFieldsPageMode.CheckYourAnswers)(loggedInRequest)
      }
    }

    "a user is logged in" when {

      "the subscriptions list action is called it" should {
        "return the list subscription configuration page with no subscriptions and therefore no subscription field definitions" in new ManageSubscriptionsSetup {
          givenApplicationAction(application, loggedInDeveloper)

          private val result =
            manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions without subscription configuration" in new ManageSubscriptionsSetup {

          val subsData = List(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions, some with subscription configuration" in new ManageSubscriptionsSetup {

          val subsData = List(
            exampleSubscriptionWithFields("api1", 3),
            exampleSubscriptionWithFields("api2", 1),
            exampleSubscriptionWithoutFields("api3"),
            exampleSubscriptionWithFields("api4", 1).copy(subscribed = false)
          )

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe OK
          contentAsString(result) should include(loggedInDeveloper.displayedName)
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
          givenApplicationAction(application, loggedInDeveloper)

          private val result = manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest)

          status(result) shouldBe NOT_FOUND
        }

        "It renders the subscription configuration list page for a privileged application" in new ManageSubscriptionsSetup {
          val subsData = List(
            exampleSubscriptionWithFields("api1", 1)
          )

          givenApplicationAction(ApplicationWithSubscriptionData(privilegedApplication, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result = manageSubscriptionController.listApiSubscriptions(privilegedApplication.id)(loggedInRequest)

          status(result) shouldBe OK
        }
      }

      "the editApiMetadataPage is called it" should {
        "renders the edit subscription page" in new ManageSubscriptionsSetup {
          val whoCanWrite = NoOne
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val wrapper = buildSubscriptionFieldsWrapper(application, List(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)
          val subsData = List(apiSubscriptionStatus)

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataPage(appId, ApiContext("/api1-api"), ApiVersion("1.0"), SaveSubsFieldsPageMode.LeftHandNavigation))(
              loggedInRequest
            )

          assertCommonEditFormFields(result, apiSubscriptionStatus)

          contentAsString(result) should include(application.name)
          contentAsString(result) should include("Sandbox")
        }
      }

      "the edit single subscription field page is called it" should {
        "render the page" in new ManageSubscriptionsSetup {
          val whoCanWrite = Anyone
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val fieldName = "my-field-name"
          val field = buildSubscriptionFieldValue(fieldName, Some("old-value"), accessDenied)
          val wrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)
          val subsData = List(apiSubscriptionStatus)

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(appId, ApiContext("/api1-api"), ApiVersion("1.0"), fieldName, SaveSubsFieldsPageMode.LeftHandNavigation))(
              loggedInRequest
            )

          status(result) shouldBe OK

          contentAsString(result) should include(apiSubscriptionStatus.name)
          contentAsString(result) should include(apiSubscriptionStatus.apiVersion.version.value)

          contentAsString(result) should include(field.definition.description)
          contentAsString(result) should include(field.definition.hint)
          contentAsString(result) should include(field.value.value)
        }

        "use the description if no hint text is available" in new ManageSubscriptionsSetup {
          val whoCanWrite = Anyone
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val fieldName = "my-field-name"
          val field = buildSubscriptionFieldValue(fieldName, Some("old-value"), accessDenied, Some(""))
          val wrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)
          val subsData = List(apiSubscriptionStatus)

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(appId, ApiContext("/api1-api"), ApiVersion("1.0"), fieldName, SaveSubsFieldsPageMode.LeftHandNavigation))(
              loggedInRequest
            )

          status(result) shouldBe OK
          private val dom = Jsoup.parse(contentAsString(result))
          dom.getElementById(s"${fieldName}-hint").html() shouldBe field.definition.description
        }

        "404 for invalid field name" in new ManageSubscriptionsSetup {
          val whoCanWrite = Anyone
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val field = buildSubscriptionFieldValue("fieldName", Some("value"), accessDenied)
          val wrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)
          val subsData = List(apiSubscriptionStatus)

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(appId, ApiContext("/api1-api"), ApiVersion("1.0"), "invalid-field-name", SaveSubsFieldsPageMode.CheckYourAnswers))(
              loggedInRequest
            )

          status(result) shouldBe NOT_FOUND
        }

        "403/404 for read only field" in new ManageSubscriptionsSetup{
          val whoCanWrite = NoOne
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val fieldName = "my-field-name"
          val field = buildSubscriptionFieldValue(fieldName, Some("old-value"), accessDenied)
          val wrapper = buildSubscriptionFieldsWrapper(application, List(field))

          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)
          val subsData = List(apiSubscriptionStatus)

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          private val result =
            addToken(manageSubscriptionController.editApiMetadataFieldPage(appId, ApiContext("/api1-api"), ApiVersion("1.0"), fieldName, SaveSubsFieldsPageMode.CheckYourAnswers))(
              loggedInRequest
            )

          status(result) shouldBe FORBIDDEN
        }
      }

      "the page mode for saveSubscriptionFields action" when {
        "LeftHandNavigation" should {
          saveSubscriptionFieldsTest(SaveSubsFieldsPageMode.LeftHandNavigation, s"/developer/applications/${appId.value}/api-metadata")
        }

        "CheckYourAnswers" should {
          saveSubscriptionFieldsTest(SaveSubsFieldsPageMode.CheckYourAnswers, s"/developer/applications/${appId.value}/check-your-answers#configurations")
        }

        def saveSubscriptionFieldsTest(mode: SaveSubsFieldsPageMode, expectedRedirectUrl: String): Unit = {
          s"save action saves valid subscription field values in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "new value"
            private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

            val subsData = List(apiSubscriptionStatus)
            givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
              .thenReturn(successful(SaveSubscriptionFieldsSuccessResponse))

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.version, mode))(
                loggedInWithFormValues
              )

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields = Map(subSubscriptionValue.definition.name -> FieldValue(newSubscriptionValue))

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.version),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields)
              )(*)
          }

          s"save action saves valid subscription field values in mode [$mode] and with a read only field" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 2)

            private val writableSubSubscriptionValue = apiSubscriptionStatus.fields.fields(1)

            val subsData = List(apiSubscriptionStatus)
            givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

            val newSubscriptionValue = "new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              writableSubSubscriptionValue.definition.name.value -> newSubscriptionValue
            )

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.version, mode))(
                loggedInWithFormValues
              )

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields = Map(writableSubSubscriptionValue.definition.name -> FieldValue(newSubscriptionValue))

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.version),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields)
              )(*)
          }

          s"save action saves valid subscription field values in mode [$mode] fails and with a read only field is passed in by a bad actor" in new ManageSubscriptionsSetup {
            val whoCanWrite = NoOne
            val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

            val wrapper = buildSubscriptionFieldsWrapper(application, List(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))

            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)

            private val readonlySubSubscriptionValue = apiSubscriptionStatus.fields.fields(0)

            val subsData = List(apiSubscriptionStatus)
            givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            val newSubscriptionValue = "illegal new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              readonlySubSubscriptionValue.definition.name.value -> newSubscriptionValue
            )

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.version, mode))(
                loggedInWithFormValues
              )

            status(result) shouldBe FORBIDDEN
          }

          s"save action fails validation and shows error message in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "my invalid value"
            val fieldErrors = Map("apiName" -> "apiName is invalid error message")

            val subsData = List(apiSubscriptionStatus)
            givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

            private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.version, mode))(
                loggedInWithFormValues
              )

            status(result) shouldBe BAD_REQUEST

            assertIsApiConfigureEditPage(result)

            contentAsString(result) should include("apiName is invalid error message")
          }

          s"save action fails with access denied and shows error message in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "my invalid value"

            val subsData = List(apiSubscriptionStatus)
            givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

            when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

            private val result =
              addToken(manageSubscriptionController.saveSubscriptionFields(appId, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion.version, mode))(
                loggedInWithFormValues
              )

            status(result) shouldBe FORBIDDEN
          }

          s"return to the login page when the user attempts to edit subscription configuration in mode [$mode]" in new ManageSubscriptionsSetup {

            val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

            val fakeContext = ApiContext("FAKE")
            val fakeVersion = ApiVersion("1.0")

            private val result =
              manageSubscriptionController.editApiMetadataPage(appId, fakeContext, fakeVersion, mode)(request)

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/developer/login")
          }

          s"return not found when trying to edit api subscription configuration for an api the application is not subscribed to in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val subsData = List(apiSubscriptionStatus)

            givenApplicationAction(ApplicationWithSubscriptionData(application, Set.empty, Map.empty), loggedInDeveloper, subsData)

            private val result = manageSubscriptionController.editApiMetadataPage(appId, apiContext, apiVersion, mode)(loggedInRequest)

            status(result) shouldBe NOT_FOUND
          }
        }
      }

      "subscriptionConfigurationPagePost save action is called it" should {
        "and fails validation it should show error message and renders the add app journey page subs configuration" in new ManageSubscriptionsSetup {
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
          val newSubscriptionValue = "my invalid value"
          val pageNumber = 1

          val fieldErrors = Map("apiName" -> "apiName is invalid error message")

          val subsData = List(apiSubscriptionStatus)
          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
            .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

          private val subSubscriptionValue = apiSubscriptionStatus.fields.fields.head

          private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name, FieldValue(newSubscriptionValue))

          private val result = addToken(manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues)

          status(result) shouldBe BAD_REQUEST

          assertIsSandboxJourneyApiConfigureEditPage(result)

          contentAsString(result) should include("apiName is invalid error message")
        }

        "and fails with access denied and shows forbidden error message" in new ManageSubscriptionsSetup {
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
          val newSubscriptionValue = "my invalid value"
          val pageNumber = 1

          val subsData = List(apiSubscriptionStatus)
          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          when(mockSubscriptionFieldsService.saveFieldValues(*, *, *[ApiContext], *[ApiVersion], *, *)(*))
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
        val subsData = List(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("api1-name")
        contentAsString(result) should include("api2-name")
        contentAsString(result) should include("1.0")
        contentAsString(result) should include("Stable")
      }

      "edit page" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
        val subsData = List(apiSubscriptionStatus)

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        private val result = addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest)

        assertCommonEditFormFields(result, apiSubscriptionStatus)
      }

      "return NOT_FOUND if page has no field definitions" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithoutFields("api1")
        val subsData = List(apiSubscriptionStatus)

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        private val result = addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest)

        status(result) shouldBe NOT_FOUND
      }

      "return NOT_FOUND if page number is invalid for edit page " when {
        def testEditPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
          import manageSubscriptionsSetup._

          val subsData = List(
            exampleSubscriptionWithFields("api1", count)
          )

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          val result = manageSubscriptionController.subscriptionConfigurationPage(appId, -1)(loggedInRequest)

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
        val subsData = List(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(appId, 1)(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include("You have completed step 1 of 2")
      }

      "step page for the last page as a redirect for sandbox" in new ManageSubscriptionsSetup {
        val subsData = List(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(appId, 2)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${application.id.value}/add/success")
      }

      "step page for the last page as a redirect for production" in new ManageSubscriptionsSetup {
        val subsData = List(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationAction(ApplicationWithSubscriptionData(productionApplication, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

        givenUpdateCheckInformationSucceeds(productionApplication)

        private val result = manageSubscriptionController.subscriptionConfigurationStepPage(productionApplication.id, 2)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${productionApplication.id.value}/request-check")

        verify(applicationServiceMock).updateCheckInformation(eqTo(productionApplication), eqTo(CheckInformation(apiSubscriptionConfigurationsConfirmed = true)))(
          *
        )
      }

      "return NOT_FOUND if page number is invalid for step page " when {
        def testStepPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
          import manageSubscriptionsSetup._

          val subsData = List(
            exampleSubscriptionWithFields("api1", count)
          )

          givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), asFields(subsData)), loggedInDeveloper, subsData)

          val result = manageSubscriptionController.subscriptionConfigurationStepPage(appId, -1)(loggedInRequest)

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
        val subsData = List(exampleSubscriptionWithoutFields("api1"))

        givenApplicationAction(ApplicationWithSubscriptionData(application, asSubscriptions(subsData), Map.empty), loggedInDeveloper, subsData)

        private val result = manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/add/success")
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

  private def aClientSecret() =
    ClientSecret(
      randomUUID.toString,
      randomUUID.toString,
      LocalDateTime.now
    )
}
