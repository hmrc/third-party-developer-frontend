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

import config.ErrorHandler
import domain._
import mocks.service.{ApplicationServiceMock, SessionServiceMock}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.filters.csrf.CSRF.TokenProvider
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

class AddApplicationStartSpec extends BaseControllerSpec
  with SubscriptionTestHelperSugar with WithCSRFAddToken {

  val appId = "1234"
  val clientId = "clientId123"

  val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val collaborator: Collaborator = Collaborator(loggedInUser.email, Role.ADMINISTRATOR)

  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(collaborator), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val underTest = new AddApplication(
      applicationServiceMock,
      sessionServiceMock,
      mock[AuditService],
      mock[ErrorHandler],
      messagesApi,
      cookieSigner
    )

    val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)

    fetchSessionByIdReturns(partLoggedInSessionId, partLoggedInSession)

    private val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)
      .withCSRFToken

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  "Add subordinate applications start page" should {
    "return the add applications page with the user logged in" in new Setup {
      private val result = await(underTest.addApplicationSubordinate()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Add an application to the sandbox")
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("get its sandbox credentials")
      bodyOf(result) should include("use its credentials for integration testing")
      bodyOf(result) should include("In production, your application will need to comply with the expectations set out in our")
      bodyOf(result) should include("Once you add your application and subscribe it to the sandbox APIs you want to integrate with you can:")
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

  "Add principal applications start page" should {
    "return the add applications page with the user logged in" in new Setup {
      private val result = await(underTest.addApplicationPrincipal()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Get production credentials")
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("Now that you've tested your software you can request production credentials to use live data.")
      bodyOf(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = await(underTest.addApplicationPrincipal()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    "redirect to the login screen when partly logged" in new Setup {
      private val result = await(underTest.addApplicationPrincipal()(partLoggedInRequest))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}
