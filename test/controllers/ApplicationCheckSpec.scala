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

import builder._
import controllers.checkpages.ApplicationCheck
import domain.models.apidefinitions.{APIStatus, APISubscriptionStatus, ApiVersionDefinition}
import domain.models.applications.ApplicationId
import domain.models.applications.Role.{ADMINISTRATOR, DEVELOPER}
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.ApplicationUpliftSuccessful
import domain.models.applications._
import helpers.string._
import mocks.service._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.checkpages._
import views.html.checkpages.applicationcheck.{LandingPageView, UnauthorisedAppDetailsView}
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView, TeamView}
import views.html.editapplication.NameSubmittedView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.test.Helpers._

import scala.concurrent.Future.successful
import domain.models.apidefinitions.{ApiContext, ApiVersion}

class ApplicationCheckSpec extends BaseControllerSpec with WithCSRFAddToken with SubscriptionTestHelperSugar with SubscriptionsBuilder {

  override val appId = ApplicationId("1234")

  val appName: String = "app"
  val sessionId = "sessionId"

  val exampleContext = ApiContext("exampleContext")
  val version = ApiVersion("version")

  val developerDto: Developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session: Session = Session(sessionId, developerDto, LoggedInState.LOGGED_IN)
  val anotherCollaboratorEmail = "collaborator@example.com"
  val yetAnotherCollaboratorEmail = "collaborator2@example.com"

  val loggedInUser: DeveloperSession = DeveloperSession(session)

  val testing: ApplicationState = ApplicationState.testing.copy(updatedOn = DateTimeUtils.now.minusMinutes(1))
  val production: ApplicationState = ApplicationState.production("thirdpartydeveloper@example.com", "ABCD")
  val pendingApproval: ApplicationState = ApplicationState.pendingGatekeeperApproval("thirdpartydeveloper@example.com")

