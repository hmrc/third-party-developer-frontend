/*
 * Copyright 2019 HM Revenue & Customs
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

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.AddApplication
import domain.Environment.PRODUCTION
import domain._
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.TestApplications.{aStandardApplication, tokens}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global

class AddApplicationSuccessSpec extends BaseControllerSpec
  with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val principalApp = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val subordinateApp = Application(appId, clientId, "App name 2", DateTimeUtils.now, DateTimeUtils.now, Environment.SANDBOX, Some("Description 2"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red3", "https://red4"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  trait Setup {
    val underTest = new AddApplication(
      mock[ApplicationService],
      mock[SessionService],
      mock[AuditService],
      mock[ErrorHandler],
      messagesApi,
      mock[ApplicationConfig]
    )

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier]))
      .willReturn(Some(session))

    given(underTest.sessionService.fetch(mockEq(partLoggedInSessionId))(any[HeaderCarrier]))
      .willReturn(Some(partLoggedInSession))

    private val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest)(partLoggedInSessionId)
      .withSession(sessionParams: _*)

    def givenTheApplicationExists(application: Application) = {
      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.fetchCredentials(mockEq(application.id))(any[HeaderCarrier])).willReturn(tokens())
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq())
    }
  }

  "Add applications production success page" should {

    "return the page with the user is logged in" in new Setup {
      givenTheApplicationExists(principalApp)

      private val result = await(underTest.addApplicationSuccess(appId, Environment.PRODUCTION)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("You have admin rights over this production application.")
      bodyOf(result) should include("Before you can get production credentials")
      bodyOf(result) should include("You must complete the checklist for your application and submit it for checking.")
      bodyOf(result) should include("We take up to 10 working days to check applications and issue production credentials.")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {

      val request = FakeRequest()

      private val result = await(underTest.addApplicationSubordinate()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when partly logged" in new Setup {
      private val result = await(underTest.addApplicationSubordinate()(partLoggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  "Add applications subordinate success page" should {

    "return the page with the user is logged in" in new Setup {
      givenTheApplicationExists(subordinateApp)

      private val result = await(underTest.addApplicationSuccess(appId, Environment.PRODUCTION)(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("You can now use its credentials to test with sandbox APIs.")
      bodyOf(result) should include("Read the guidance on")
      bodyOf(result) should include("to find out which endpoints to use, creating a test user and types of test data.")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {

      val request = FakeRequest()

      private val result = await(underTest.addApplicationSubordinate()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when partly logged" in new Setup {
      private val result = await(underTest.addApplicationSubordinate()(partLoggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}
