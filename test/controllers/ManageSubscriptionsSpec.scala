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

import java.util.UUID.randomUUID

import config.ErrorHandler
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.applications.{CheckInformation, Privileged, Standard}
import domain.models.controllers.SaveSubsFieldsPageMode
import domain.models.developers.Session
import domain.models.subscriptions.ApiSubscriptionFields._
import domain.models.subscriptions.{AccessRequirements, DevhubAccessLevel, DevhubAccessRequirements}
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import org.joda.time.DateTimeZone
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{AuditService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future
import domain.models.subscriptions.DevhubAccessRequirement.NoOne
import domain.models.subscriptions.DevhubAccessRequirement.Anyone
import utils.TestApplications
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions.{EditApiMetadataView, ListApiSubscriptionsView}

import scala.concurrent.ExecutionContext.Implicits.global
import domain.ApplicationNotFound
import domain.models.developers.Developer
import domain.models.developers.LoggedInState
import domain.models.developers.DeveloperSession
import domain.models.applications.Application
import domain.models.applications.Role
import domain.models.applications.Environment
import domain.models.applications.Collaborator
import domain.models.applications.ApplicationState
import domain.models.applications.ApplicationToken
import domain.models.applications.ClientSecret

class ManageSubscriptionsSpec extends BaseControllerSpec with WithCSRFAddToken with SubscriptionTestHelperSugar {
  val failedNoApp: Future[Nothing] = Future.failed(new ApplicationNotFound)

  val appId = "1234"
  val clientId = "clientId123"

  val developer: Developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val apiContext = "test"
  val apiVersion = "1.0"

  val loggedInUser: DeveloperSession = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession: Session =
    Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val role = Role.ADMINISTRATOR

  val application: Application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    None,
    Environment.SANDBOX,
    Some("Description 1"),
    Set(Collaborator(loggedInUser.email, role)),
    state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(
      redirectUris = Seq("https://red1", "https://red2"),
      termsAndConditionsUrl = Some("http://tnc-url.com")
    )
  )

  val productionApplication = application.copy(deployedTo = Environment.PRODUCTION, id = appId + "_Prod")

  val privilegedApplication: Application = application.copy(id = "456", access = Privileged())

  val tokens: ApplicationToken =
    ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

  private val sessionParams = Seq(
    "csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken
  )

  trait ManageSubscriptionsSetup extends ApplicationServiceMock with SessionServiceMock {
    val mockAuditService: AuditService = mock[AuditService]
    val mockSubscriptionFieldsService: SubscriptionFieldsService = mock[SubscriptionFieldsService]
    val mockErrorHandler: ErrorHandler = fakeApplication.injector.instanceOf[ErrorHandler]

    val listApiSubscriptionsView = app.injector.instanceOf[ListApiSubscriptionsView]
    val editApiMetadataView = app.injector.instanceOf[EditApiMetadataView]
    val subscriptionConfigurationStartView = app.injector.instanceOf[SubscriptionConfigurationStartView]
    val subscriptionConfigurationPageView = app.injector.instanceOf[SubscriptionConfigurationPageView]
    val subscriptionConfigurationStepPageView = app.injector.instanceOf[SubscriptionConfigurationStepPageView]

    val manageSubscriptionController = new ManageSubscriptions(
      sessionServiceMock,
      mockAuditService,
      applicationServiceMock,
      mockErrorHandler,
      mcc,
      mockSubscriptionFieldsService,
      cookieSigner,
      listApiSubscriptionsView,
      editApiMetadataView,
      subscriptionConfigurationStartView,
      subscriptionConfigurationPageView,
      subscriptionConfigurationStepPageView
    )

    fetchSessionByIdReturns(sessionId, session)

    fetchByApplicationIdReturns(appId,application)
    fetchByApplicationIdReturns(privilegedApplication.id,privilegedApplication)
    fetchByApplicationIdReturns(productionApplication.id, productionApplication)

    fetchByTeamMemberEmailReturns(List(application))

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageSubscriptionController, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)

    def editFormPostRequest(fieldName: String, fieldValue: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      loggedInRequest
        .withFormUrlEncodedBody(
          fieldName -> fieldValue)
    }

    def assertCommonEditFormFields(result: Result, apiSubscriptionStatus: APISubscriptionStatus): Unit = {
      status(result) shouldBe OK

      bodyOf(result) should include(apiSubscriptionStatus.name)
      bodyOf(result) should include(apiSubscriptionStatus.apiVersion.version)

      val fields = apiSubscriptionStatus.fields.fields.toList

      for(field <- fields){
        bodyOf(result) should include(field.definition.description)
        bodyOf(result) should include(field.definition.hint)
        
        if (!field.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.Admininstator)){
          bodyOf(result) should include("disabled")
        }
      }
    }

    def assertIsApiConfigureEditPage(result: Result) : Unit = {
      bodyOf(result) should include("Subscription configuration")
      bodyOf(result) should include("Environment")
      bodyOf(result) should include(application.name)
    }

    def assertIsSandboxJourneyApiConfigureEditPage(result: Result) : Unit = {
      bodyOf(result) should not include "Subscription configuration"
      bodyOf(result) should not include "Environment"
      bodyOf(result) should not include application.name
    }
  }

  "ManageSubscriptions" when {
    "using an appplication pending approval" should {

      trait PendingApprovalReturnsBadRequest extends ManageSubscriptionsSetup with TestApplications {
        def executeAction: () => Result

        val pageNumber = 1
        
        val apiVersion = exampleSubscriptionWithFields("api1", 1)
        val subsData = Seq(
          apiVersion
        )

        val app = aStandardPendingApprovalApplication(developer.email)

        fetchByApplicationIdReturns(app)          
        givenApplicationHasSubs(app, subsData)
        
        val result : Result = executeAction()

        status(result) shouldBe BAD_REQUEST
      }

      "return a bad request for subscriptionConfigurationStart action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.subscriptionConfigurationStart(app.id)(loggedInRequest))
      }

      "return a bad request for subscriptionConfigurationPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.subscriptionConfigurationPage(app.id, pageNumber)(loggedInRequest))
      }

      "return a bad request for subscriptionConfigurationPagePost action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.subscriptionConfigurationPagePost(app.id, pageNumber)(loggedInRequest))
      }
      
      "return a bad request for subscriptionConfigurationStepPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.subscriptionConfigurationStepPage(app.id, pageNumber)(loggedInRequest))
      }
      
      "return a bad request for listApiSubscriptions action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.listApiSubscriptions(app.id)(loggedInRequest))
      }

      "return a bad request for editApiMetadataPage action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.editApiMetadataPage(app.id, apiVersion.context, apiVersion.apiVersion.version, SaveSubsFieldsPageMode.CheckYourAnswers)(loggedInRequest))
      }

      "return a bad request for saveSubscriptionFields action" in new PendingApprovalReturnsBadRequest {
        def executeAction = () => await(manageSubscriptionController.saveSubscriptionFields(app.id, apiVersion.context, apiVersion.apiVersion.version, SaveSubsFieldsPageMode.CheckYourAnswers)(loggedInRequest))
      }
    }
  
    "a user is logged in" when {

      "the subscriptions list action is called it" should {
        "return the list subscription configuration page with no subscriptions and therefore no subscription field definitions" in new ManageSubscriptionsSetup {
          givenApplicationHasNoSubs(application)

          private val result =
            await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions without subscription configuration" in new ManageSubscriptionsSetup {

          val subsData = Seq(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))

          givenApplicationHasSubs(application, subsData)

          private val result =
            await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

          status(result) shouldBe NOT_FOUND
        }

        "return the list subscription configuration page with several subscriptions, some with subscription configuration" in new ManageSubscriptionsSetup {

          val subsData = Seq(
            exampleSubscriptionWithFields("api1", 3),
            exampleSubscriptionWithFields("api2", 1),
            exampleSubscriptionWithoutFields("api3"),
            exampleSubscriptionWithFields("api4", 1).copy(subscribed = false)
          )

          givenApplicationHasSubs(application, subsData)

          private val result =
            await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

          status(result) shouldBe OK
          bodyOf(result) should include(loggedInUser.displayedName)
          bodyOf(result) should include("Sign out")
          bodyOf(result) should include(
            "Edit the configuration for these APIs you have subscribed to."
          )

          bodyOf(result) should include(generateName("api1"))
          bodyOf(result) should include("Stable")
          bodyOf(result) should include(generateValueName("api1", 1))
          bodyOf(result) should include(generateValueName("api1", 2))
          bodyOf(result) should include(generateValueName("api1", 3))
          bodyOf(result) should not include generateValueName("api1", 4)

          bodyOf(result) should include(generateName("api2"))
          bodyOf(result) should include(generateValueName("api2", 1))

          bodyOf(result) should not include generateName("api3")
          bodyOf(result) should not include generateName("api4")
        }

        "return not found if app has no subscription field definitions" in new ManageSubscriptionsSetup {
          givenApplicationHasSubs(application, Seq.empty)

          private val result =
            await(manageSubscriptionController.listApiSubscriptions(appId)(loggedInRequest))

          status(result) shouldBe NOT_FOUND
        }

        "It renders the subscription configuration list page for a privileged application" in new ManageSubscriptionsSetup {
          val subsData = Seq(
            exampleSubscriptionWithFields("api1", 1)
          )

          givenApplicationHasSubs(privilegedApplication, subsData)

          private val result =
            await(manageSubscriptionController.listApiSubscriptions(privilegedApplication.id)(loggedInRequest))

          status(result) shouldBe OK
        }
      }
      
      "the editApiMetadataPage is called it" should {
        "renders the edit subscription page" in new ManageSubscriptionsSetup {
          val whoCanWrite = NoOne
          val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

          val wrapper = buildSubscriptionFieldsWrapper(application, Seq(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))
          
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)

          private val readonlySubSubscriptionValue  = apiSubscriptionStatus.fields.fields(0)
          
          givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

          val subsData = Seq(apiSubscriptionStatus)

          givenApplicationHasSubs(application, subsData)

          private val result: Result =
            await(addToken(manageSubscriptionController.editApiMetadataPage(appId, "/api1-api", "1.0", SaveSubsFieldsPageMode.LeftHandNavigation))(loggedInRequest))

          assertCommonEditFormFields(result, apiSubscriptionStatus)

          bodyOf(result) should include(application.name)
          bodyOf(result) should include("Sandbox")
        }
      }
    
      "the page mode for saveSubscriptionFields action" when {
        "LeftHandNavigation" should {
          saveSubscriptionFieldsTest(SaveSubsFieldsPageMode.LeftHandNavigation, s"/developer/applications/$appId/api-metadata")
        }
        
        "CheckYourAnswers" should {
          saveSubscriptionFieldsTest(SaveSubsFieldsPageMode.CheckYourAnswers, s"/developer/applications/$appId/check-your-answers#configurations")
        }

        def saveSubscriptionFieldsTest(mode: SaveSubsFieldsPageMode, expectedRedirectUrl: String) = {
          s"save action saves valid subscription field values in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "new value"
            private val subSubscriptionValue  = apiSubscriptionStatus.fields.fields.head

            givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

            when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
              .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

            private val result: Result =
              await(addToken(manageSubscriptionController.saveSubscriptionFields(
                appId,
                apiSubscriptionStatus.context,
                apiSubscriptionStatus.apiVersion.version,
                mode))(loggedInWithFormValues))

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields = Map(subSubscriptionValue.definition.name -> newSubscriptionValue)

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.version),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields))(any[HeaderCarrier]())
          }

          s"save action saves valid subscription field values in mode [$mode] and with a read only field" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 2)

            private val readonlySubSubscriptionValue  = apiSubscriptionStatus.fields.fields(0)
            private val writableSubSubscriptionValue  = apiSubscriptionStatus.fields.fields(1)

            givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

            when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
              .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

            val newSubscriptionValue = "new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              writableSubSubscriptionValue.definition.name -> newSubscriptionValue
            )

            private val result: Result =
              await(addToken(manageSubscriptionController.saveSubscriptionFields(
                appId,
                apiSubscriptionStatus.context,
                apiSubscriptionStatus.apiVersion.version,
                mode))(loggedInWithFormValues))

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(expectedRedirectUrl)

            val expectedFields = Map(writableSubSubscriptionValue.definition.name -> newSubscriptionValue)

            verify(mockSubscriptionFieldsService)
              .saveFieldValues(
                eqTo(role),
                eqTo(application),
                eqTo(apiSubscriptionStatus.context),
                eqTo(apiSubscriptionStatus.apiVersion.version),
                eqTo(apiSubscriptionStatus.fields.fields),
                eqTo(expectedFields))(any[HeaderCarrier]())
          }

          s"save action saves valid subscription field values in mode [$mode] fails and with a read only field is passed in by a bad actor" in new ManageSubscriptionsSetup {
            val whoCanWrite = NoOne
            val accessDenied = AccessRequirements(devhub = DevhubAccessRequirements(Anyone, whoCanWrite))

            val wrapper = buildSubscriptionFieldsWrapper(application, Seq(buildSubscriptionFieldValue("field-name", Some("old-value"), accessDenied)))
            
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1).copy(fields = wrapper)

            private val readonlySubSubscriptionValue  = apiSubscriptionStatus.fields.fields(0)
            
            givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

            when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            val newSubscriptionValue = "illegal new value"

            private val loggedInWithFormValues = loggedInRequest.withFormUrlEncodedBody(
              readonlySubSubscriptionValue.definition.name -> newSubscriptionValue
            )

            private val result: Result =
              await(addToken(manageSubscriptionController.saveSubscriptionFields(
                appId,
                apiSubscriptionStatus.context,
                apiSubscriptionStatus.apiVersion.version,
                mode))(loggedInWithFormValues))

            status(result) shouldBe FORBIDDEN
          }
          
          s"save action fails validation and shows error message in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "my invalid value"
            val fieldErrors = Map("apiName" -> "apiName is invalid error message")

            givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

            when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
              .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

            private val subSubscriptionValue  = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

            private val result = await(addToken(manageSubscriptionController.saveSubscriptionFields(
                appId,
                apiSubscriptionStatus.context,
                apiSubscriptionStatus.apiVersion.version,
                mode))(loggedInWithFormValues))

            status(result) shouldBe BAD_REQUEST

            assertIsApiConfigureEditPage(result)

            bodyOf(result) should include("apiName is invalid error message")
          }

          s"save action fails with access deinied and shows error message in mode [$mode]" in new ManageSubscriptionsSetup {
            val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
            val newSubscriptionValue = "my invalid value"
            val fieldErrors = Map("apiName" -> "apiName is invalid error message")

            givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

            when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
              .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

            private val subSubscriptionValue  = apiSubscriptionStatus.fields.fields.head

            private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

            private val result = await(addToken(manageSubscriptionController.saveSubscriptionFields(
                appId,
                apiSubscriptionStatus.context,
                apiSubscriptionStatus.apiVersion.version,
                mode))(loggedInWithFormValues))

            status(result) shouldBe FORBIDDEN
          }

          s"return to the login page when the user attempts to edit subscription configuration in mode [$mode]" in new ManageSubscriptionsSetup {

            val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

            val fakeContext = "FAKE"
            val fakeVersion = "1.0"

            private val result =
              await(manageSubscriptionController.editApiMetadataPage(appId, fakeContext, fakeVersion, mode)(request))

            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/developer/login")
          }

          s"return not found when trying to edit api subscription configuration for an api the application is not subscribed to in mode [$mode]" in new ManageSubscriptionsSetup {
            givenApplicationHasSubs(application, Seq.empty)

            private val result = await(manageSubscriptionController.editApiMetadataPage(appId, apiContext, apiVersion, mode)(loggedInRequest))

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

          givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

          when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
            .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

          private val subSubscriptionValue  = apiSubscriptionStatus.fields.fields.head

          private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

          private val result = await(addToken(
            manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues))

          status(result) shouldBe BAD_REQUEST

          assertIsSandboxJourneyApiConfigureEditPage(result)

          bodyOf(result) should include("apiName is invalid error message")
        }

        "and fails with access denied and shows forbidden error message" in new ManageSubscriptionsSetup {
          val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
          val newSubscriptionValue = "my invalid value"
          val pageNumber = 1

          val fieldErrors = Map("apiName" -> "apiName is invalid error message")

          givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

          when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]()))
            .thenReturn(Future.successful(SaveSubscriptionFieldsAccessDeniedResponse))

          private val subSubscriptionValue  = apiSubscriptionStatus.fields.fields.head

          private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

          private val result = await(addToken(
            manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues))

          status(result) shouldBe FORBIDDEN
        }
      }
    }

    "a user is doing the add new sandbox app journey they" should {
      "be able to view the subscription fields start page if they have subscribed to APIs with subscription fields" in new ManageSubscriptionsSetup {
        val subsData = Seq(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest))

        status(result) shouldBe OK
        bodyOf(result) should include("api1-name")
        bodyOf(result) should include("api2-name")
        bodyOf(result) should include("1.0")
        bodyOf(result) should include("Stable")
      }

      "edit page" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
        val subsData = Seq(apiSubscriptionStatus)

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest))

        assertCommonEditFormFields(result, apiSubscriptionStatus)
      }

      "return NOT_FOUND if page has no field definitions" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithoutFields("api1")
        val subsData = Seq(apiSubscriptionStatus)

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(addToken(manageSubscriptionController.subscriptionConfigurationPage(appId, 1))(loggedInRequest))

        status(result) shouldBe NOT_FOUND
      }

      "return NOT_FOUND if page number is invalid for edit page " when {
        def testEditPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
          import manageSubscriptionsSetup._

          val subsData = Seq(
            exampleSubscriptionWithFields("api1", count)
          )

          givenApplicationHasSubs(application, subsData)
          val result =
            await(manageSubscriptionController.subscriptionConfigurationPage(appId, -1)(loggedInRequest))

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
        val subsData = Seq(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStepPage(appId, 1)(loggedInRequest))

        status(result) shouldBe OK
        bodyOf(result) should include("You have completed step 1 of 2")
      }

      "step page for the last page as a redirect for sandbox" in new ManageSubscriptionsSetup {
        val subsData = Seq(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStepPage(appId, 2)(loggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${application.id}/add/success")
      }

      "step page for the last page as a redirect for production" in new ManageSubscriptionsSetup {
        val subsData = Seq(
          exampleSubscriptionWithFields("api1", 1),
          exampleSubscriptionWithFields("api2", 1)
        )

        givenApplicationHasSubs(productionApplication, subsData)

        givenUpdateCheckInformationSucceeds(productionApplication)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStepPage(productionApplication.id, 2)(loggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${productionApplication.id}/request-check")

        verify(applicationServiceMock).updateCheckInformation(eqTo(productionApplication),eqTo(CheckInformation(apiSubscriptionConfigurationsConfirmed = true)))(any[HeaderCarrier])
    }

    "return NOT_FOUND if page number is invalid for step page " when {
      def testStepPageNumbers(count: Int, manageSubscriptionsSetup: ManageSubscriptionsSetup) = {
        import manageSubscriptionsSetup._

        val subsData = Seq(
          exampleSubscriptionWithFields("api1", count)
        )

        givenApplicationHasSubs(application, subsData)

        val result = await(manageSubscriptionController.subscriptionConfigurationStepPage(appId, -1)(loggedInRequest))

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
        val subsData = Seq(exampleSubscriptionWithoutFields("api1"))

        givenApplicationHasSubs(application, subsData)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStart(appId)(loggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/add/success")
      }
    }

    "when the user is not logged in" should {
      "return to the login page when the user attempts to list subscription configuration" in new ManageSubscriptionsSetup {

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

        private val result =
          await(manageSubscriptionController.listApiSubscriptions(appId)(request))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }
  }

  private def aClientSecret() =
    ClientSecret(
      randomUUID.toString,
      randomUUID.toString,
      DateTimeUtils.now.withZone(DateTimeZone.getDefault)
    )
}
