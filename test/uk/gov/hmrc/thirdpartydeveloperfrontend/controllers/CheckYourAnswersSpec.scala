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

import java.util.UUID.randomUUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.{ApplicationCheck, CheckYourAnswers}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApplicationAlreadyExists, ApplicationUpliftSuccessful, DeskproTicketCreationFailed}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.{ADMINISTRATOR, DEVELOPER}
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import org.jsoup.Jsoup
import org.mockito.invocation.InvocationOnMock
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import views.html.checkpages._
import views.html.checkpages.applicationcheck.LandingPageView
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView}
import views.html.checkpages.checkyouranswers.CheckYourAnswersView
import views.html.checkpages.checkyouranswers.team.TeamView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import java.time.{LocalDateTime, ZoneOffset, Clock, Instant}

class CheckYourAnswersSpec
    extends BaseControllerSpec
    with LocalUserIdTracker
    with DeveloperBuilder
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with SubscriptionsBuilder
{

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, LocalDateTime.now())

  val appName: String = "app"
  val apiVersion = ApiVersion("version")

  val anotherCollaboratorEmail = "collaborator@example.com"
  val hashedAnotherCollaboratorEmail: String = anotherCollaboratorEmail.toSha256

  val testing: ApplicationState = ApplicationState.testing.copy(updatedOn = LocalDateTime.now.minusMinutes(1))
  val production: ApplicationState = ApplicationState.production("thirdpartydeveloper@example.com", "thirdpartydeveloper", "ABCD")
  val pendingApproval: ApplicationState = ApplicationState.pendingGatekeeperApproval("thirdpartydeveloper@example.com", "thirdpartydeveloper")

  val appTokens = ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, ApiContext("context"), apiVersion)

  val exampleApiSubscription = Some(
    APISubscriptions(
      "Example API",
      "api-example-microservice",
      ApiContext("exampleContext"),
      Seq(
        APISubscriptionStatus(
          "API1",
          "api-example-microservice",
          ApiContext("exampleContext"),
          ApiVersionDefinition(apiVersion, APIStatus.STABLE),
          subscribed = true,
          requiresTrust = false,
          fields = emptyFields
        )
      )
    )
  )

  val groupedSubs = GroupedSubscriptions(
    Seq.empty,
    Seq(
      APISubscriptions(
        "ServiceName",
        "apiContent",
        ApiContext("context"),
        Seq(
          APISubscriptionStatus(
            "API1",
            "subscriptionServiceName",
            ApiContext("context"),
            ApiVersionDefinition(apiVersion, APIStatus.STABLE),
            subscribed = true,
            requiresTrust = false,
            fields = emptyFields
          )
        )
      )
    )
  )

  val groupedSubsSubscribedToExampleOnly = GroupedSubscriptions(testApis = Seq.empty, apis = Seq.empty, exampleApi = exampleApiSubscription)

  val groupedSubsSubscribedToNothing = GroupedSubscriptions(testApis = Seq.empty, apis = Seq.empty, exampleApi = None)

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock with TermsOfUseVersionServiceMock {
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
    val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    val underTest = new CheckYourAnswers(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      mock[ApplicationCheck],
      sessionServiceMock,
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
      contactDetailsView,
      termsOfUseVersionServiceMock,
      clock
    )

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    updateApplicationSuccessful()

    fetchByApplicationIdReturns(sampleApp.id, sampleApp)

    fetchCredentialsReturns(sampleApp, appTokens)

    givenRemoveTeamMemberSucceeds(loggedInDeveloper)

    givenUpdateCheckInformationSucceeds(sampleApp)

    val context = ApiContext("apiContent")
    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, context, apiVersion)

    val subscriptions = Seq(
      APISubscriptions(
        "API1",
        "ServiceName",
        context,
        Seq(
          APISubscriptionStatus(
            "API1",
            "subscriptionServiceName",
            context,
            ApiVersionDefinition(apiVersion, APIStatus.STABLE),
            subscribed = true,
            requiresTrust = false,
            fields = emptyFields
          )
        )
      )
    )

    val groupedSubs = GroupedSubscriptions(Seq.empty, subscriptions)

    givenApplicationNameIsValid()

    implicit val hc = HeaderCarrier()

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
    val loggedInRequestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val defaultCheckInformation = CheckInformation(contactDetails = Some(ContactDetails("Tester", "tester@example.com", "12345678")))

    def givenApplicationExists(
        appId: ApplicationId = appId,
        clientId: ClientId = clientId,
        userRole: CollaboratorRole = ADMINISTRATOR,
        state: ApplicationState = testing,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Standard()
    ): Application = {

      val collaborators = Set(
        loggedInDeveloper.email.asCollaborator(userRole),
        anotherCollaboratorEmail.asDeveloperCollaborator
      )

      val application = Application(
        appId,
        clientId,
        appName,
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        grantLength,
        Environment.PRODUCTION,
        collaborators = collaborators,
        access = access,
        state = state,
        checkInformation = checkInformation
      )

      givenApplicationAction(application, loggedInDeveloper)
      fetchCredentialsReturns(application, appTokens)
      givenUpdateCheckInformationSucceeds(application)

      application
    }
    def mockRequestUplift() {
      when(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(*))
        .thenReturn(successful(ApplicationUpliftSuccessful))
    }

    def failedToCreateDeskproTicket() {
      when(underTest.applicationService.requestUplift(eqTo(appId), any[String], any[DeveloperSession])(*))
        .thenReturn(failed(new DeskproTicketCreationFailed("Failed")))
    }

  }

  "validate failure when application name already exists" in new Setup {
    private val application = givenApplicationExists(
      checkInformation = Some(
        CheckInformation(
          confirmedName = true,
          apiSubscriptionsConfirmed = true,
          apiSubscriptionConfigurationsConfirmed = true,
          Some(ContactDetails("Example Name", "name@example.com", "012346789")),
          providedPrivacyPolicyURL = true,
          providedTermsAndConditionsURL = true,
          teamConfirmed = true,
          List(TermsOfUseAgreement("test@example.com", LocalDateTime.now(ZoneOffset.UTC), "1.0"))
        )
      )
    )

    private val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody()

    val expectedCheckInformation: CheckInformation = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)

    when(underTest.applicationService.requestUplift(eqTo(appId), eqTo(application.name), eqTo(loggedInDeveloper))(*))
      .thenAnswer((i: InvocationOnMock) => {
        failed(new ApplicationAlreadyExists)
      })

    givenUpdateCheckInformationSucceeds(application, expectedCheckInformation)

    private val result = addToken(underTest.answersPageAction(appId))(requestWithFormBody)

    status(result) shouldBe CONFLICT
    verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(*)

    private val errorMessageElement = Jsoup.parse(contentAsString(result)).select("td#confirmedName span.govuk-error-message")
    errorMessageElement.text() shouldBe "Error: That application name already exists. Enter a unique name for your application."
  }

  "check your answers submitted" should {
    "return credentials requested page" in new Setup {
      givenApplicationExists()
      mockRequestUplift()

      private val result = underTest.answersPageAction(appId)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(checkpages.routes.ApplicationCheck.credentialsRequested(appId).url)
    }

    "return forbidden when not logged in" in new Setup {
      givenApplicationExists()
      private val result = underTest.answersPageAction(appId)(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }
  }

  "check your answers page" should {
    "return check your answers page when environment is Production" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
      givenApplicationExists()

      private val result = addToken(underTest.answersPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Check your answers before requesting credentials")
      body should include("About your application")
      body should include("Now request production credentials")
    }

    "return check your answers page when environment is QA" in new Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
      givenApplicationExists()

      private val result = addToken(underTest.answersPage(appId))(loggedInRequest)

      status(result) shouldBe OK
      private val body = contentAsString(result)

      body should include("Check your answers before requesting credentials")
      body should include("About your application")
      body should include("Now request to add your application to QA")
    }

    "return forbidden when accessed without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = addToken(underTest.answersPage(appId))(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }

    "return server error" in new Setup {
      failedToCreateDeskproTicket()

      givenApplicationExists()

      private val result = addToken(underTest.answersPageAction(appId))(loggedInRequest)
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "validation failure submit action" in new Setup {
      mockRequestUplift()

      givenApplicationExists()

      private val result = addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe SEE_OTHER
    }

    "return forbidden when accessing action without being an admin" in new Setup {
      givenApplicationExists(userRole = DEVELOPER)

      private val result = addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = addToken(underTest.answersPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = addToken(underTest.answersPage(appId))(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is already approved" in new Setup {
      givenApplicationExists(state = production)

      private val result = addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody)
      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when an attempt is made to submit and the app is pending check" in new Setup {
      givenApplicationExists(state = pendingApproval)

      private val result = addToken(underTest.answersPageAction(appId))(loggedInRequestWithFormBody)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "Manage teams checks" should {
    "return manage team list page when check page is navigated to" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.team(appId))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Add members of your organisation and give them permissions to access this application")
      contentAsString(result) should include(developer.email)
    }

    "not return the manage team list page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.team(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "team post redirect to check landing page" in new Setup {
      val application = givenApplicationExists(checkInformation = Some(CheckInformation()))

      private val result = addToken(underTest.teamAction(appId))(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(*)
    }

    "team post doesn't redirect to the check landing page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.teamAction(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "return add team member page when check page is navigated to" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.teamAddMember(appId))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Add a team member")
    }

    "not return the add team member page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.teamAddMember(appId))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "return remove team member confirmation page when navigated to" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Are you sure you want to remove this team member from your application?")

      contentAsString(result) should include(anotherCollaboratorEmail)
    }

    "not return the remove team member confirmation page when not logged in" in new Setup {
      givenApplicationExists()

      private val result = addToken(underTest.teamMemberRemoveConfirmation(appId, hashedAnotherCollaboratorEmail))(loggedOutRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/login")
    }

    "redirect to the team member list when the remove confirmation post is executed" in new Setup {
      val application = givenApplicationExists()

      val request = loggedInRequest.withFormUrlEncodedBody("email" -> anotherCollaboratorEmail)

      private val result = addToken(underTest.teamMemberRemoveAction(appId))(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/check-your-answers/team")

      verify(underTest.applicationService).removeTeamMember(eqTo(application), eqTo(anotherCollaboratorEmail), eqTo(loggedInDeveloper.email))(*)
    }

    "team post redirect to check landing page when no check information on application" in new Setup {
      val application = givenApplicationExists(checkInformation = None)

      private val result = addToken(underTest.teamAction(appId))(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/check-your-answers")

      private val expectedCheckInformation = CheckInformation(teamConfirmed = true)
      verify(underTest.applicationService).updateCheckInformation(eqTo(application), eqTo(expectedCheckInformation))(*)
    }
  }

}
