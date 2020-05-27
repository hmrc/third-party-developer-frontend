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

import controllers.checkpages.ApplicationCheck
import domain._
import domain.Role._
import helpers.string._
import mocks.service._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future

class ApplicationCheckSpec extends BaseControllerSpec with WithCSRFAddToken with SubscriptionTestHelperSugar {

  val appId = "1234"
  val appName: String = "app"
  val clientId = "clientIdzzz"
  val sessionId = "sessionId"

  val developerDto: Developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session: Session = Session(sessionId, developerDto, LoggedInState.LOGGED_IN)
  val anotherCollaboratorEmail = "collaborator@example.com"
  val yetAnotherCollaboratorEmail = "collaborator2@example.com"

  val loggedInUser: DeveloperSession = DeveloperSession(session)

  val testing: ApplicationState = ApplicationState.testing.copy(updatedOn = DateTimeUtils.now.minusMinutes(1))
  val production: ApplicationState = ApplicationState.production("thirdpartydeveloper@example.com", "ABCD")
  val pendingApproval: ApplicationState = ApplicationState.pendingGatekeeperApproval("thirdpartydeveloper@example.com")
  val application: Application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens: ApplicationToken = ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")
  val exampleApiSubscription: Some[APISubscriptions] = Some(APISubscriptions("Example API", "api-example-microservice", "exampleContext",
    Seq(APISubscriptionStatus("API1", "api-example-microservice", "exampleContext",
      APIVersion("version", APIStatus.STABLE), subscribed = true, requiresTrust = false))))

