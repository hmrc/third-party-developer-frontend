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
import controllers.Profile
import domain._
import org.jsoup.Jsoup
import org.mockito.BDDMockito._
import org.mockito.Matchers.{any, eq => mockEq}
import org.mockito.Mockito.verify
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.http.Status.OK
import play.api.test.FakeRequest
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction.PasswordChangeFailedDueToInvalidCredentials
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProfileSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup {
    val underTest = new Profile(
      mock[ApplicationService],
      mock[AuditService],
      mock[SessionService],
      mock[ThirdPartyDeveloperConnector],
      mock[ErrorHandler],
      messagesApi,
      mock[ApplicationConfig]
    )

    val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
  }

  "updateProfile" should {
    "update profile with normalized firstname and lastname" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest)(sessionId)
        .withFormUrlEncodedBody(
          ("firstname", "  first  "), // with whitespaces before and after
          ("lastname", "  last  ") // with whitespaces before and after
        )

      val requestCaptor = ArgumentCaptor.forClass(classOf[UpdateProfileRequest])

      given(underTest.sessionService.fetch(Matchers.eq(sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(Session(sessionId, loggedInUser))))
      given(underTest.connector.updateProfile(Matchers.eq(loggedInUser.email), requestCaptor.capture())(any[HeaderCarrier])).willReturn(Future.successful(OK))

      val result = await(addToken(underTest.updateProfile())(request))

      status(result) shouldBe 200
      requestCaptor.getValue.firstName shouldBe "first"
      requestCaptor.getValue.lastName shouldBe "last"
    }

    "fail and send an audit event while changing the password if old password is incorrect" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest)(sessionId)
        .withSession("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      given(underTest.sessionService.fetch(Matchers.eq(sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(Session(sessionId, loggedInUser))))
      given(underTest.connector.changePassword(Matchers.eq(ChangePassword(loggedInUser.email, "oldPassword", "StrongNewPwd!2")))(any[HeaderCarrier]))
        .willReturn(Future.failed(new InvalidCredentials()))

      val result = await(addToken(underTest.updatePassword())(request))

      status(result) shouldBe 401
      verify(underTest.auditService).audit(mockEq(PasswordChangeFailedDueToInvalidCredentials(loggedInUser.email)), mockEq(null))(any[HeaderCarrier])
    }

    "Password updated should have correct page title" in new Setup {
      val request = FakeRequest()
        .withLoggedIn(underTest)(sessionId)
        .withSession("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody(
          ("currentpassword", "oldPassword"),
          ("password", "StrongNewPwd!2"),
          ("confirmpassword", "StrongNewPwd!2")
        )

      given(underTest.sessionService.fetch(Matchers.eq(sessionId))(any[HeaderCarrier])).willReturn(Future.successful(Some(Session(sessionId, loggedInUser))))
      given(underTest.connector.changePassword(Matchers.eq(ChangePassword(loggedInUser.email, "oldPassword", "StrongNewPwd!2")))(any[HeaderCarrier]))
        .willReturn(Future.successful(OK))

      val result = await(addToken(underTest.updatePassword())(request))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      dom.getElementsByClass("heading-xlarge").get(0).text shouldEqual "Password changed"

    }
  }
}
