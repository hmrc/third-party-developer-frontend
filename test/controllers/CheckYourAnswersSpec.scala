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
import controllers.checkpages.{ApplicationCheck, CheckYourAnswers}
import domain.models.apidefinitions.{APISubscriptionStatus, APIVersion}
import domain.models.applications.{Access, ApplicationState, CheckInformation, ContactDetails, Role, Standard, TermsOfUseAgreement}
import domain.models.developers.Session
import helpers.string._
import mocks.service._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.checkpages.{ApiSubscriptionsView, ConfirmNameView, ContactDetailsView, PrivacyPolicyView, TermsAndConditionsView, TermsOfUseView}
import views.html.checkpages.applicationcheck.LandingPageView
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView}
import views.html.checkpages.checkyouranswers.CheckYourAnswersView
import views.html.checkpages.checkyouranswers.team.TeamView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import domain.models.applications.ClientSecret
import domain.models.developers.Developer
import domain.models.developers.LoggedInState
import domain.models.developers.DeveloperSession
import domain.models.applications.Application
import domain.models.applications.Environment
import domain.models.applications.Collaborator
import domain.models.applications.ApplicationToken
import domain.models.apidefinitions.APIStatus
import domain.models.subscriptions.APISubscription
import domain.ApplicationUpliftSuccessful
import domain.DeskproTicketCreationFailed
import domain.ApplicationAlreadyExists
import domain.models.applications.Role.{ADMINISTRATOR, DEVELOPER}

class CheckYourAnswersSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken with SubscriptionsBuilder{

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  val appId = "1234"
  val appName: String = "app"
  val clientId = "clientIdzzz"
  val sessionId = "sessionId"


  val developerDto = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(sessionId, developerDto, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val anotherCollaboratorEmail = "collaborator@example.com"
  val hashedAnotherCollaboratorEmail: String = anotherCollaboratorEmail.toSha256

  val testing: ApplicationState = ApplicationState.testing.copy(updatedOn = DateTimeUtils.now.minusMinutes(1))
  val production: ApplicationState = ApplicationState.production("thirdpartydeveloper@example.com", "ABCD")
  val pendingApproval: ApplicationState = ApplicationState.pendingGatekeeperApproval("thirdpartydeveloper@example.com")
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

  val emptyFields = emptySubscriptionFieldsWrapper("myAppId","myClientId", "context", "version")

  val exampleApiSubscription = Some(APISubscriptions("Example API", "api-example-microservice", "exampleContext",
    Seq(APISubscriptionStatus("API1", "api-example-microservice", "exampleContext",
      APIVersion("version", APIStatus.STABLE), subscribed = true, requiresTrust = false,
      fields = emptyFields))))

  val groupedSubs = GroupedSubscriptions(Seq.empty,
    Seq(APISubscriptions("API1", "ServiceName", "apiContent",
      Seq(APISubscriptionStatus(
        "API1",
        "subscriptionServiceName",
        "context",
        APIVersion("version", APIStatus.STABLE),
        subscribed = true,
        requiresTrust = false,
        fields = emptyFields)))))

  val groupedSubsSubscribedToExampleOnly = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = exampleApiSubscription)

  val groupedSubsSubscribedToNothing = GroupedSubscriptions(
    testApis = Seq.empty,
    apis = Seq.empty,
    exampleApi = None)

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val checkYourAnswersView = app.injector.instanceOf[CheckYourAnswersView]
    val landingPageView = app.injector.instanceOf[LandingPageView]
    val teamView = app.injector.instanceOf[TeamView]
    val teamMemberAddView = app.injector.instanceOf[TeamMemberAddView]
    val teamMemberRemoveConfirmationView = app.injector.instanceOf[TeamMemberRemoveConfirmationView]
    val termsOfUseView = app.injector.instanceOf[TermsOfUseView]
    val confirmNameView = app.injector.instanceOf[ConfirmNameView]
    val termsAndConditionsView = app.injector.instanceOf[TermsAndConditionsView]
    val privacyPolicyView = app.injector.instanceOf[PrivacyPolicyView]
    val apiSubscriptionsViewTemplate = app.injector.instanceOf[ApiSubscriptionsView]
    val contactDetailsView = app.injector.instanceOf[ContactDetailsView]

    val underTest = new CheckYourAnswers(
      applicationServiceMock,
      mock[ApplicationCheck],
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      checkYourAnswersView,
      landingPageView,
      teamView,
      teamMemberAddView,
      teamMemberRemoveConfirmationView,
      termsOfUseView,
      confirmNameView,
      termsAndConditionsView,
      privacyPolicyView,
      apiSubscriptionsViewTemplate,
      contactDetailsView
    )

    fetchSessionByIdReturns(sessionId, session)

    updateApplicationSuccessful()

    fetchByApplicationIdReturns(application.id, application)

    fetchCredentialsReturns(application, tokens)

    givenRemoveTeamMemberSucceeds(loggedInUser)

    givenUpdateCheckInformationSucceeds(application)

