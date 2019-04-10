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

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector
import controllers.Registration
import domain.{Developer, RegistrationSuccessful}
import org.mockito.BDDMockito._
import org.mockito.Matchers._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier


class RegistrationSpec extends BaseControllerSpec {

  implicit val materializer = fakeApplication.materializer
  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  var userPassword = "Password1!"

  trait Setup {
    val underTest = new Registration(
      mock[SessionService],
      mock[ThirdPartyDeveloperConnector],
      mockErrorHandler,
      mock[ApplicationConfig]
    )

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
  }


  "registration" should {
    "register with normalized firstname, lastname, email and organisation" in new Setup {
      val request = FakeRequest().withSession(sessionParams: _*).withFormUrlEncodedBody(
        ("firstname", "   first  "), // with whitespaces before and after
        ("lastname", "  last  "), // with whitespaces before and after
        ("emailaddress", "email@example.com"),
        ("password", "VALID@1q2w3e"),
        ("confirmpassword", "VALID@1q2w3e"),
        ("organisation", "org")
      )

      val requestCaptor = ArgumentCaptor.forClass(classOf[domain.Registration])
      given(underTest.connector.register(requestCaptor.capture())(any[HeaderCarrier])).willReturn(RegistrationSuccessful)

      val result = await(underTest.register()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/confirmation")

      requestCaptor.getValue.firstName shouldBe "first"
      requestCaptor.getValue.lastName shouldBe "last"
      requestCaptor.getValue.email shouldBe "email@example.com"
      requestCaptor.getValue.password shouldBe "VALID@1q2w3e"
      requestCaptor.getValue.organisation shouldBe Some("org")
    }
    "register with no organisation" in new Setup {
      val request = FakeRequest().withSession(sessionParams: _*).withFormUrlEncodedBody(
        ("firstname", "   first  "), // with whitespaces before and after
        ("lastname", "  last  "), // with whitespaces before and after
        ("emailaddress", "email@example.com"),
        ("password", "VALID@1q2w3e"),
        ("confirmpassword", "VALID@1q2w3e")
      )

      val requestCaptor = ArgumentCaptor.forClass(classOf[domain.Registration])
      given(underTest.connector.register(requestCaptor.capture())(any[HeaderCarrier])).willReturn(RegistrationSuccessful)

      await(underTest.register()(request))

      requestCaptor.getValue.organisation shouldBe None
    }
  }

  "registration verification" should {
    val code = "verificationCode"

    "redirect the user to login if their verification link matches an account" in new Setup {
      given(underTest.connector.verify(Matchers.eq(code))(any[HeaderCarrier])).willReturn(OK)
      val result = await(underTest.verify(code)(FakeRequest()))

      status(result) shouldBe OK
      bodyOf(result) should include("Email address verified")
    }

    "redirect the user to confirmation page when resending verification" in new Setup {
      val email = "john.smith@example.com"
      val newSessionParams = Seq(("email", email), sessionParams.head)
      val request = FakeRequest().withSession(newSessionParams: _*)
      given(underTest.connector.resendVerificationEmail(Matchers.eq(email))(any[HeaderCarrier])).willReturn(NO_CONTENT)
      val result = await(underTest.resendVerification()(request))

      status(result) shouldBe SEE_OTHER
    }

    "show error page when resending verification fails" in new Setup {
      val email = "john.smith@example.com"
      val newSessionParams = Seq(("email", email), sessionParams.head)
      val request = FakeRequest().withSession(newSessionParams: _*)
      given(underTest.connector.resendVerificationEmail(Matchers.eq(email))(any[HeaderCarrier])).willReturn(NOT_FOUND)
      val result = await(underTest.resendVerification()(request))

      status(result) shouldBe NOT_FOUND
    }
  }
}