  val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, exampleContext, ApiVersion("api-example-microservice"))

  val tokens: ApplicationToken = ApplicationToken(Seq(aClientSecret(), aClientSecret()), "token")
  val exampleApiSubscription: Some[APISubscriptions] = Some(
    APISubscriptions(
      "Example API",
      "api-example-microservice",
      exampleContext,
      Seq(
        APISubscriptionStatus(
          "API1",
          "api-example-microservice",
          exampleContext,
          ApiVersionDefinition(version, APIStatus.STABLE),
          subscribed = true,
          requiresTrust = false,
          fields = emptyFields
        )
      )
    )
  )

  val defaultCheckInformation: CheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

  val groupedSubsSubscribedToExampleOnly: GroupedSubscriptions = GroupedSubscriptions(testApis = Seq.empty, apis = Seq.empty, exampleApi = exampleApiSubscription)

  val groupedSubsSubscribedToNothing: GroupedSubscriptions = GroupedSubscriptions(testApis = Seq.empty, apis = Seq.empty, exampleApi = None)

  trait ApplicationProvider {
    // N.B. All fixtures in any Setup that are to be used to supply this method MUST be global scope or lazy to avoid NPE
    def createApplication(): Application
  }

  trait BasicApplicationProvider extends ApplicationProvider {
    def createApplication() =
      Application(
        appId,
        clientId,
        "App name 1",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        Environment.PRODUCTION,
        Some("Description 1"),
        Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
        state = ApplicationState.production(loggedInUser.email, ""),
        access = Standard(
          redirectUris = Seq("https://red1", "https://red2"),
          termsAndConditionsUrl = Some("http://tnc-url.com")
        )
      )
  }

  def createFullyConfigurableApplication(
      collaborators: Set[Collaborator],
      appId: ApplicationId = appId,
      clientId: ClientId = clientId,
      state: ApplicationState = testing,
      checkInformation: Option[CheckInformation] = None,
      access: Access = Standard()
  ): Application = {

    Application(
      appId,
      clientId,
      appName,
      DateTimeUtils.now,
      DateTimeUtils.now,
      None,
      Environment.PRODUCTION,
      collaborators = collaborators,
      access = access,
      state = state,
      checkInformation = checkInformation
    )
  }

  def createPartiallyConfigurableApplication(
      appId: ApplicationId = appId,
      clientId: ClientId = clientId,
      userRole: Role = ADMINISTRATOR,
      state: ApplicationState = testing,
      checkInformation: Option[CheckInformation] = None,
      access: Access = Standard()
  ): Application = {

    // this is to ensure we always have one ADMINISTRATOR
    val anotherRole = if (userRole.isAdministrator) DEVELOPER else ADMINISTRATOR

    val collaborators = Set(
      Collaborator(loggedInUser.email, userRole),
      Collaborator(anotherCollaboratorEmail, anotherRole)
    )

    createFullyConfigurableApplication(collaborators, appId, clientId, state, checkInformation, access)
  }

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationProvider {
    val landingPageView = app.injector.instanceOf[LandingPageView]
    val unauthorisedAppDetailsView = app.injector.instanceOf[UnauthorisedAppDetailsView]
    val nameSubmittedView = app.injector.instanceOf[NameSubmittedView]
    val teamView = app.injector.instanceOf[TeamView]
    val teamMemberAddView = app.injector.instanceOf[TeamMemberAddView]
    val teamMemberRemoveConfirmationView = app.injector.instanceOf[TeamMemberRemoveConfirmationView]
    val termsOfUseView = app.injector.instanceOf[TermsOfUseView]
    val confirmNameView = app.injector.instanceOf[ConfirmNameView]
    val contactDetailsView = app.injector.instanceOf[ContactDetailsView]
    val apiSubscriptionsViewTemplate = app.injector.instanceOf[ApiSubscriptionsView]
    val privacyPolicyView = app.injector.instanceOf[PrivacyPolicyView]
    val termsAndConditionsView = app.injector.instanceOf[TermsAndConditionsView]

    val applicationCheck = app.injector.instanceOf[ApplicationCheck]

    val underTest = new ApplicationCheck(
      applicationServiceMock,
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      landingPageView,
      unauthorisedAppDetailsView,
      nameSubmittedView,
      teamView,
      teamMemberAddView,
      teamMemberRemoveConfirmationView,
      termsOfUseView,
      confirmNameView,
      contactDetailsView,
      apiSubscriptionsViewTemplate,
      privacyPolicyView,
      termsAndConditionsView
    )

    val application = createApplication()

    implicit val hc: HeaderCarrier = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)

    givenApplicationUpdateSucceeds()

    fetchByApplicationIdReturns(appId, application)

    fetchCredentialsReturns(application, tokens)

    givenRemoveTeamMemberSucceeds(loggedInUser)

    givenUpdateCheckInformationSucceeds(application)

    givenApplicationNameIsValid()

    givenApplicationHasNoSubs(application)

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val loggedInRequestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    def idAttributeOnCheckedInput(result: Future[Result]): String = Jsoup.parse(contentAsString(result)).select("input[checked]").attr("id")
  }

  "check request submitted" should {
    "return credentials requested page" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = underTest.credentialsRequested(appId)(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Request received")
      body should include("We've sent you a confirmation email")
      body should include("What happens next?")
      body should include("We may ask for a demonstration of your software.")
      body should include("The checking process can take up to 10 working days.")
      body should include("By requesting credentials you've created a new production application")
    }
    "return forbidden when not logged in" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val result = underTest.credentialsRequested(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }
  }

  "landing page" should {
    "return landing page" in new Setup {

      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Get production credentials")
      body should include("Save and come back later")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "show all steps as required when no check information exists" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show app name step as complete when it has been done" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(CheckInformation(confirmedName = true)))

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepCompleteIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show api subscription step as complete when it has been done" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(CheckInformation(apiSubscriptionsConfirmed = true)))

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepCompleteIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show contact details step as complete when it has been done" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(
        checkInformation = Some(CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678"))))
      )

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepCompleteIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show privacy policy and terms and conditions step as complete when it has been done" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(
        checkInformation = Some(CheckInformation(providedPrivacyPolicyURL = true, providedTermsAndConditionsURL = true))
      )

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepCompleteIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
    }

    "show agree to terms of use step as complete when it has been done" in new Setup {
      def createApplication() =
        createPartiallyConfigurableApplication(
          checkInformation = Some(CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0"))))
        )

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepCompleteIndication("agree-terms-status"))
    }

    "show api subscription configuration step as complete when it has been done" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(CheckInformation(apiSubscriptionConfigurationsConfirmed = true)))

      givenApplicationHasSubs(application, sampleSubscriptionsWithSubscriptionConfiguration(application))

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      private val body = contentAsString(result)
      body should include(stepRequiredIndication("app-name-status"))
      body should include(stepRequiredIndication("api-subscriptions-status"))
      body should include(stepRequiredIndication("contact-details-status"))
      body should include(stepRequiredIndication("urls-status"))
      body should include(stepRequiredIndication("agree-terms-status"))
      body should include(stepCompleteIndication("api-subscription-configurations-status"))
    }

    "successful submit action should take you to the check-your-answers page" in new Setup {
      def createApplication() =
        createPartiallyConfigurableApplication(checkInformation =
          Some(
            CheckInformation(
              confirmedName = true,
              apiSubscriptionsConfirmed = true,
              apiSubscriptionConfigurationsConfirmed = true,
              Some(ContactDetails("Example Name", "name@example.com", "012346789")),
              providedPrivacyPolicyURL = true,
              providedTermsAndConditionsURL = true,
              teamConfirmed = true,
              Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0"))
            )
          )
        )

      when(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(any[HeaderCarrier]))
        .thenReturn(successful(ApplicationUpliftSuccessful))

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/check-your-answers")
    }

    "successful submit action should take you to the check-your-answers page when no configurations confirmed because none required" in new Setup {
      def createApplication() =
        createPartiallyConfigurableApplication(checkInformation =
          Some(
            CheckInformation(
              confirmedName = true,
              apiSubscriptionsConfirmed = true,
              apiSubscriptionConfigurationsConfirmed = false,
              Some(ContactDetails("Example Name", "name@example.com", "012346789")),
              providedPrivacyPolicyURL = true,
              providedTermsAndConditionsURL = true,
              teamConfirmed = true,
              Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0"))
            )
          )
        )

      when(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(any[HeaderCarrier]))
        .thenReturn(successful(ApplicationUpliftSuccessful))

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/check-your-answers")
    }

    "validation failure submit action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.requestCheckPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.requestCheckAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "api subscriptions review" should {
    "return page" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      val subsData = Seq(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))
      givenApplicationHasSubs(application, subsData)

      private val result = addToken(underTest.apiSubscriptionsPage(application.id))(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Confirm which APIs you want to use")
      contentAsString(result) should include(generateName("api1"))
    }

    "success action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      val subsData = Seq(exampleSubscriptionWithoutFields("api1"), exampleSubscriptionWithoutFields("api2"))
      givenApplicationHasSubs(application, subsData)

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.apiSubscriptionsPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return 404 NOT FOUND when no API subscriptions are retrieved" in new Setup with BasicApplicationProvider {
      givenApplicationHasNoSubs(application)

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return 404 NOT FOUND when only API-EXAMPLE-MICROSERVICE API is subscribed to" in new Setup with BasicApplicationProvider {
      givenApplicationHasSubs(application, Seq(onlyApiExampleMicroserviceSubscribedTo))

      private val result = addToken(underTest.apiSubscriptionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "contact review" should {
    "return page" in new Setup {

      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val result = addToken(underTest.contactPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Who to contact about your application")
      contentAsString(result) should include("tester@example.com")
    }

    "successful contact action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val requestWithFormBody = loggedInRequest
        .withFormUrlEncodedBody("email" -> "email@example.com", "telephone" -> "0000", "fullname" -> "john smith")

      private val result = addToken(underTest.contactAction(appId))(requestWithFormBody)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure contact action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.contactPage(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.contactPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.contactPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.contactAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "name review" should {
    "return page" in new Setup {

      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.namePage(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Confirm the name of your application")
    }

    "successful name action different names" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "My First Tax App")

      private val result = addToken(underTest.nameAction(appId))(requestWithFormBody)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, "My First Tax App", application.description, application.access)

      await(result) // await before verify
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successful name action same names" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "app")

      private val result = addToken(underTest.nameAction(appId))(requestWithFormBody)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, application.name, application.description, application.access)

      await(result) // await before verify
      verify(underTest.applicationService, never)
        .update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(confirmedName = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "Validation failure name is blank action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> "")

      private val result = addToken(underTest.nameAction(appId))(requestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "Validation failure name contains HMRC action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      when(underTest.applicationService.isApplicationNameValid(*, *, *)(any[HeaderCarrier]))
        .thenReturn(Future.successful(Invalid.invalidName))

      private val applicationName = "Blacklisted HMRC"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = addToken(underTest.nameAction(appId))(requestWithFormBody)

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Application name must not include HMRC or HM Revenue and Customs")

      verify(underTest.applicationService)
        .isApplicationNameValid(eqTo(applicationName), eqTo(Environment.PRODUCTION), eqTo(Some(appId)))(any[HeaderCarrier])
    }

    "Validation failure when duplicate name" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      when(underTest.applicationService.isApplicationNameValid(*, *, *)(any[HeaderCarrier]))
        .thenReturn(Future.successful(Invalid.duplicateName))

      private val applicationName = "Duplicate Name"

      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("applicationName" -> applicationName)

      private val result = addToken(underTest.nameAction(appId))(requestWithFormBody)

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("That application name already exists. Enter a unique name for your application.")

      verify(underTest.applicationService)
        .isApplicationNameValid(eqTo(applicationName), eqTo(Environment.PRODUCTION), eqTo(Some(appId)))(any[HeaderCarrier])
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.namePage(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.namePage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.namePage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.nameAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "privacy policy review" should {
    "return page" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Does your application have a privacy policy?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = false)
      lazy val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))

      def createApplication() = createPartiallyConfigurableApplication(access = access, checkInformation = Some(checkInformation))

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      lazy val access = Standard().copy(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      def createApplication() = createPartiallyConfigurableApplication(access = access, checkInformation = Some(checkInformation))

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedPrivacyPolicyURL = true)
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(checkInformation))

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)
      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "http://privacypolicy.example.com")

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls)
      private val standardAccess = Standard(privacyPolicyUrl = Some("http://privacypolicy.example.com"))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, application.name, application.description, standardAccess)

      await(result) // await before verify
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }
    "successfully process when no URL" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = addToken(underTest.privacyPolicyAction(application.id))(loggedInRequestWithUrls)
      private val standardAccess: Standard = Standard(privacyPolicyUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, application.name, application.description, standardAccess)

      await(result) // await before verify
      verify(underTest.applicationService)
        .update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(providedPrivacyPolicyURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when privacy policy url is invalid" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "privacyPolicyURL" -> "invalid url")

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls)
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when privacy policy url is missing" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithUrls)
      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden when accessing the action without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return forbidden when accessing the page without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.privacyPolicyPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.privacyPolicyAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms and conditions review" should {
    "return page" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Does your application have terms and conditions?")
    }

    "return page with no option pre-selected when the step has not been completed and no URL has been provided" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe ""
    }

    "return page with yes pre-selected when the step has not been completed but a URL has already been provided" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = false)
      lazy val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      def createApplication() = createPartiallyConfigurableApplication(access = access, checkInformation = Some(checkInformation))

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with yes pre-selected when the step was previously completed with a URL" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      lazy val access = Standard().copy(termsAndConditionsUrl = Some("http://termsandconds.example.com"))
      def createApplication() = createPartiallyConfigurableApplication(access = access, checkInformation = Some(checkInformation))

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "yes"
    }

    "return page with no pre-selected when the step was previously completed with no URL" in new Setup {
      lazy val checkInformation = defaultCheckInformation.copy(providedTermsAndConditionsURL = true)
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(checkInformation))

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      idAttributeOnCheckedInput(result) shouldBe "no"
    }

    "successfully process valid urls" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls =
        loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "http://termsAndConditionsURL.example.com")

      private val result = addToken(underTest.termsAndConditionsAction(application.id))(loggedInRequestWithUrls)
      private val standardAccess = Standard(termsAndConditionsUrl = Some("http://termsAndConditionsURL.example.com"))
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, application.name, application.description, standardAccess)

      await(result) // await before verify
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "successfully process when doesn't have url" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(defaultCheckInformation))

      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "false")

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls)
      private val standardAccess = Standard(termsAndConditionsUrl = None)
      private val expectedUpdateRequest =
        UpdateApplicationRequest(application.id, application.deployedTo, application.name, application.description, standardAccess)

      await(result) // await before verify
      verify(underTest.applicationService).update(eqTo(expectedUpdateRequest))(any[HeaderCarrier])
      verify(underTest.applicationService)
        .updateCheckInformation(eqTo(application), eqTo(defaultCheckInformation.copy(providedTermsAndConditionsURL = true)))(any[HeaderCarrier])
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")
    }

    "fail validation when terms and conditions url is invalid but hasUrl true" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true", "termsAndConditionsURL" -> "invalid url")

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls)
      status(result) shouldBe BAD_REQUEST
    }

    "fail validation when terms and conditions url is missing but hasUrl true" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val loggedInRequestWithUrls = loggedInRequest.withFormUrlEncodedBody("hasUrl" -> "true")

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithUrls)
      status(result) shouldBe BAD_REQUEST
    }

    "action unavailable when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "page unavailable when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "redirect to the application credentials tab when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.termsAndConditionsPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to the application credentials tab when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.termsAndConditionsAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "terms of use review" should {
    "return page" in new Setup {

      def createApplication() = createPartiallyConfigurableApplication()
      private val result = addToken(underTest.termsOfUsePage(appId))(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
    }

    "be forbidden when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.termsOfUsePage(appId))(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "action is forbidden when accessed without being an admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return a bad request when the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.termsOfUsePage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.termsOfUsePage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is already approved" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = production)

      private val result = addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when an attempt is made to submit and the app is pending check" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(state = pendingApproval)

      private val result = addToken(underTest.termsOfUseAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }

    "successful terms of use action" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()
      private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")

      private val result = addToken(underTest.termsOfUseAction(appId))(requestWithFormBody)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/request-check")

    }
  }

  "Manage teams checks" should {
    "return manage team list page when check page is navigated to" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.team(appId))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Add members of your organisation and give them permissions to access this application")
      contentAsString(result) should include(developerDto.email)
    }

    "not return the manage team list page when not logged in" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.team(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "team post redirect to check landing page" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = Some(CheckInformation()))

      private val result = addToken(underTest.teamAction(appId))(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/request-check")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(any[HeaderCarrier])
    }

    "team post doesn't redirect to the check landing page when not logged in" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.teamAction(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "return add team member page when check page is navigated to" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.teamAddMember(appId))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Add a team member")
    }

    "not return the add team member page when not logged in" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.teamAddMember(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    val hashedAnotherCollaboratorEmail: String = anotherCollaboratorEmail.toSha256

    "return remove team member confirmation page when navigated to" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Are you sure you want to remove this team member from your application?")

      contentAsString(result) should include(anotherCollaboratorEmail)
    }

    "not return the remove team member confirmation page when not logged in" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "redirect to the team member list when the remove confirmation post is executed" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      val request = loggedInRequest.withFormUrlEncodedBody("email" -> anotherCollaboratorEmail)

      private val result = addToken(underTest.teamMemberRemoveAction(appId))(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/request-check/team")

      verify(underTest.applicationService).removeTeamMember(eqTo(application), eqTo(anotherCollaboratorEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
    }

    "team post redirect to check landing page when no check information on application" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(checkInformation = None)

      private val result = addToken(underTest.teamAction(appId))(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/request-check")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(any[HeaderCarrier])
    }
  }

  "unauthorised App details" should {
    "redirect to landing page when Admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication()

      private val result = addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/request-check")
    }

    "return unauthorised App details page with one Admin" in new Setup {
      def createApplication() = createPartiallyConfigurableApplication(userRole = DEVELOPER)

      private val result = addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Production application")
      body should include("You cannot view this application because you're not an administrator.")
      body should include("Ask the administrator")
      body should include(anotherCollaboratorEmail)
    }

    "return unauthorised App details page with 2 Admins " in new Setup {
      lazy val collaborators = Set(
        Collaborator(loggedInUser.email, DEVELOPER),
        Collaborator(anotherCollaboratorEmail, ADMINISTRATOR),
        Collaborator(yetAnotherCollaboratorEmail, ADMINISTRATOR)
      )

      def createApplication() = createFullyConfigurableApplication(collaborators = collaborators)

      private val result = addToken(underTest.unauthorisedAppDetails(appId))(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

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
