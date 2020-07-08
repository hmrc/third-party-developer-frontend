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
import domain.{Developer, LoggedInState, Session, TicketCreated}
import mocks.service.SessionServiceMock
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito._
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.DeskproService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.{SupportEnquiryView, SupportThankyouView}

import scala.concurrent.ExecutionContext.Implicits.global

class SupportSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends SessionServiceMock {
    val supportEnquiryView = app.injector.instanceOf[SupportEnquiryView]
    val supportThankYouView = app.injector.instanceOf[SupportThankyouView]

    val underTest = new Support(
      mock[DeskproService],
      sessionServiceMock,
      mock[ErrorHandler],
      mcc,
      cookieSigner,
      supportEnquiryView,
      supportThankYouView
      )

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")

    val sessionId = "sessionId"
  }

  "suppport" should {

    "support form is prepopulated when user logged in" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.LOGGED_IN))

      val result: Result = await(addToken(underTest.raiseSupportEnquiry())(request))

      assertFullNameAndEmail(result,"John Doe", "thirdpartydeveloper@example.com")
    }

    "support form fields are blank when not logged in" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)

      fetchSessionByIdReturnsNone(sessionId)

      val result = await(addToken(underTest.raiseSupportEnquiry())(request))
      assertFullNameAndEmail(result, "", "")
    }

    "support form fields are blank when part logged in enabling MFA" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession(sessionParams: _*)

      fetchSessionByIdReturns(sessionId, Session(sessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA))

      val result = await(addToken(underTest.raiseSupportEnquiry())(request))

      assertFullNameAndEmail(result, "","")
    }

    "submit request with name, email & comments from form" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(
          "fullname" -> "Peter Smith",
          "emailaddress" -> "peter@example.com",
          "comments" -> "A+++, good seller, would buy again")

      val captor: ArgumentCaptor[SupportEnquiryForm] = ArgumentCaptor.forClass(classOf[SupportEnquiryForm])
      given(underTest.deskproService.submitSupportEnquiry(captor.capture())(any[Request[AnyRef]], any[HeaderCarrier])).willReturn(TicketCreated)

      val result = await(addToken(underTest.submitSupportEnquiry())(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/support/submitted")

      captor.getValue.fullname shouldBe "Peter Smith"
      captor.getValue.email shouldBe "peter@example.com"
      captor.getValue.comments shouldBe "A+++, good seller, would buy again"
    }

    "submit request with incomplete form results in BAD_REQUEST" in new Setup {
      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody(
          "fullname" -> "Peter Smith",
          "comments" -> "A+++, good seller, would buy again")

     val result = await(addToken(underTest.submitSupportEnquiry())(request))
      status(result) shouldBe 400
    }
  }

  private def assertFullNameAndEmail(result: Result, fullName: String, email: String): Any = {
    status(result) shouldBe 200
    val dom = Jsoup.parse(bodyOf(result))

    dom.getElementsByAttributeValue("name", "fullname").attr("value") shouldEqual fullName
    dom.getElementsByAttributeValue("name", "emailaddress").attr("value") shouldEqual email
  }
}
