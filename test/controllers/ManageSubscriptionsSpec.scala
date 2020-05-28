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
import domain._
import domain.ApiSubscriptionFields._
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

  val application: Application = Application(
    appId,
    clientId,
    "App name 1",
    DateTimeUtils.now,
    DateTimeUtils.now,
    Environment.SANDBOX,
    Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
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

    val manageSubscriptionController = new ManageSubscriptions(
      sessionServiceMock,
      mockAuditService,
      applicationServiceMock,
      mockErrorHandler,
      messagesApi,
      mockSubscriptionFieldsService,
      cookieSigner
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
        .withFormUrlEncodedBody("apiName" -> "",
          "displayedStatus" -> "",
          "fields[0].name" -> fieldName,
          "fields[0].description" -> "",
          "fields[0].shortDescription" -> "",
          "fields[0].hint" -> "",
          "fields[0].type" -> "",
          "fields[0].value" -> fieldValue)
    }

    def assertCommonEditFormFields(result: Result, apiSubscriptionStatus: APISubscriptionStatus): Unit = {
      status(result) shouldBe OK

      bodyOf(result) should include(apiSubscriptionStatus.name)
      bodyOf(result) should include(apiSubscriptionStatus.apiVersion.version)

      val fields = apiSubscriptionStatus.fields.head.fields.toList

      for(field <- fields){
        bodyOf(result) should include(field.definition.description)
        bodyOf(result) should include(field.definition.hint)
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
    "a user is logged in" should {
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
          "Edit the configuration for the APIs you have subscribed to."
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

      "return not found when trying to edit api subscription configuration for an api the application is not subscribed to" in new ManageSubscriptionsSetup {
        givenApplicationHasSubs(application, Seq.empty)

        private val result = await(manageSubscriptionController.editApiMetadataPage(appId, apiContext, apiVersion, SaveSubsFieldsPageMode.LeftHandNavigation)(loggedInRequest))

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

      "renders the edit subscription page" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 2)

        val subsData = Seq(apiSubscriptionStatus)

        givenApplicationHasSubs(application, subsData)

        private val result: Result =
          await(addToken(manageSubscriptionController.editApiMetadataPage(appId, "/api1-api", "1.0", SaveSubsFieldsPageMode.LeftHandNavigation))(loggedInRequest))

        assertCommonEditFormFields(result, apiSubscriptionStatus)

        bodyOf(result) should include(application.name)
        bodyOf(result) should include("Sandbox")

      }

      "save action saves valid subscription field values" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
        val newSubscriptionValue = "new value"
        private val subSubscriptionValue  = apiSubscriptionStatus.fields.head.fields.head

        givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

        when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier]()))
          .thenReturn(Future.successful(SaveSubscriptionFieldsSuccessResponse))

        private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

        private val result: Result =
          await(addToken(manageSubscriptionController.saveSubscriptionFields(
            appId,
            apiSubscriptionStatus.context,
            apiSubscriptionStatus.apiVersion.version,
            SaveSubsFieldsPageMode.LeftHandNavigation))(loggedInWithFormValues))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/api-metadata")

        val expectedFields: Fields = Map(subSubscriptionValue.definition.name -> newSubscriptionValue)

        verify(mockSubscriptionFieldsService)
          .saveFieldValues(
            eqTo(appId),
            eqTo(apiSubscriptionStatus.context),
            eqTo(apiSubscriptionStatus.apiVersion.version),
            eqTo(expectedFields))(any[HeaderCarrier]())
      }

      "save action fails validation and shows error message" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
        val newSubscriptionValue = "my invalid value"
        val fieldErrors = Map("apiName" -> "apiName is invalid error message")

        givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

        when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier]()))
          .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

        private val subSubscriptionValue  = apiSubscriptionStatus.fields.head.fields.head

        private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

        private val result = await(addToken(manageSubscriptionController.saveSubscriptionFields(
            appId,
            apiSubscriptionStatus.context,
            apiSubscriptionStatus.apiVersion.version,
            SaveSubsFieldsPageMode.LeftHandNavigation))(loggedInWithFormValues))

        status(result) shouldBe BAD_REQUEST

        assertIsApiConfigureEditPage(result)

        bodyOf(result) should include("apiName is invalid error message")
      }

      "save action fails valid and shows error message and renders the add app journey page subs configuration" in new ManageSubscriptionsSetup {
        val apiSubscriptionStatus: APISubscriptionStatus = exampleSubscriptionWithFields("api1", 1)
        val newSubscriptionValue = "my invalid value"
        val pageNumber = 1

        val fieldErrors = Map("apiName" -> "apiName is invalid error message")

        givenApplicationHasSubs(application, Seq(apiSubscriptionStatus))

        when(mockSubscriptionFieldsService.saveFieldValues(any(), any(), any(), any())(any[HeaderCarrier]()))
          .thenReturn(Future.successful(SaveSubscriptionFieldsFailureResponse(fieldErrors)))

        private val subSubscriptionValue  = apiSubscriptionStatus.fields.head.fields.head

        private val loggedInWithFormValues = editFormPostRequest(subSubscriptionValue.definition.name,newSubscriptionValue)

        private val result = await(addToken(
          manageSubscriptionController.subscriptionConfigurationPagePost(appId, pageNumber))(loggedInWithFormValues))

        status(result) shouldBe BAD_REQUEST

        assertIsSandboxJourneyApiConfigureEditPage(result)

        bodyOf(result) should include("apiName is invalid error message")
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

        givenUpdateCheckInformationReturns(productionApplication.id)

        private val result =
          await(manageSubscriptionController.subscriptionConfigurationStepPage(productionApplication.id, 2)(loggedInRequest))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/applications/${productionApplication.id}/request-check")

        verify(applicationServiceMock).updateCheckInformation(eqTo(productionApplication.id),eqTo(CheckInformation(apiSubscriptionConfigurationsConfirmed = true)))(any[HeaderCarrier])
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

      "return to the login page when the user attempts to edit subscription configuration" in new ManageSubscriptionsSetup {

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

        val fakeContext = "FAKE"
        val fakeVersion = "1.0"

        private val result =
          await(manageSubscriptionController.editApiMetadataPage(appId, fakeContext, fakeVersion, SaveSubsFieldsPageMode.LeftHandNavigation)(request))

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
