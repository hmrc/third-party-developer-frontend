/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.jsoup.Jsoup
import views.html.support.{ApiSupportPageView, LandingPageView, SupportPageConfirmationView, SupportPageDetailView}
import views.html.{SupportEnquiryView, SupportThankyouView}

import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinitionData
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SupportSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val supportEnquiryView          = app.injector.instanceOf[SupportEnquiryView]
    val supportThankYouView         = app.injector.instanceOf[SupportThankyouView]
    val supportLandingPageView      = app.injector.instanceOf[LandingPageView]
    val apiSupportPageView          = app.injector.instanceOf[ApiSupportPageView]
    val supportPageDetailView       = app.injector.instanceOf[SupportPageDetailView]
    val supportPageConfirmationView = app.injector.instanceOf[SupportPageConfirmationView]

    val underTest = new Support(
      mock[DeskproService],
      sessionServiceMock,
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      supportEnquiryView,
      supportThankYouView,
      supportLandingPageView,
      apiSupportPageView,
      supportPageDetailView,
      supportPageConfirmationView,
      SupportServiceMock.aMock
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = "sessionId"
  }

  "support" should {
    "render the new support landing page when a user is logged in" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.raiseSupportEnquiry(true))(request)

      status(result) shouldBe OK
      val dom = Jsoup.parse(contentAsString(result))

      dom.getElementById("using-an-api").attr("value") shouldEqual "api"
      dom.getElementById("signing-into-account").attr("value") shouldEqual "account"
      dom.getElementById("setting-up-application").attr("value") shouldEqual "application"
    }

    "redirect to the new api support page when the api option is selected" in new Setup {
      val request = FakeRequest()
        .withFormUrlEncodedBody("helpWithChoice" -> "api")
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.chooseSupportOptionAction())(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/new-support/api/choose-api")
    }

    "render the new api support select api page" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.apiSupportPage)(request)

      status(result) shouldBe OK
    }

    "redirect to the new api support details page when an api is selected" in new Setup {
      val apiName = "test-api"
      val request = FakeRequest()
        .withFormUrlEncodedBody(
          "helpWithApiChoice" -> "api-call",
          "apiName"           -> apiName
        )
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))
      SupportServiceMock.UpdateApiChoice.succeeds()
      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.apiSupportAction)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/new-support/api/details")
    }

    "return a bad request when no api is selected" in new Setup {
      val request = FakeRequest()
        .withFormUrlEncodedBody(
          "helpWithApiChoice" -> "api-call"
        )
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))
      SupportServiceMock.UpdateApiChoice.fails()
      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.apiSupportAction)(request)

      status(result) shouldBe BAD_REQUEST
    }

    "render the new api support details page" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      SupportServiceMock.GetSupportFlow.succeeds()

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.supportDetailsPage())(request)

      status(result) shouldBe OK
    }

    "render the new account support page when the option is selected" in new Setup {
      val request = FakeRequest()
        .withFormUrlEncodedBody("helpWithChoice" -> "account")
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.chooseSupportOptionAction())(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/new-support/api/details")
    }

    "render the new application support page when the option is selected" in new Setup {
      val request = FakeRequest()
        .withFormUrlEncodedBody("helpWithChoice" -> "application")
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.chooseSupportOptionAction())(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/new-support/api/details")
    }

    "support form is prepopulated when user logged in" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result = addToken(underTest.raiseSupportEnquiry(false))(request)

      assertFullNameAndEmail(result, "John Doe", "thirdpartydeveloper@example.com")
    }

    "support form fields are blank when not logged in" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)

      fetchSessionByIdReturnsNone(sessionId)

      val result = addToken(underTest.raiseSupportEnquiry(false))(request)
      assertFullNameAndEmail(result, "", "")
    }

    "support form fields are blank when part logged in enabling MFA" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA))

      val result = addToken(underTest.raiseSupportEnquiry(false))(request)

      assertFullNameAndEmail(result, "", "")
    }

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

      val result = addToken(underTest.supportDetailsAction())(request)

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

      val result = addToken(underTest.supportDetailsAction())(request)

      status(result) shouldBe 400
    }

    "submit request with name, email & comments from form" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(
          "fullname"     -> "Peter Smith",
          "emailaddress" -> "peter@example.com",
          "comments"     -> "A+++, good seller, would buy again"
        )

      when(underTest.deskproService.submitSupportEnquiry(*[Option[UserId]], *)(any[Request[AnyRef]], *)).thenReturn(successful(TicketCreated))

      val result = addToken(underTest.submitSupportEnquiry())(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/support/submitted")
    }

    "submit request with name, email and invalid comments returns BAD_REQUEST" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(
          "fullname"     -> "Peter Smith",
          "emailaddress" -> "peter@example.com",
          "comments"     -> "A+++, good como  puedo iniciar, would buy again"
        )

      when(underTest.deskproService.submitSupportEnquiry(*[Option[UserId]], *)(any[Request[AnyRef]], *)).thenReturn(successful(TicketCreated))

      val result = addToken(underTest.submitSupportEnquiry())(request)

      status(result) shouldBe 400

    }

    "submit request with incomplete form results in BAD_REQUEST" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(
          "fullname" -> "Peter Smith",
          "comments" -> "A+++, good seller, would buy again"
        )

      val result = addToken(underTest.submitSupportEnquiry())(request)

      status(result) shouldBe 400
    }
  }

  private def assertFullNameAndEmail(result: Future[Result], fullName: String, email: String): Any = {
    status(result) shouldBe 200
    val dom = Jsoup.parse(contentAsString(result))

    dom.getElementsByAttributeValue("name", "fullname").attr("value") shouldEqual fullName
    dom.getElementsByAttributeValue("name", "emailaddress").attr("value") shouldEqual email
  }
}
