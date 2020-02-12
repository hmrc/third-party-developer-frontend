package unit.controllers

import config.ErrorHandler
import controllers.{APISubscriptions, ApiSubscriptionsHelper, ApplicationCheck, CheckYourAnswers, GroupedSubscriptions}
import domain.Role._
import domain._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.Assertion
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContentAsEmpty, Result}
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

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  val defaultMode: CheckYourAnswersPageMode = CheckYourAnswersPageMode.RequestCheck

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
    val underTest = new CheckYourAnswers(
      mock[ApplicationService],
      mock[ApiSubscriptionsHelper],
      mock[ApplicationCheck],
      mock[SessionService],
      mockErrorHandler,
      messagesApi
    )

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

      val application = Application(appId, clientId, appName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), access = access, state = state, checkInformation = checkInformation)

      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens)
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq())

      application
    }

  }

  "validate failure when application name already exists" in new Setup {
          private val application = givenTheApplicationExists(checkInformation =
            Some(CheckInformation(
              confirmedName = true,
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

          private val result = await(addToken(underTest.answersPageAction(appId))(requestWithFormBody))

          status(result) shouldBe CONFLICT
          verify(underTest.applicationService).updateCheckInformation(mockEq(appId), mockEq(expectedCheckInformation))(any[HeaderCarrier])

          private val errorMessageElement = Jsoup.parse(bodyOf(result)).select("td#confirmedName span.error-message")
          errorMessageElement.text() shouldBe "That application name already exists. Enter a unique name for your application."
        }
  }
