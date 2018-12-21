/*
 * Copyright 2018 HM Revenue & Customs
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

import java.util.UUID

import config.ApplicationConfig
import controllers._
import domain._
import org.mockito.BDDMockito.given
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.AuditAction.{LoginFailedDueToInvalidEmail, LoginFailedDueToInvalidPassword, LoginFailedDueToLockedAccount, LoginSucceeded}
import service.{ApplicationService, AuditAction, AuditService, SessionService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future
import scala.concurrent.Future._
import uk.gov.hmrc.http.HeaderCarrier

class UserLoginAccountSpec extends UnitSpec with MockitoSugar with WithFakeApplication with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val user = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val session = Session(UUID.randomUUID().toString, user)
  val emailFieldName: String = "emailaddress"
  val passwordFieldName: String = "password"
  val userPassword = "Password1!"

  trait Setup {
    val underTest = new UserLoginAccount {
      override val sessionService = mock[SessionService]
      override val auditService = mock[AuditService]
      override val appConfig = mock[ApplicationConfig]
      override val applicationService = mock[ApplicationService]
    }

    def mockAuthenticate(email: String, password: String, result: Future[Session]) =
      given(underTest.sessionService.authenticate(Matchers.eq(email), Matchers.eq(password))(any[HeaderCarrier])).willReturn(result)

    def mockLogout() =
      given(underTest.sessionService.destroy(Matchers.eq(session.sessionId))(any[HeaderCarrier]))
        .willReturn(Future.successful(204))

    def mockAudit(auditAction: AuditAction, result: Future[AuditResult]) =
      given(underTest.auditService.audit(Matchers.eq(auditAction), Matchers.eq(Map.empty))(any[HeaderCarrier])).willReturn(result)

    given(underTest.appConfig.isExternalTestEnvironment).willReturn(false)
    given(underTest.sessionService.authenticate(anyString(), anyString())(any[HeaderCarrier])).willReturn(failed(new InvalidCredentials))
    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
  }

  "authenticate" should {

    "return the manage Applications page when the credentials are correct" in new Setup {
      mockAuthenticate(user.email, userPassword, successful(session))
      mockAudit(LoginSucceeded, successful(AuditResult.Success))

      val request = FakeRequest().withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      val result = await(underTest.authenticate()(request))

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/developer/applications")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginSucceeded), Matchers.eq(Map("developerEmail" -> user.email, "developerFullName" -> user.displayedName)))(any[HeaderCarrier])
    }

    "return the login page when the password is incorrect" in new Setup {

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, "wrongPassword1!"))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe 401
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToInvalidPassword), Matchers.eq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }

    "return the login page when the email has not been registered" in new Setup {
      private val unregisteredEmail = "unregistered@email.test"
      mockAuthenticate(unregisteredEmail, userPassword, failed(new InvalidEmail))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, unregisteredEmail), (passwordFieldName, userPassword))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe 401
      bodyOf(result) should include("Provide a valid email or password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToInvalidEmail), Matchers.eq(Map("developerEmail" -> unregisteredEmail)))(any[HeaderCarrier])
    }

    "return to the login page when the account is unverified" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new UnverifiedAccount))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))
      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe 403
      bodyOf(result) should include("Verify your account using the email we sent. Or get us to resend the verification email")
      result.toString should include(user.email.replace("@", "%40"))
    }

    "return the login page when the account is locked" in new Setup {
      mockAuthenticate(user.email, userPassword, failed(new LockedAccount))

      val request = FakeRequest()
        .withSession(sessionParams: _*)
        .withFormUrlEncodedBody((emailFieldName, user.email), (passwordFieldName, userPassword))

      val result = await(addToken(underTest.authenticate())(request))

      status(result) shouldBe 423
      bodyOf(result) should include("You entered incorrect login details too many times you&#x27;ll now have to reset your password")
      verify(underTest.auditService, times(1)).audit(
        Matchers.eq(LoginFailedDueToLockedAccount), Matchers.eq(Map("developerEmail" -> user.email)))(any[HeaderCarrier])
    }
  }

  "accountLocked" should {
    "destroy session when locked" in new Setup {
      mockLogout()
      val request = FakeRequest().withLoggedIn(underTest)(session.sessionId)
      await(underTest.accountLocked()(request))
      verify(underTest.sessionService, atLeastOnce()).destroy(Matchers.eq(session.sessionId))(Matchers.any[HeaderCarrier])
    }
  }
}
