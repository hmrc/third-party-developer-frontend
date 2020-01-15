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

import config.ApplicationConfig
import controllers.{APISubscriptions, ApiSubscriptionsHelper, ApplicationCheck, GroupedSubscriptions}
import domain.Role._
import domain._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.{never, verify}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.Assertion
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class ApplicationCheckSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val appName: String = "app"
  val clientId = "clientIdzzz"
  val sessionId = "sessionId"

  val developerDto = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(sessionId, developerDto, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val testing: ApplicationState = ApplicationState.testing.copy(updatedOn = DateTimeUtils.now.minusMinutes(1))
  val production: ApplicationState = ApplicationState.production("thirdpartydeveloper@example.com", "ABCD")
  val pendingApproval: ApplicationState = ApplicationState.pendingGatekeeperApproval("thirdpartydeveloper@example.com")
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationTokens(EnvironmentToken(
    "clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  val exampleApiSubscription = Some(APISubscriptions("Example API", "api-example-microservice", "exampleContext",
    Seq(APISubscriptionStatus("API1", "api-example-microservice", "exampleContext",
      APIVersion("version", APIStatus.STABLE), subscribed = true, requiresTrust = false))))

  val groupedSubs = GroupedSubscriptions(Seq.empty,
    Seq(APISubscriptions("API1", "ServiceName", "apiContent",
      Seq(APISubscriptionStatus(
        "API1", "subscriptionServiceName", "context", APIVersion("version", APIStatus.STABLE), subscribed = true, requiresTrust = false)))))

  val groupedSubsSubscribedToExampleOnly = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = exampleApiSubscription)

  val groupedSubsSubscribedToNothing = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = None)

  trait Setup {
    val underTest = new ApplicationCheck(
      mock[ApplicationService],
      mock[ApiSubscriptionsHelper],
      mock[SessionService],
      mockErrorHandler,
      messagesApi,
      mock[ApplicationConfig]
    )

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .willReturn(successful(ApplicationUpdateSuccessful))

    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier]))
      .willReturn(successful(application))

    given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier]))
      .willReturn(tokens)

    given(underTest.applicationService.removeTeamMember(any[Application], any[String], mockEq(loggedInUser.email))(any[HeaderCarrier]))
      .willReturn(ApplicationUpdateSuccessful)

    given(underTest.applicationService.updateCheckInformation(mockEq(appId), any[CheckInformation])(any[HeaderCarrier]))
      .willReturn(ApplicationUpdateSuccessful)

    given(underTest.apiSubscriptionsHelper.fetchAllSubscriptions(any[Application], any[DeveloperSession])(any[HeaderCarrier]))
      .willReturn(successful(Some(SubscriptionData(Role.ADMINISTRATOR, application, Some(groupedSubs)))))

    given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
      .willReturn(Future.successful(Valid))

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest,implicitly)(sessionId).withSession(sessionParams: _*)

    val defaultCheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

    def givenTheApplicationExists(appId: String = appId, clientId: String = clientId, userRole: Role = ADMINISTRATOR,
                                  state: ApplicationState = testing,
                                  checkInformation: Option[CheckInformation] = None,
                                  access: Access = Standard()): Application = {

      val application = Application(appId, clientId, appName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), access = access, state = state, checkInformation = checkInformation)

      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens)
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq())

      application
    }

    def idAttributeOnCheckedInput(result: Result): String = Jsoup.parse(bodyOf(result)).select("input[checked]").attr("id")
  }

  trait SubscriptionValidationSetup extends Setup {

    def testSubscriptionValidation: Assertion = {

      givenTheApplicationExists()

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      val result = await(addToken(underTest.apiSubscriptionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(mockEq(appId), any())(any[HeaderCarrier])

      val errorMessageElement = Jsoup.parse(bodyOf(result)).select("ul.error-summary-list")
      errorMessageElement.text() shouldBe "You must subscribe to at least one API, in addition to optionally subscribing to Hello World"
    }
  }

  "landing page" should {
    "return landing page" in new Setup {

      givenTheApplicationExists()

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Submit your application for check")
      body should include("Cancel")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "show all steps as required when no check information exists" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show app name step as complete when it has been done" in new Setup {
      givenTheApplicationExists(checkInformation = Some(CheckInformation(confirmedName = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepCompleteIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show app details step as complete when it has been done" in new Setup {
      givenTheApplicationExists(checkInformation = Some(CheckInformation(applicationDetails = Some("blah blah"))))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepCompleteIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show api subscription step as complete when it has been done" in new Setup {
      givenTheApplicationExists(checkInformation = Some(CheckInformation(apiSubscriptionsConfirmed = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepCompleteIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show contact details step as complete when it has been done" in new Setup {
      givenTheApplicationExists(checkInformation =
        Some(CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepCompleteIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show privacy policy and terms and conditions step as complete when it has been done" in new Setup {
      givenTheApplicationExists(
        checkInformation = Some(CheckInformation(providedPrivacyPolicyURL = true, providedTermsAndConditionsURL = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepCompleteIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show agree to terms of use step as complete when it has been done" in new Setup {
      givenTheApplicationExists(
        checkInformation = Some(CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("app-details-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepCompleteIndication("agree-terms-status"))
    }

    "successful submit action" in new Setup {
      givenTheApplicationExists(checkInformation =
        Some(CheckInformation(
          confirmedName = true,
          Some("Details"),
          apiSubscriptionsConfirmed = true,
          Some(ContactDetails("Example Name", "name@example.com", "012346789")),
          providedPrivacyPolicyURL = true,
          providedTermsAndConditionsURL = true,
          Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      given(underTest.applicationService.requestUplift(mockEq(appId), any[String], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(ApplicationUpliftSuccessful)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe OK
    }

    "validation failure submit action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "validate failure when application name already exists" in new Setup {
      private val application = givenTheApplicationExists(checkInformation =
        Some(CheckInformation(
          confirmedName = true,
          Some("Details"),
          apiSubscriptionsConfirmed = true,
          Some(ContactDetails("Example Name", "name@example.com", "012346789")),
          providedPrivacyPolicyURL = true,
          providedTermsAndConditionsURL = true,
          Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      val expectedCheckInformation: CheckInformation = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)

      given(underTest.applicationService.requestUplift(mockEq(appId), mockEq(application.name), mockEq(loggedInUser))(any[HeaderCarrier]))
        .willAnswer(new Answer[Future[ApplicationUpliftSuccessful]]() {
          def answer(invocation: InvocationOnMock): Future[ApplicationUpliftSuccessful] = {
            Future.failed(new ApplicationAlreadyExists)
          }
        })
      given(underTest.applicationService.updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier]))
        .willReturn(ApplicationUpdateSuccessful)

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe CONFLICT
      verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier])

      private val errorMessageElement = Jsoup.parse(bodyOf(result)).select("td#confirmedName span.error-message")
      errorMessageElement.text() shouldBe "That application name already exists. Enter a unique name for your application."
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.requestCheckAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "api subscriptions review" should {
    "return page" in new Setup {
      givenTheApplicationExists()
      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Confirm the APIs your application uses")
      bodyOf(result) should include("subscriptionServiceName")
    }

    "success action" in new Setup {
      givenTheApplicationExists()
      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequest.withFormUrlEncodedBody()))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return 404 NOT FOUND when no API subscriptions are retrieved" in new Setup {
      given(underTest.apiSubscriptionsHelper.fetchAllSubscriptions(any[Application], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(successful(None))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()
      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when no APIs have been subscribed to" in new SubscriptionValidationSetup {
      given(underTest.apiSubscriptionsHelper.fetchAllSubscriptions(any[Application], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(successful(Some(SubscriptionData(Role.ADMINISTRATOR, application, Some(groupedSubsSubscribedToNothing)))))

      testSubscriptionValidation
    }

    "fail validation when only the example API has been subscribed to" in new SubscriptionValidationSetup {
      given(underTest.apiSubscriptionsHelper.fetchAllSubscriptions(any[Application], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(successful(Some(SubscriptionData(Role.ADMINISTRATOR, application, Some(groupedSubsSubscribedToExampleOnly)))))

      testSubscriptionValidation
    }
  }

  "contact review" should {
    "return page" in new Setup {

      givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Who is your application review contact?")
      bodyOf(result) should include("tester@example.com")
    }

    "successful contact action" in new Setup {
      givenTheApplicationExists()

      private val requestWithFormBody = loggedInRequest
        .withFormUrlEncodedBody("email" -> "email@example.com", "telephone" -> "0000", "fullname" -> "john smith")

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure contact action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.contactPage(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "details review" should {
    "return page" in new Setup {

      givenTheApplicationExists()
      private val result = await(addToken(underTest.detailsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("What does your application do?")
    }

    "successful details action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationDetails" -> "Some Details about my tax app")

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure details missing action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "allow details to be up to 3000 characters" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationDetails" -> "S" * 3000)

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when details longer than 3001 characters" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationDetails" -> "S" * 3001)

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.detailsPage(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.detailsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.detailsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.detailsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "name review" should {
    "return page" in new Setup {

      givenTheApplicationExists()

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Confirm your application's name")
    }

    "successful name action different names" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "My First Tax App")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))
      val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, "My First Tax App", appUnderTest.description, appUnderTest.access)
      verify(underTest.applicationService).update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successful name action same names" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "app")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))
      val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, appUnderTest.access)
      verify(underTest.applicationService, never()).update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure name is blank action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "Validation failure name contains HMRC action" in new Setup {
      givenTheApplicationExists()

      given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Invalid.invalidName))

      private val applicationName = "Blacklisted HMRC"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST

      bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

      verify(underTest.applicationService)
        .isApplicationNameValid(mockEq(applicationName), mockEq(Environment.PRODUCTION), mockEq(Some(appId)))(any[HeaderCarrier])
    }

    "Validation failure when duplicate name" in new Setup {
      givenTheApplicationExists()

      given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Invalid.duplicateName))

      private val applicationName = "Duplicate Name"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST

      bodyOf(result) should include("That application name already exists. Enter a unique name for your application.")

      verify(underTest.applicationService)
        .isApplicationNameValid(mockEq(applicationName), mockEq(Environment.PRODUCTION), mockEq(Some(appId)))(any[HeaderCarrier])
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.namePage(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "privacy policy review" should {
    "return page" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Where can we find your privacy policy?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = false)
      private val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      givenTheApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      private val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      givenTheApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      givenTheApplicationExists(checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "http://privacypolicy.example.com")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      val standardAccess = Standard(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService).update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }
    "successfully process when no URL" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      val standardAccess = Standard(privacyPolicyUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService)
        .update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when privacy policy url is invalid" in new Setup {
      givenTheApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "invalid url")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when privacy policy url is missing" in new Setup {
      givenTheApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms and conditions review" should {
    "return page" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Where can we find your terms and conditions?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = false)
      private val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      givenTheApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      private val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      givenTheApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      givenTheApplicationExists(checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls =
        loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "http://termsAndConditionsURL.example.com")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      val standardAccess = Standard(termsAndConditionsUrl = Some("http://termsAndConditionsURL.example.com"))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService).update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successfully process when doesn't have url" in new Setup {
      private val appUnderTest = givenTheApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      val standardAccess = Standard(termsAndConditionsUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService).update(mockEq(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(mockEq(appId), mockEq(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when terms and conditions url is invalid but hasUrl true" in new Setup {
      givenTheApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "invalid url")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when terms and conditions url is missing but hasUrl true" in new Setup {
      givenTheApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "action unavailable when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "page unavailable when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "redirect to the application credentials tab when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms of use review" should {
    "return page" in new Setup {

      givenTheApplicationExists()
      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Agree to our terms of use")
    }

    "be forbidden when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "action is forbidden when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsOfUseAction(appId))(requestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsOfUseAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

      private val result = await(addToken(underTest.termsOfUseAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "successful terms of use action" in new Setup {
      givenTheApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")

      private val result = await(addToken(underTest.termsOfUseAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")

    }

  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def stepRequiredIndication(id: String) = {
    s"""<div id="$id" class="step-status status-incomplete">Required</div>"""
  }

  private def stepCompleteIndication(id: String) = {
    s"""<div id="$id" class="step-status status-completed">Complete</div>"""
  }
}
