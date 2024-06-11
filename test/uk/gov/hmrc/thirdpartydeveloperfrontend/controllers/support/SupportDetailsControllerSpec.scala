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

import views.html.SupportEnquiryView
import views.html.support.{SupportPageConfirmationView, SupportPageDetailView}

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithSupportSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SupportDetailsControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val supportPageDetailView       = app.injector.instanceOf[SupportPageDetailView]
    val supportEnquiryView          = app.injector.instanceOf[SupportEnquiryView]
    val supportPageConfirmationView = app.injector.instanceOf[SupportPageConfirmationView]

    val underTest = new SupportDetailsController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      supportPageDetailView,
      supportPageConfirmationView
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = "sessionId"

  }

  trait IsLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, cookieSigner)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))
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

  "SupportDetailsController" when {
    "invoke supportDetailsPage" should {
      "render the new support details page" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds()

        val result = addToken(underTest.supportDetailsPage())(request)

        status(result) shouldBe OK
      }
    }

    "invoke submitSupportDetails" should {
      "submit new request with name, email & comments from form" in new Setup {
        val request = FakeRequest()
          .withSession(sessionParams: _*)
          .withFormUrlEncodedBody(
            "fullName"     -> "Peter Smith",
            "emailAddress" -> "peter@example.com",
            "details"      -> "A+++, good seller, would buy again, this is a long comment"
          )
        SupportServiceMock.GetSupportFlow.succeeds()
        SupportServiceMock.SubmitTicket.succeeds()

        val result = addToken(underTest.submitSupportDetails())(request)

        status(result) shouldBe 303
        redirectLocation(result) shouldBe Some("/developer/new-support/confirmation")
      }

      "submit request with name, email and invalid details returns BAD_REQUEST" in new Setup {
        val request = FakeRequest()
          .withSession(sessionParams: _*)
          .withFormUrlEncodedBody(
            "fullName"     -> "Peter Smith",
            "emailAddress" -> "peter@example.com",
            "details"      -> "A+++, good como  puedo iniciar, would buy again"
          )
        SupportServiceMock.GetSupportFlow.succeeds()
        SupportServiceMock.SubmitTicket.succeeds()

        val result = addToken(underTest.submitSupportDetails())(request)

        status(result) shouldBe 400
      }

      "submit request with name, email, details and invalid team member email returns BAD_REQUEST" in new Setup {
        val request = FakeRequest()
          .withSession(sessionParams: _*)
          .withFormUrlEncodedBody(
            "fullName"               -> "Peter Smith",
            "emailAddress"           -> "peter@example.com",
            "details"                -> "Blah blah blah",
            "teamMemberEmailAddress" -> "abc"
          )
        SupportServiceMock.GetSupportFlow.succeeds()
        SupportServiceMock.SubmitTicket.succeeds()

        val result = addToken(underTest.submitSupportDetails())(request)

        status(result) shouldBe 400
      }
    }

    "invoke supportConfirmationPage" should {
      "succeed when session exists" in new Setup with IsLoggedIn {
        val requestWithSupportCookie = request.withSupportSession(underTest, cookieSigner)(sessionId)
        SupportServiceMock.GetSupportFlow.succeeds()

        val result = addToken(underTest.supportConfirmationPage())(requestWithSupportCookie)

        status(result) shouldBe OK
      }

      "succeed when session doesnt exist" in new Setup {
        val request = FakeRequest()

        val result = addToken(underTest.supportConfirmationPage())(request)

        status(result) shouldBe SEE_OTHER
      }

    }
  }
}
