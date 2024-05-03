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

import views.html.support._

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

class CheckCdsAccessIsRequiredControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val applyForPrivateApiAccessView = app.injector.instanceOf[ApplyForPrivateApiAccessView]
    val chooseAPrivateApiView        = app.injector.instanceOf[ChooseAPrivateApiView]
    val checkCdsAccessIsRequiredView = app.injector.instanceOf[CheckCdsAccessIsRequiredView]
    val cdsAccessIsNotRequiredView   = app.injector.instanceOf[CdsAccessIsNotRequiredView]

    val underTest = new CheckCdsAccessIsRequiredController(
      mcc,
      SupportServiceMock.aMock,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      chooseAPrivateApiView,
      checkCdsAccessIsRequiredView,
      cdsAccessIsNotRequiredView
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = "sessionId"
    val basicFlow                            = SupportFlow(sessionId, SupportData.UsingAnApi.id)

    val appropriateFlow =
      basicFlow.copy(entrySelection = SupportData.UsingAnApi.id, subSelection = Some(SupportData.PrivateApiDocumentation.id), privateApi = Some(SupportData.ChooseCDS.text))

    def shouldBeRedirectedToPreviousPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api"
    }

    def shouldBeRedirectedToApplyPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api/apply"
    }

    def shouldBeRedirectedToNotRequiredPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api/cds-access-not-required"
    }

    def shouldBeRedirectedToCheckPage(result: Future[Result]) = {
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe "/developer/new-support/api/private-api/cds-check"
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

  "CheckCdsAccessIsRequiredController" when {
    "invoke checkCdsAccessIsRequiredPage" should {
      "render the page when flow has private api of CDS present" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.page())(request)

        status(result) shouldBe OK
      }

      "render the previous page when flow has private api that is not CDS" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow.copy(privateApi = Some("I'm not CDS")))

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }

      "render the previous page when flow has no private api present" in new Setup with IsLoggedIn {
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow.copy(privateApi = None))

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }

      "render the previous page when there is no flow" in new Setup with NoSupportSessionExists {
        SupportServiceMock.GetSupportFlow.succeeds(basicFlow)

        val result = addToken(underTest.page())(request)

        shouldBeRedirectedToPreviousPage(result)
      }
    }

    "invoke submit" should {
      "submit new valid request from form with CDS" in new Setup with IsLoggedIn {
        val formRequest = request
          .withFormUrlEncodedBody(
            "confirmCdsIntegration" -> "yes"
          )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.SubmitTicket.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToApplyPage(result)
      }

      "submit new valid request from form with CDS but choosing No" in new Setup with IsLoggedIn {
        val formRequest = request
          .withFormUrlEncodedBody(
            "confirmCdsIntegration" -> "no"
          )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)
        SupportServiceMock.SubmitTicket.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToNotRequiredPage(result)
      }

      "submit invalid request returns BAD_REQUEST" in new Setup with IsLoggedIn {
        val formRequest = request
          .withFormUrlEncodedBody(
            "xyz" -> "yes"
          )
        SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

        val result = addToken(underTest.submit())(formRequest)

        redirectLocation(result) shouldBe None
        status(result) shouldBe BAD_REQUEST
      }

      "submit valid request but no session" in new Setup with NoSupportSessionExists {
        val formRequest = request
          .withFormUrlEncodedBody(
            "confirmCdsIntegration" -> "yes"
          )

        SupportServiceMock.GetSupportFlow.succeeds()

        val result = addToken(underTest.submit())(formRequest)

        shouldBeRedirectedToPreviousPage(result)
      }
    }
  }

  "invoke cdsAccessIsNotRequiredPage" should {
    "render the page" in new Setup with IsLoggedIn {
      SupportServiceMock.GetSupportFlow.succeeds(appropriateFlow)

      val result = addToken(underTest.cdsAccessIsNotRequiredPage())(request)

      status(result) shouldBe OK
    }
  }
}
