/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import views.html.support.HelpWithSigningInView

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class HelpWithSigningInControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val helpWithUsingAnApiView = app.injector.instanceOf[HelpWithSigningInView]

    val underTest                            = new HelpWithSigningInController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      helpWithUsingAnApiView
    )
    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = "sessionId"
    val basicFlow                            = SupportFlow(sessionId, "?")
    val appropriateFlow                      = basicFlow.copy(entrySelection = SupportData.SigningIn.id)

    def shouldBeRedirectedToPreviousPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support"
    }

    def shouldBeRedirectedToNextPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/details"
    }
  }

  trait IsLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))
  }

  trait NoSupportSessionExists {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturnsNone(sessionId)
  }

  trait NotLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withSession(sessionParams: _*)

    fetchSessionByIdReturnsNone(sessionId)
  }

  trait IsPartLoggedInEnablingMFA {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA))
  }

  "HelpWithSigningInController" when {
    "invoking page()" should {
      "render the HelpWithSigningInView" in new Setup() with NotLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.page())(request)

        status(result) shouldBe OK
      }

      "render the previous page when flow is wrong" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow.copy(entrySelection = "Something else"))

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }

      "render the previous page when there is no flow" in new Setup with NoSupportSessionExists {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow)

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }
    }

    "invoke submitChoiceOfPrivateApi" should {
      "submit new valid request from form for 'Forgotten Password' choice" in new Setup with IsLoggedIn {
        val formRequest = request.withFormUrlEncodedBody(
          "choice" -> SupportData.ForgottenPassword.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToNextPage(result)
      }

      "submit new valid request from form for 'Access Codes' choice" in new Setup with IsLoggedIn {
        val formRequest = request.withFormUrlEncodedBody(
          "choice" -> SupportData.AccessCodes.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.UpdateWithDelta.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToNextPage(result)
      }

      "submit invalid request returns BAD_REQUEST" in new Setup with IsLoggedIn {
        val formRequest = request.withFormUrlEncodedBody(
          "bobbins" -> SupportData.AccessCodes.id
        )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.submit())(formRequest)

        status(result) shouldBe BAD_REQUEST
      }

      "submit valid request but no session" in new Setup with NoSupportSessionExists {
        val formRequest = request.withFormUrlEncodedBody(
          "choice" -> SupportData.AccessCodes.id
        )
        SupportServiceMock.GetSupportFlow.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToPreviousPage(result)
      }
    }
  }
}
