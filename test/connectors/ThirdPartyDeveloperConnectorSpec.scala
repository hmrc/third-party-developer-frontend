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

package connectors

import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector.JsonFormatters._
import connectors.ThirdPartyDeveloperConnector.UnregisteredUserCreationRequest
import domain.Session._
import domain.{UpdateLoggedInStateRequest, _}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ThirdPartyDeveloperConnectorSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockHttp: HttpClient = mock[HttpClient]
    val mockPayloadEncryption: PayloadEncryption = mock[PayloadEncryption]
    val encryptedJson = new EncryptedJson(mockPayloadEncryption)
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
    val mockMetrics = new NoopConnectorMetrics()
    val encryptedString = JsString("someEncryptedStringOfData")
    val encryptedBody: JsValue = Json.toJson(SecretRequest(encryptedString.as[String]))

    when(mockAppConfig.thirdPartyDeveloperUrl).thenReturn("http://THIRD_PARTY_DEVELOPER:9000")
    when(mockPayloadEncryption.encrypt(any[String])(any())).thenReturn(encryptedString)

    val connector = new ThirdPartyDeveloperConnector(mockHttp, encryptedJson, mockAppConfig, mockMetrics)

    def endpoint(path: String) = s"${connector.serviceBaseUrl}/$path"
  }

  "api" should {
    "be deskpro" in new Setup {
      connector.api shouldEqual API("third-party-developer")
    }
  }

  "register" should {
    "successfully register a developer" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      when(mockHttp.POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.successful(HttpResponse(Status.CREATED)))

      connector.register(registrationToTest).futureValue shouldBe RegistrationSuccessful

      verify(mockPayloadEncryption).encrypt(Json.toJson(registrationToTest))
      verify(mockHttp).POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }

    "fail to register a developer when the email address is already in use" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      when(mockHttp.POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("409 exception", Status.CONFLICT, Status.CONFLICT)))

      connector.register(registrationToTest).futureValue shouldBe EmailAlreadyInUse

      verify(mockPayloadEncryption).encrypt(Json.toJson(registrationToTest))
      verify(mockHttp).POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }

    "successfully verify a developer" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")
      val code = "A1234"

      when(mockHttp.GET(endpoint(s"verification?code=$code"))).
        thenReturn(Future.successful(HttpResponse(Status.OK)))

      connector.verify(code).futureValue shouldBe Status.OK

      verify(mockHttp).GET(endpoint(s"verification?code=$code"))
    }
  }

  "createUnregisteredUser" should {
    val email = "john.smith@example.com"

    "successfully create an unregistered user" in new Setup {
      when(mockHttp.POST(endpoint("unregistered-developer"), encryptedBody, Seq("Content-Type" -> "application/json")))
        .thenReturn(Future.successful(HttpResponse(Status.OK)))

      val result = await(connector.createUnregisteredUser(email))

      result shouldBe Status.OK
      verify(mockPayloadEncryption).encrypt(Json.toJson(UnregisteredUserCreationRequest(email)))
      verify(mockHttp).POST(endpoint("unregistered-developer"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }

    "propagate error when the request fails" in new Setup {
      when(mockHttp.POST(endpoint("unregistered-developer"), encryptedBody, Seq("Content-Type" -> "application/json")))
        .thenReturn(Future.failed(Upstream5xxResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(connector.createUnregisteredUser(email))
      }
    }
  }

  "fetchSession" should {
    val sessionId = "sessionId"
    val session = Session(sessionId, Developer("John", "Smith", "john.smith@example.com"), LoggedInState.LOGGED_IN)

    "return session" in new Setup {
      when(mockHttp.GET(endpoint(s"session/$sessionId"))).
        thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(session)))))

      private val fetchedSession = await(connector.fetchSession(sessionId))
      fetchedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.GET(endpoint(s"session/$sessionId"))).
        thenReturn(Future.failed(new NotFoundException("")))

      private val error = await(connector.fetchSession(sessionId).failed)
      error shouldBe a[SessionInvalid]
    }
  }

  "deleteSession" should {
    val sessionId = "sessionId"

    "delete the session" in new Setup {
      when(mockHttp.DELETE(endpoint(s"session/$sessionId"))).
        thenReturn(Future.successful(HttpResponse(Status.NO_CONTENT)))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }

    "be successful when not found" in new Setup {
      when(mockHttp.DELETE(endpoint(s"session/$sessionId"))).
        thenReturn(Future.failed(new NotFoundException("")))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }
  }

  "updateSessionLoggedInState" should {
    val sessionId = "sessionId"
    val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
    val session = Session(sessionId, Developer("John", "Smith", "john.smith@example.com"), LoggedInState.LOGGED_IN)

    "update session logged in state" in new Setup {
      when(mockHttp.PUT(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN"), ""))
        .thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(session)))))

      private val updatedSession = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      updatedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.PUT(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN"), ""))
        .thenReturn(Future.failed(new NotFoundException("")))

      private val error = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest).failed)

      error shouldBe a[SessionInvalid]
    }
  }

  "Update profile" should {
    "update profile" in new Setup {
      val email = "john.smith@example.com"
      val updated = UpdateProfileRequest("First", "Last")

      when(mockHttp.POST(endpoint(s"developer/$email"), updated)).
        thenReturn(Future.successful(HttpResponse(Status.OK)))

      connector.updateProfile(email, updated).futureValue shouldBe Status.OK
    }
  }

  "Resend verification" should {
    "send verification mail" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.POSTEmpty(endpoint(s"$email/resend-verification"))).
        thenReturn(Future.successful(HttpResponse(Status.OK)))

      connector.resendVerificationEmail(email).futureValue shouldBe Status.OK

      verify(mockHttp).POSTEmpty(eqTo(endpoint(s"$email/resend-verification")),any())(any(),any(),any())
    }
  }

  "Reset password" should {
    "successfully request reset" in new Setup {
      val email = "user@example.com"
      when(mockHttp.POSTEmpty(endpoint(s"$email/password-reset-request"))).thenReturn(Future.successful(HttpResponse(Status.OK)))

      connector.requestReset(email).futureValue

      verify(mockHttp).POSTEmpty(eqTo(endpoint(s"$email/password-reset-request")),any())(any(),any(),any())
    }

    "successfully validate reset code" in new Setup {
      val email = "user@example.com"
      val code = "ABC123"
      when(mockHttp.GET(endpoint(s"reset-password?code=$code"))).thenReturn(
        Future.successful(HttpResponse(Status.OK, responseJson = Some(Json.obj("email" -> email)))))

      connector.fetchEmailForResetCode(code).futureValue shouldBe email

      verify(mockHttp).GET(endpoint(s"reset-password?code=$code"))
    }

    "successfully reset password" in new Setup {
      val passwordReset = PasswordReset("user@example.com", "newPassword")

      when(mockHttp.POST(endpoint("reset-password"), encryptedBody, Seq("Content-Type" -> "application/json")))
        .thenReturn(Future.successful(HttpResponse(Status.OK)))

      connector.reset(passwordReset).futureValue

      verify(mockPayloadEncryption).encrypt(Json.toJson(passwordReset))
      verify(mockHttp).POST(endpoint("reset-password"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }
  }


  "checkPassword" should {
    val email = "john.smith@example.com"
    val password = "MyPassword1"

    val checkPasswordRequest = PasswordCheckRequest(email, password)

    "successfully return if called with an encrypted payload" in new Setup {
      val checkPassword = PasswordCheckRequest(email, password)

      when(mockHttp.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.successful(HttpResponse(Status.NO_CONTENT)))

      await(connector.checkPassword(checkPasswordRequest)) shouldBe VerifyPasswordSuccessful
      verify(mockPayloadEncryption).encrypt(Json.toJson(checkPasswordRequest))
    }

    "should throw InvalidCredentials if the response is 401" in new Setup {
      when(mockHttp.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("401 error", Status.UNAUTHORIZED, Status.UNAUTHORIZED)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[InvalidCredentials]
      verify(mockPayloadEncryption).encrypt(Json.toJson(checkPasswordRequest))
    }

    "should throw UnverifiedAccount if the response is 403" in new Setup {
      when(mockHttp.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("403 error", Status.FORBIDDEN, Status.FORBIDDEN)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[UnverifiedAccount]
      verify(mockPayloadEncryption).encrypt(Json.toJson(checkPasswordRequest))
    }

    "should throw LockedAccount if the response is 423" in new Setup {
      when(mockHttp.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("423 error", Status.LOCKED, Status.LOCKED)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[LockedAccount]
      verify(mockPayloadEncryption).encrypt(Json.toJson(checkPasswordRequest))
    }
  }

  "accountSetupQuestions" should {

    val email = "john.smith@example.com"
    val developer = Developer(email, "test", "testington", None)

    "successfully complete a developer account setup" in new Setup {
      when(mockHttp.POSTEmpty(endpoint(s"developer/account-setup/$email/complete"))).
        thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      connector.completeAccountSetup(email).futureValue shouldBe developer
    }

    "successfully update roles" in new Setup {
      private val request = AccountSetupRequest(roles = Some(Seq("aRole")), rolesOther = Some("otherRole"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/roles"), request)).
        thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      connector.updateRoles(email, request).futureValue shouldBe developer
    }

    "successfully update services" in new Setup {
      private val request = AccountSetupRequest(services = Some(Seq("aService")), servicesOther = Some("otherService"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/services"), request)).
        thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      connector.updateServices(email, request).futureValue shouldBe developer
    }

    "successfully update targets" in new Setup {
      private val request = AccountSetupRequest(targets = Some(Seq("aTarget")), targetsOther = Some("otherTargets"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/targets"), request)).
        thenReturn(Future.successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      connector.updateTargets(email, request).futureValue shouldBe developer
    }
  }

  "change password" should {

    val changePasswordRequest = ChangePassword("email@example.com", "oldPassword123", "newPassword321")

    "throw Invalid Credentials if the response is Unauthorised" in new Setup {
      when(mockHttp.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Unauthorised error", Status.UNAUTHORIZED, Status.UNAUTHORIZED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[InvalidCredentials]

      verify(mockPayloadEncryption).encrypt(Json.toJson(changePasswordRequest))
    }

    "throw Unverified Account if the response is Forbidden" in new Setup {
      when(mockHttp.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Forbidden error", Status.FORBIDDEN, Status.FORBIDDEN)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[UnverifiedAccount]

      verify(mockPayloadEncryption).encrypt(Json.toJson(changePasswordRequest))
    }

    "throw Locked Account if the response is Locked" in new Setup {
      when(mockHttp.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Locked error", Status.LOCKED, Status.LOCKED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[LockedAccount]

      verify(mockPayloadEncryption).encrypt(Json.toJson(changePasswordRequest))
    }
  }

  "create MFA" should {

    "return the created secret" in new Setup {

      val email = "john.smith@example.com"
      val expectedSecret = "ABCDEF"

      when(mockHttp.POSTEmpty(endpoint(s"developer/$email/mfa"))).
        thenReturn(Future.successful(HttpResponse(Status.CREATED, Some(Json.obj("secret" -> expectedSecret)))))

      connector.createMfaSecret(email).futureValue shouldBe expectedSecret

      verify(mockHttp).POSTEmpty(eqTo(endpoint(s"developer/$email/mfa")), any())(any(),any(),any())

    }

  }

  "verify MFA" should {
    "return false if verification fails due to InvalidCode" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.failed(new BadRequestException("Bad Request")))

      private val result = connector.verifyMfa(email, code)

      result.futureValue shouldBe false
    }

    "return true if verification is successful" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.successful(HttpResponse(Status.NO_CONTENT)))

      private val result = connector.verifyMfa(email, code)

      result.futureValue shouldBe true
    }

    "throw if verification fails due to error" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.failed(Upstream5xxResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(connector.verifyMfa(email, code))
      }
    }
  }

  "enableMFA" should {
    "return no_content if successfully enabled" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.PUT(endpoint(s"developer/$email/mfa/enable"), "")).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      private val result = await(connector.enableMfa(email))

      result shouldBe NO_CONTENT
    }
  }
}