  val groupedSubsSubscribedToExampleOnly: GroupedSubscriptions = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = exampleApiSubscription)

  val groupedSubsSubscribedToNothing: GroupedSubscriptions = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = None)

  trait Setup extends ApplicationServiceMock {
    val underTest = new ApplicationCheck(
      applicationServiceMock,
      mock[SessionService],
      mockErrorHandler,
      messagesApi,
      cookieSigner
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    givenApplicationUpdateSucceeds()

    fetchByApplicationIdReturns(appId, application)

    fetchCredentialsReturns(application, tokens)

    givenRemoveTeamMemberSucceeds(loggedInUser)

    givenUpdateCheckInformationReturns(appId)

    givenApplicationNameIsValid()

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest,implicitly)(sessionId).withSession(sessionParams: _*)
    val loggedInRequestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val defaultCheckInformation: CheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

    def givenApplicationExists(
        appId: String = appId,
        clientId: String = clientId,
        userRole: Role = ADMINISTRATOR,
        state: ApplicationState = testing,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Standard(),
        hasSubs: Boolean = false): Application = {

      // this is to ensure we always have one ADMINISTRATOR
      val anotherRole = if(userRole.isAdministrator) DEVELOPER else ADMINISTRATOR

      val collaborators = Set(
        Collaborator(loggedInUser.email, userRole),
        Collaborator(anotherCollaboratorEmail, anotherRole)
      )

      givenApplicationExistsWithCollaborators(collaborators, appId, clientId, state, checkInformation, access, hasSubs)
    }


    def givenApplicationExistsWithCollaborators(
        collaborators: Set[Collaborator],
        appId: String = appId,
        clientId: String = clientId,
        state: ApplicationState = testing,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Standard(),
        hasSubs: Boolean = false ): Application = {

      val application = Application(appId, clientId, appName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION,
         collaborators = collaborators, access = access, state = state, checkInformation = checkInformation)

      fetchByApplicationIdReturns(application.id, application)
      fetchCredentialsReturns(application.id, tokens)
      if(hasSubs)
        givenApplicationHasSubs(application, sampleSubscriptionsWithSubscriptionConfiguration(application))
      else
        givenApplicationHasNoSubs(application)

      application
    }

    def idAttributeOnCheckedInput(result: Result): String = Jsoup.parse(bodyOf(result)).select("input[checked]").attr("id")
  }

  "check request submitted" should {
    "return credentials requested page" in new Setup{
      givenApplicationExists()
      private val result = await(underTest.credentialsRequested(appId)(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Request received")
      body should include("We've sent you a confirmation email")
      body should include("What happens next?")
      body should include("We may ask for a demonstration of your software.")
      body should include("The checking process can take up to 10 working days.")
      body should include("By requesting credentials you've created a new production application")
    }
    "return forbidden when not logged in" in new Setup {
      givenApplicationExists()
      private val result = await(underTest.credentialsRequested(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }
  }

  "landing page" should {
    "return landing page" in new Setup {

      givenApplicationExists()

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Get production credentials")
      body should include("Save and come back later")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "show all steps as required when no check information exists" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show app name step as complete when it has been done" in new Setup {
      givenApplicationExists(checkInformation = Some(CheckInformation(confirmedName = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepCompleteIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show api subscription step as complete when it has been done" in new Setup {
      givenApplicationExists(checkInformation = Some(CheckInformation(apiSubscriptionsConfirmed = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepCompleteIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show contact details step as complete when it has been done" in new Setup {
      givenApplicationExists(checkInformation =
        Some(CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepCompleteIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show privacy policy and terms and conditions step as complete when it has been done" in new Setup {
      givenApplicationExists(
        checkInformation = Some(CheckInformation(providedPrivacyPolicyURL = true, providedTermsAndConditionsURL = true)))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepCompleteIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show agree to terms of use step as complete when it has been done" in new Setup {
      givenApplicationExists(
        checkInformation = Some(CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepCompleteIndication("agree-terms-status"))
    }

    "show api subscription configuration step as complete when it has been done" in new Setup {
      givenApplicationExists(
        checkInformation = Some(CheckInformation(apiSubscriptionConfigurationsConfirmed = true)),
        hasSubs = true)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      private val body = bodyOf(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
      body should include(stepCompleteIndication("api-subscription-configurations-status"))
    }

    "successful submit action should take you to the check-your-answers page" in new Setup {
      givenApplicationExists(checkInformation =
        Some(CheckInformation(
          confirmedName = true,
          apiSubscriptionsConfirmed = true,
          apiSubscriptionConfigurationsConfirmed = true,
          Some(ContactDetails("Example Name", "name@example.com", "012346789")),
          providedPrivacyPolicyURL = true,
          providedTermsAndConditionsURL = true,
          teamConfirmed = true,
          Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      given(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(ApplicationUpliftSuccessful)

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")
    }

    "successful submit action should take you to the check-your-answers page when no configurations confirmed because none required" in new Setup {
      givenApplicationExists(checkInformation =
        Some(CheckInformation(
          confirmedName = true,
          apiSubscriptionsConfirmed = true,
          apiSubscriptionConfigurationsConfirmed = false,
          Some(ContactDetails("Example Name", "name@example.com", "012346789")),
          providedPrivacyPolicyURL = true,
          providedTermsAndConditionsURL = true,
          teamConfirmed = true,
          Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0")))))

      given(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(any[HeaderCarrier]))
        .willReturn(ApplicationUpliftSuccessful)

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")
    }

    "validation failure submit action" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.requestCheckPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "api subscriptions review" should {
    "return page" in new Setup {
      val application = givenApplicationExists()
      val subsData = Seq(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))
      givenApplicationHasSubs(application, subsData)

      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Confirm which APIs you want to use")
      bodyOf(result) should include(generateName("api1"))
    }

    "success action" in new Setup {
      val application = givenApplicationExists()
      val subsData = Seq(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))
      givenApplicationHasSubs(application, subsData)

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return 404 NOT FOUND when no API subscriptions are retrieved" in new Setup {
      givenApplicationHasNoSubs(application)

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return 404 NOT FOUND when only API-EXAMPLE-MICROSERVICE API is subscribed to" in new Setup {
      givenApplicationHasSubs(application, Seq(onlyApiExampleMicroserviceSubscribedTo) )

      private val result = await(addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "contact review" should {
    "return page" in new Setup {

      givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Who to contact about your application")
      bodyOf(result) should include("tester@example.com")
    }

    "successful contact action" in new Setup {
      givenApplicationExists()

      private val requestWithFormBody = loggedInRequest
        .withFormUrlEncodedBody("email" -> "email@example.com", "telephone" -> "0000", "fullname" -> "john smith")

      private val result = await(addToken(underTest.contactAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure contact action" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.contactPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "name review" should {
    "return page" in new Setup {

      givenApplicationExists()

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Confirm the name of your application")
    }

    "successful name action different names" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "My First Tax App")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, "My First Tax App", appUnderTest.description, appUnderTest.access)
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService).updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successful name action same names" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "app")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, appUnderTest.access)
      verify(underTest.applicationService, never()).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService).updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure name is blank action" in new Setup {
      givenApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "")

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "Validation failure name contains HMRC action" in new Setup {
      givenApplicationExists()

      given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Invalid.invalidName))

      private val applicationName = "Blacklisted HMRC"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST

      bodyOf(result) should include("Application name must not include HMRC or HM Revenue and Customs")

      verify(underTest.applicationService)
        .isApplicationNameValid(eqTo(applicationName), eqTo(Environment.PRODUCTION), eqTo(Some(appId)))(any[HeaderCarrier])
    }

    "Validation failure when duplicate name" in new Setup {
      givenApplicationExists()

      given(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(Invalid.duplicateName))

      private val applicationName = "Duplicate Name"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = await(addToken(underTest.nameAction(appId))(requestWithFormBody))

      status(result) shouldBe BAD_REQUEST

      bodyOf(result) should include("That application name already exists. Enter a unique name for your application.")

      verify(underTest.applicationService)
        .isApplicationNameValid(eqTo(applicationName), eqTo(Environment.PRODUCTION), eqTo(Some(appId)))(any[HeaderCarrier])
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.namePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "privacy policy review" should {
    "return page" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Does your application have a privacy policy?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = false)
      private val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      givenApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      private val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      givenApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      givenApplicationExists(checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "http://privacypolicy.example.com")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      private val standardAccess = Standard(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)

      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }
    "successfully process when no URL" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      private val standardAccess: Standard = Standard(privacyPolicyUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService)
        .update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when privacy policy url is invalid" in new Setup {
      givenApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "invalid url")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when privacy policy url is missing" in new Setup {
      givenApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.privacyPolicyPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms and conditions review" should {
    "return page" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Does your application have terms and conditions?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = false)
      private val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      givenApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      private val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      givenApplicationExists(access = access, checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      private val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      givenApplicationExists(checkInformation = Some(checkInformation))

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls =
        loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "http://termsAndConditionsURL.example.com")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      private val standardAccess = Standard(termsAndConditionsUrl = Some("http://termsAndConditionsURL.example.com"))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successfully process when doesn't have url" in new Setup {
      private val appUnderTest = givenApplicationExists(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      private val standardAccess = Standard(termsAndConditionsUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(appUnderTest.id, appUnderTest.deployedTo, appUnderTest.name, appUnderTest.description, standardAccess)
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(appId), eqTo(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when terms and conditions url is invalid but hasUrl true" in new Setup {
      givenApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "invalid url")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when terms and conditions url is missing but hasUrl true" in new Setup {
      givenApplicationExists()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls))
      status(result) shouldBe BAD_REQUEST
    }

    "action unavailable when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "page unavailable when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "redirect to the application credentials tab when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms of use review" should {
    "return page" in new Setup {

      givenApplicationExists()
      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Agree to our terms of use")
    }

    "be forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "action is forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsOfUsePage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "successful terms of use action" in new Setup {
      givenApplicationExists()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")

      private val result = await(addToken(underTest.termsOfUseAction(appId))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")

    }
  }

  "Manage teams checks" should {
    "return manage team list page when check page is navigated to" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.team(appId))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Add members of your organisation and give them permissions to access this application")
      bodyOf(result) should include(developerDto.email)
    }

    "not return the manage team list page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.team(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "team post redirect to check landing page" in new Setup {
      givenApplicationExists(checkInformation = Some(CheckInformation()))

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(appId), eqTo(expectedCheckInformation))(any[HeaderCarrier])
    }

    "team post doesn't redirect to the check landing page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.teamAction(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }


    "return add team member page when check page is navigated to" in new Setup{
      givenApplicationExists()

      private val result = await(addToken(underTest.teamAddMember(appId))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Add a team member")
    }

    "not return the add team member page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.teamAddMember(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    val hashedAnotherCollaboratorEmail: String = anotherCollaboratorEmail.toSha256

    "return remove team member confirmation page when navigated to" in new Setup{
      givenApplicationExists()

      private val result = await(addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Are you sure you want to remove this team member from your application?")

      bodyOf(result) should include(anotherCollaboratorEmail)
    }

    "not return the remove team member confirmation page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = await(addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }


    "redirect to the team member list when the remove confirmation post is executed" in new Setup{
      val app = givenApplicationExists()

      val request = loggedInRequest.withFormUrlEncodedBody("email" -> anotherCollaboratorEmail)

      private val result = await(addToken(underTest.teamMemberRemoveAction(appId))(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check/team")

      verify(underTest.applicationService).removeTeamMember(eqTo(app),eqTo(anotherCollaboratorEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
    }

    "team post redirect to check landing page when no check information on application" in new Setup {
      givenApplicationExists(checkInformation = None)

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(appId), eqTo(expectedCheckInformation))(any[HeaderCarrier])
    }
  }

  "unauthorised App details" should {
    "redirect to landing page when Admin" in new Setup {

      givenApplicationExists()

      private val result = await(addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/request-check")
    }
    "return unauthorised App details page with one Admin" in new Setup {

      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Production application")
      body should include("You cannot view this application because you're not an administrator.")
      body should include("Ask the administrator")
      body should include(anotherCollaboratorEmail)
    }
    "return unauthorised App details page with 2 Admins " in new Setup {

      val collaborators = Set(
        Collaborator(loggedInUser.email, DEVELOPER),
        Collaborator(anotherCollaboratorEmail, ADMINISTRATOR),
        Collaborator(yetAnotherCollaboratorEmail, ADMINISTRATOR)
      )

      givenApplicationExistsWithCollaborators(collaborators = collaborators)

      private val result = await(addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Production application")
      body should include("You cannot view this application because you're not an administrator.")
      body should include("Ask an administrator")
      body should include(anotherCollaboratorEmail)
      body should include(yetAnotherCollaboratorEmail)
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def stepRequiredIndication(id: String) = {
    s"""<div id="$id" class="step-status status-incomplete">To do</div>"""
  }

  private def stepCompleteIndication(id: String) = {
    s"""<div id="$id" class="step-status status-completed">Complete</div>"""
  }
}
