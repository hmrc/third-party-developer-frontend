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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.jsoup.Jsoup
import views.html.support.SupportEnquiryInitialChoiceView
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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SupportData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{SessionServiceMock, SupportServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.DeskproService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SupportEnquiryControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock with SupportServiceMockModule {
    val supportEnquiryInitialChoiceView = app.injector.instanceOf[SupportEnquiryInitialChoiceView]
    val supportEnquiryView              = app.injector.instanceOf[SupportEnquiryView]
    val supportThankyouView             = app.injector.instanceOf[SupportThankyouView]

    val underTest = new SupportEnquiryController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler],
      mock[DeskproService],
      SupportServiceMock.aMock,
      supportEnquiryInitialChoiceView,
      supportEnquiryView,
      supportThankyouView
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildDeveloper(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = "sessionId"
  }

  trait IsLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
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
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA))
  }

  "SupportEnquiryController" when {
    "invoking supportEnquiryPage for new support" should {
      "render the new support enquiry initial choice page when a user is logged in" in new Setup with IsLoggedIn {
        val result = addToken(underTest.supportEnquiryPage(true))(request)

        status(result) shouldBe OK
        val dom = Jsoup.parse(contentAsString(result))

        dom.getElementById(SupportData.FindingAnApi.id).attr("value") shouldEqual SupportData.FindingAnApi.id
        dom.getElementById(SupportData.UsingAnApi.id).attr("value") shouldEqual SupportData.UsingAnApi.id
        dom.getElementById(SupportData.SigningIn.id).attr("value") shouldEqual SupportData.SigningIn.id
        dom.getElementById(SupportData.SettingUpApplication.id).attr("value") shouldEqual SupportData.SettingUpApplication.id
      }

      "render the new support enquiry initial choice page when a user is not logged in" in new Setup with NotLoggedIn {
        val result = addToken(underTest.supportEnquiryPage(true))(request)

        status(result) shouldBe OK
        val dom = Jsoup.parse(contentAsString(result))

        dom.getElementById(SupportData.FindingAnApi.id).attr("value") shouldEqual SupportData.FindingAnApi.id
        dom.getElementById(SupportData.UsingAnApi.id).attr("value") shouldEqual SupportData.UsingAnApi.id
        dom.getElementById(SupportData.SigningIn.id).attr("value") shouldEqual SupportData.SigningIn.id
        dom.getElementById(SupportData.SettingUpApplication.id).attr("value") shouldEqual SupportData.SettingUpApplication.id
      }

    }

    "invoking supportEnquiryPage for old support" should {
      "support form is prepopulated when user logged in" in new Setup with IsLoggedIn {
        val result = addToken(underTest.supportEnquiryPage(false))(request)

        assertFullNameAndEmail(result, "John Doe", "thirdpartydeveloper@example.com")
      }

      "support form fields are blank when not logged in" in new Setup with NotLoggedIn {
        val result = addToken(underTest.supportEnquiryPage(false))(request)
        assertFullNameAndEmail(result, "", "")
      }

      "support form fields are blank when part logged in enabling MFA" in new Setup with IsPartLoggedInEnablingMFA {
        val result = addToken(underTest.supportEnquiryPage(false))(request)

        assertFullNameAndEmail(result, "", "")
      }
    }

    "invovking submitInitialChoice" should {
      "redirect to the new help with using an api page when the 'Using an API' option is selected" in new Setup {
        val request = FakeRequest()
          .withFormUrlEncodedBody("initialChoice" -> SupportData.UsingAnApi.id)
          .withLoggedIn(underTest, implicitly)(sessionId)
          .withSession(sessionParams: _*)

        fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

        SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

        val result = addToken(underTest.submitInitialChoice())(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/new-support/api/choose-api")
      }

      "redirect to the generic support details page when any other option is selected" in new Setup {
        val request = FakeRequest()
          .withFormUrlEncodedBody("initialChoice" -> SupportData.FindingAnApi.id)
          .withLoggedIn(underTest, implicitly)(sessionId)
          .withSession(sessionParams: _*)

        fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

        SupportServiceMock.FetchAllPublicApis.succeeds(List(ApiDefinitionData.apiDefinition))

        val result = addToken(underTest.submitInitialChoice())(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/new-support/api/details")
      }
    }

    "invoking the submitSupportEnquiry" should {
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

    "invoking thankyouPage" should {
      "render the thankyou page" in new Setup with IsLoggedIn {
        val result = addToken(underTest.thankyouPage())(request)

        val dom = Jsoup.parse(contentAsString(result))

        dom.title shouldEqual "Thank you - HMRC Developer Hub - GOV.UK"
      }
    }
  }

  private def assertFullNameAndEmail(result: Future[Result], fullName: String, email: String): Any = {
    status(result) shouldBe 200
    val dom = Jsoup.parse(contentAsString(result))

    dom.getElementsByAttributeValue("name", "fullname").attr("value") shouldEqual fullName
    dom.getElementsByAttributeValue("name", "emailaddress").attr("value") shouldEqual email
  }
}
