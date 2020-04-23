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

import controllers.checkpages.{ApplicationCheck, CheckYourAnswers}
import domain.Role._
import domain._
import helpers.string._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future
import scala.concurrent.Future.successful


class CheckYourAnswersSpec extends BaseControllerSpec with SubscriptionTestHelperSugar with WithCSRFAddToken {

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
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationToken("clientId", Seq(aClientSecret(), aClientSecret()), "token")

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
    val underTest = new CheckYourAnswers(
      mock[ApplicationService],
      mock[ApiSubscriptionsHelper],
      mock[ApplicationCheck],
      mock[SessionService],
      mockErrorHandler,
      messagesApi,
      cookieSigner
    )

    when(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .thenReturn(Some(session))

    when(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .thenReturn(successful(ApplicationUpdateSuccessful))

    when(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier]))
      .thenReturn(successful(application))

    when(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier]))
      .thenReturn(tokens)

    when(underTest.applicationService.removeTeamMember(any[Application], any[String], mockEq(loggedInUser.email))(any[HeaderCarrier]))
      .thenReturn(ApplicationUpdateSuccessful)

    when(underTest.applicationService.updateCheckInformation(mockEq(appId), any[CheckInformation])(any[HeaderCarrier]))
      .thenReturn(ApplicationUpdateSuccessful)

    val subscriptions = Seq(
      APISubscriptions("API1", "ServiceName", "apiContent",
        Seq(APISubscriptionStatus(
          "API1", "subscriptionServiceName", "context", APIVersion("version", APIStatus.STABLE), subscribed = true, requiresTrust = false))))
    val groupedSubs = GroupedSubscriptions(Seq.empty,subscriptions)
    
    when(underTest.applicationService.fetchAllSubscriptions(any[Application])(any[HeaderCarrier]))
      .thenReturn(successful(Seq(mock[APISubscription])))
    
    when(underTest.applicationService.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(successful(Valid))

    val hc = HeaderCarrier()

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val loggedInRequestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val defaultCheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

    def givenTheApplicationExists(appId: String = appId, clientId: String = clientId, userRole: Role = ADMINISTRATOR,
      state: ApplicationState = testing,
      checkInformation: Option[CheckInformation] = None,
      access: Access = Standard()): Application = {

      val collaborators = Set(
        Collaborator(loggedInUser.email, userRole),
        Collaborator(anotherCollaboratorEmail, Role.DEVELOPER)
      )

      val application = Application(appId, clientId, appName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION,
        collaborators = collaborators, access = access, state = state, checkInformation = checkInformation)

      when(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).thenReturn(application)
      when(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).thenReturn(tokens)
      when(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).thenReturn(Seq())

      application
    }
    def mockRequestUplift() {
      when(underTest.applicationService.requestUplift (mockEq (appId), any[String], any[DeveloperSession] ) (any[HeaderCarrier] ) )
      .thenReturn (ApplicationUpliftSuccessful)
    }

    def failedToCreateDeskproTicket() {
      when(underTest.applicationService.requestUplift (mockEq (appId), any[String], any[DeveloperSession] ) (any[HeaderCarrier] ) )
      .thenReturn(Future.failed(new DeskproTicketCreationFailed("Failed")))
    }

  }

  "validate failure when application name already exists" in new Setup {
    private val application = givenTheApplicationExists(
      checkInformation = Some(CheckInformation(
        confirmedName = true,
        apiSubscriptionsConfirmed = true,
        Some(ContactDetails("Example Name", "name@example.com", "012346789")),
        providedPrivacyPolicyURL = true,
        providedTermsAndConditionsURL = true,
        teamConfirmed = true,
        Seq(TermsOfUseAgreement("test@example.com", DateTimeUtils.now, "1.0"))
      ))
    )
    private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val expectedCheckInformation: CheckInformation = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)

    when(underTest.applicationService.requestUplift(mockEq(appId), mockEq(application.name), mockEq(loggedInUser))(any[HeaderCarrier]))
      .thenAnswer(new Answer[Future[ApplicationUpliftSuccessful]]() {
        def answer(invocation: InvocationOnMock): Future[ApplicationUpliftSuccessful] = {
          Future.failed(new ApplicationAlreadyExists)
        }
      })
    when(underTest.applicationService.updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier]))
      .thenReturn(ApplicationUpdateSuccessful)

    private val result = await(addToken(underTest.answersPageAction(appId))(requestWithFormBody))

    status(result) shouldBe CONFLICT
    verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier])

    private val errorMessageElement = Jsoup.parse(bodyOf(result)).select("td#confirmedName span.error-message")
    errorMessageElement.text() shouldBe "That application name already exists. Enter a unique name for your application."
  }

  "check your answers submitted" should {
    "return credentials requested page" in new Setup {
      givenTheApplicationExists()
      mockRequestUplift()

      private val result = await(underTest.answersPageAction(appId)(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(checkpages.routes.ApplicationCheck.credentialsRequested(appId).url)
    }

    "return forbidden when not logged in" in new Setup {
      givenTheApplicationExists()
      private val result = await(underTest.answersPageAction(appId)(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }
  }

  "check your answers page" should {
    "return check your answers page" in new Setup {

      givenTheApplicationExists()

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe OK
      private val body = bodyOf(result)

      body should include("Check your answers before requesting credentials")
      body should include("About your application")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe FORBIDDEN
    }

    "return server error" in new Setup {
      failedToCreateDeskproTicket()

      givenTheApplicationExists()

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequest))

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "validation failure submit action" in new Setup {
      mockRequestUplift()

      givenTheApplicationExists()

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe SEE_OTHER
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      givenTheApplicationExists(userRole = DEVELOPER)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.answersPage(appId))(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenTheApplicationExists(state = production)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenTheApplicationExists(state = pendingApproval)

      private val result = await(addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody))

      status(result) shouldBe BAD_REQUEST
    }
  }

  "Manage teams checks" should {
    "return manage team list page when check page is navigated to" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.team(appId))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Add members of your organisation and give them permissions to access this application")
      bodyOf(result) should include(developerDto.email)
    }

    "not return the manage team list page when not logged in" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.team(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "team post redirect to check landing page" in new Setup {
      givenTheApplicationExists(checkInformation = Some(CheckInformation()))

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier])
    }

    "team post doesn't redirect to the check landing page when not logged in" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.teamAction(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }


    "return add team member page when check page is navigated to" in new Setup{
      givenTheApplicationExists()

      private val result = await(addToken(underTest.teamAddMember(appId))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Add a team member")
    }

    "not return the add team member page when not logged in" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.teamAddMember(appId))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "return remove team member confirmation page when navigated to" in new Setup{
      givenTheApplicationExists()

      private val result = await(addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedInRequest))

      status(result) shouldBe OK

      bodyOf(result) should include("Are you sure you want to remove this team member from your application?")

      bodyOf(result) should include(anotherCollaboratorEmail)
    }

    "not return the remove team member confirmation page when not logged in" in new Setup {
      givenTheApplicationExists()

      private val result = await(addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedOutRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }


    "redirect to the team member list when the remove confirmation post is executed" in new Setup{
      val app = givenTheApplicationExists()

      val request = loggedInRequest.withFormUrlEncodedBody("email" -> anotherCollaboratorEmail)

      private val result = await(addToken(underTest.teamMemberRemoveAction(appId))(request))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers/team")

      verify(underTest.applicationService).removeTeamMember(mockEq(app),mockEq(anotherCollaboratorEmail), mockEq(loggedInUser.email))(any[HeaderCarrier])
    }

    "team post redirect to check landing page when no check information on application" in new Setup {
      givenTheApplicationExists(checkInformation = None)

      private val result = await(addToken(underTest.teamAction(appId))(loggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier])
    }
  }

}