    val context = "apiContent"
    val version = "version"
    val emptyFields = emptySubscriptionFieldsWrapper("myAppId","myClientId",context, version)

    val subscriptions = Seq(
      APISubscriptions("API1", "ServiceName", context,
        Seq(
          APISubscriptionStatus(
            "API1",
            "subscriptionServiceName",
            context,
            APIVersion(version, APIStatus.STABLE),
            subscribed = true,
            requiresTrust = false,
            fields = emptyFields
          ))))

    val groupedSubs = GroupedSubscriptions(Seq.empty,subscriptions)

    fetchAllSubscriptionsReturns(Seq(mock[APISubscription]))

    givenApplicationNameIsValid()

    implicit val hc = HeaderCarrier()

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val loggedInRequestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val defaultCheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

    def givenApplicationExists(appId: String = appId, clientId: String = clientId, userRole: Role = ADMINISTRATOR,
      state: ApplicationState = testing,
      checkInformation: Option[CheckInformation] = None,
      access: Access = Standard()): Application = {

      val collaborators = Set(
        Collaborator(loggedInUser.email, userRole),
        Collaborator(anotherCollaboratorEmail, Role.DEVELOPER)
      )

      val application = Application(appId, clientId, appName, DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION,
        collaborators = collaborators, access = access, state = state, checkInformation = checkInformation)

      fetchByApplicationIdReturns(application.id, application)
      fetchCredentialsReturns(application,tokens)
      givenApplicationHasNoSubs(application)
      givenUpdateCheckInformationSucceeds(application)

      application
    }
    def mockRequestUplift() {
      when(underTest.applicationService.requestUplift (eqTo (appId), any[String], any[DeveloperSession] ) (any[HeaderCarrier] ) )
      .thenReturn (ApplicationUpliftSuccessful)
    }

    def failedToCreateDeskproTicket() {
      when(underTest.applicationService.requestUplift (eqTo (appId), any[String], any[DeveloperSession] ) (any[HeaderCarrier] ) )
      .thenReturn(Future.failed(new DeskproTicketCreationFailed("Failed")))
    }

  }

  "validate failure when application name already exists" in new Setup {
    private val application = givenApplicationExists(
      checkInformation = Some(CheckInformation(
        confirmedName = true,
        apiSubscriptionsConfirmed = true,
        apiSubscriptionConfigurationsConfirmed = true,
        Some(ContactDetails("Example Name", "name@example.com", "012346789")),
        providedPrivacyPolicyURL = true,
        providedTermsAndConditionsURL = true,
        teamConfirmed = true,
        Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0"))
      ))
    )
    private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val expectedCheckInformation: CheckInformation = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)

    when(underTest.applicationService.requestUplift(eqTo(appId), eqTo(application.name), eqTo(loggedInUser))(any[HeaderCarrier]))
      .thenAnswer(new Answer[Future[ApplicationUpliftSuccessful]]() {
        def answer(invocation: InvocationOnMock): Future[ApplicationUpliftSuccessful] = {
          Future.failed(new ApplicationAlreadyExists)
        }
      })
    givenUpdateCheckInformationSucceeds(application, expectedCheckInformation)

    private val result = await(addToken(underTest.answersPageAction(appId))(requestWithFormBody))

    status(result) shouldBe CONFLICT
    verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(any[HeaderCarrier])

    private val errorMessageElement = Jsoup.parse(bodyOf(result)).select("td#confirmedName span.error-message")
    errorMessageElement.text() shouldBe "That application name already exists. Enter a unique name for your application."
  }

  "check your answers submitted" should {
    "return credentials requested page" in new Setup {
      givenApplicationExists()
      mockRequestUplift()

      private val result = await(underTest.answersPageAction(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(checkpages.routes.ApplicationCheck.credentialsRequested(appId).url)
    }

    "return forbidden when not logged in" in new Setup {
      givenApplicationExists()
      private val result = await(underTest.answersPageAction(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }
  }

  "check your answers page" should {
    "return check your answers page" in new Setup {

      givenApplicationExists()

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Check your answers before requesting credentials")
      body should include("About your application")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "return server error" in new Setup {
      failedToCreateDeskproTicket()

      givenApplicationExists()

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequest))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "validation failure submit action" in new Setup {
      mockRequestUplift()

      givenApplicationExists()

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe SEE_OTHER
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
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
      val application = givenApplicationExists(checkInformation = Some(CheckInformation()))

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(any[HeaderCarrier])
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
      val application = givenApplicationExists()

      val request = loggedInRequest.withFormUrlEncodedBody("email" -> anotherCollaboratorEmail)

      private val result = await(addToken(underTest.teamMemberRemoveAction(appId))(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers/team")

      verify(underTest.applicationService).removeTeamMember(eqTo(application),eqTo(anotherCollaboratorEmail), eqTo(loggedInUser.email))(any[HeaderCarrier])
    }

    "team post redirect to check landing page when no check information on application" in new Setup {
      val application = givenApplicationExists(checkInformation = None)

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(any[HeaderCarrier])
    }
  }

}
