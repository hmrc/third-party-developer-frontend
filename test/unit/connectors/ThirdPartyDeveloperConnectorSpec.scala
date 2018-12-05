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

package unit.connectors

import config.WSHttp
import connectors.ThirdPartyDeveloperConnector
import domain.Session._
import domain._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.{API, NoopMetrics}
import uk.gov.hmrc.play.test.UnitSpec
import utils.TestPayloadEncryptor
import play.api.http.Status.NO_CONTENT
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ThirdPartyDeveloperConnectorSpec extends UnitSpec with MockitoSugar with ScalaFutures with TestPayloadEncryptor {

  trait Setup {
    implicit val hc = HeaderCarrier()

    val connector = new ThirdPartyDeveloperConnector {
      val http = mock[WSHttp]
      val serviceBaseUrl = "http://THIRD_PARTY_DEVELOPER:9000"
      val payloadEncryption = TestPayloadEncryption
      val metrics = NoopMetrics
    }
    def endpoint(path: String) = s"${connector.serviceBaseUrl}/$path"
  }


  "api" should {
    "be deskpro" in new Setup {
      connector.api shouldEqual API("third-party-developer")
    }
  }

  "register" should {
    "successfully registration a developer" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")
      val encryptedBody = EncryptedJson.toSecretRequestJson(registrationToTest)

      when(connector.http.POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.successful(HttpResponse(201)))

      connector.register(registrationToTest).futureValue shouldBe RegistrationSuccessful

      verify(connector.http).POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }

    "fail to registration a developer when the email address is already in use" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      val encryptedBody = EncryptedJson.toSecretRequestJson(registrationToTest)

      when(connector.http.POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("409 exception", 409, 409)))

      connector.register(registrationToTest).futureValue shouldBe EmailAlreadyInUse

      verify(connector.http).POST(endpoint("developer"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }

    "successfully verify a developer" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")
      val code = "A1234"

      when(connector.http.GET(endpoint(s"verification?code=$code"))).
        thenReturn(Future.successful(HttpResponse(200)))

      connector.verify(code).futureValue shouldBe 200

      verify(connector.http).GET(endpoint(s"verification?code=$code"))
    }
  }

  "createSession" should {
    val email = "john.smith@example.com"
    val password = "MyPassword1"

    val loginRequest = LoginRequest(email, password)
    val encryptedBody = EncryptedJson.toSecretRequestJson(loginRequest)

    "successfully request a session to be created with an encrypted payload" in new Setup {
      val createdSession = Session("sessionId", Developer("John", "Smith", email))

      when(connector.http.POST(endpoint("session"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.successful(HttpResponse(201, Some(Json.toJson(createdSession)))))

      val session = await(connector.createSession(loginRequest))
      session shouldBe createdSession
    }

    "should throw InvalidCredentials if the response is 401" in new Setup {
      when(connector.http.POST(endpoint("session"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("401 error", 401, 401)))

      val error = await(connector.createSession(loginRequest).failed)
      error shouldBe a[InvalidCredentials]
    }

    "should throw UnverifiedAccount if the response is 403" in new Setup {
      when(connector.http.POST(endpoint("session"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("403 error", 403, 403)))

      val error = await(connector.createSession(loginRequest).failed)
      error shouldBe a[UnverifiedAccount]
    }

    "should throw LockedAccount if the response is 423" in new Setup {
      when(connector.http.POST(endpoint("session"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("423 error", 423, 423)))

      val error = await(connector.createSession(loginRequest).failed)
      error shouldBe a[LockedAccount]
    }
  }

  "fetchSession" should {
    val sessionId = "sessionId"
    val session = Session(sessionId, Developer("John", "Smith", "john.smith@example.com"))

    "return session" in new Setup {
      when(connector.http.GET(endpoint(s"session/$sessionId"))).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(session)))))

      val fetchedSession = await(connector.fetchSession(sessionId))
      fetchedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(connector.http.GET(endpoint(s"session/$sessionId"))).
        thenReturn(Future.failed(new NotFoundException("")))

      val error = await(connector.fetchSession(sessionId).failed)
      error shouldBe a[SessionInvalid]
    }
  }

  "deleteSession" should {
    val sessionId = "sessionId"

    "delete the session" in new Setup {
      when(connector.http.DELETE(endpoint(s"session/$sessionId"))).
        thenReturn(Future.successful(HttpResponse(204)))

      await(connector.deleteSession(sessionId)) shouldBe 204
    }

    "be successful when not found" in new Setup {
      when(connector.http.DELETE(endpoint(s"session/$sessionId"))).
        thenReturn(Future.failed(new NotFoundException("")))

      await(connector.deleteSession(sessionId)) shouldBe 204
    }
  }

  "Update profile" should {
    "update profile" in new Setup {
      val email = "john.smith@example.com"
      val updated = UpdateProfileRequest("First", "Last")

      when(connector.http.POST(endpoint(s"developer/$email"), updated)).
        thenReturn(Future.successful(HttpResponse(200)))

      connector.updateProfile(email, updated).futureValue shouldBe 200
    }
  }


  "Resend verification" should {
    "send verification mail" in new Setup {
      val email = "john.smith@example.com"

      when(connector.http.POSTEmpty(endpoint(s"$email/resend-verification"))).
        thenReturn(Future.successful(HttpResponse(200)))

      connector.resendVerificationEmail(email).futureValue shouldBe 200

      verify(connector.http).POSTEmpty(endpoint(s"$email/resend-verification"))
    }
  }

  "Reset password" should {
    "successfully request reset" in new Setup {
      val email = "user@example.com"
      when(connector.http.POSTEmpty(endpoint(s"$email/password-reset-request"))).thenReturn(Future.successful(HttpResponse(200)))

      connector.requestReset(email).futureValue

      verify(connector.http).POSTEmpty(endpoint(s"$email/password-reset-request"))
    }

    "successfully validate reset code" in new Setup {
      val email = "user@example.com"
      val code = "ABC123"
      when(connector.http.GET(endpoint(s"reset-password?code=$code"))).thenReturn(
        Future.successful(HttpResponse(200, responseJson = Some(Json.obj("email" -> email)))))

      connector.fetchEmailForResetCode(code).futureValue shouldBe email

      verify(connector.http).GET(endpoint(s"reset-password?code=$code"))
    }

    "successfully reset password" in new Setup {
      val passwordReset = PasswordReset("user@example.com", "newPassword")
      val encryptedBody = EncryptedJson.toSecretRequestJson(passwordReset)

      when(connector.http.POST(endpoint("reset-password"), encryptedBody, Seq("Content-Type" -> "application/json")))
        .thenReturn(Future.successful(HttpResponse(200)))

      connector.reset(passwordReset).futureValue

      verify(connector.http).POST(endpoint("reset-password"), encryptedBody, Seq("Content-Type" -> "application/json"))
    }
  }


  "checkPassword" should {
    val email = "john.smith@example.com"
    val password = "MyPassword1"

    val checkPasswordRequest = PasswordCheckRequest(email, password)
    val encryptedBody = EncryptedJson.toSecretRequestJson(checkPasswordRequest)

    "successfully return if called with an encrypted payload" in new Setup {
      val checkPassword = PasswordCheckRequest(email, password)

      when(connector.http.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.successful(HttpResponse(204)))

      await(connector.checkPassword(checkPasswordRequest)) shouldBe VerifyPasswordSuccessful
    }

    "should throw InvalidCredentials if the response is 401" in new Setup {
      when(connector.http.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("401 error", 401, 401)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[InvalidCredentials]
    }

    "should throw UnverifiedAccount if the response is 403" in new Setup {
      when(connector.http.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("403 error", 403, 403)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[UnverifiedAccount]
    }

    "should throw LockedAccount if the response is 423" in new Setup {
      when(connector.http.POST(endpoint("check-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("423 error", 423, 423)))

      await(connector.checkPassword(checkPasswordRequest).failed) shouldBe a[LockedAccount]
    }
  }

  "accountSetupQuestions" should {

    val email = "john.smith@example.com"
    val developer = Developer(email, "test", "testington", None)

    "successfully complete a developer account setup" in new Setup {
      when(connector.http.POSTEmpty(endpoint(s"developer/account-setup/$email/complete"))).
       thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(developer)))))

      connector.completeAccountSetup(email).futureValue shouldBe developer
    }

    "successfully update roles" in new Setup {
      private val request = AccountSetupRequest(roles = Some(Seq("aRole")), rolesOther = Some("otherRole"))
      when(connector.http.PUT(endpoint(s"developer/account-setup/$email/roles"), request)).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(developer)))))

      connector.updateRoles(email, request).futureValue shouldBe developer
    }

    "successfully update services" in new Setup {
      private val request = AccountSetupRequest(services = Some(Seq("aService")), servicesOther = Some("otherService"))
      when(connector.http.PUT(endpoint(s"developer/account-setup/$email/services"), request)).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(developer)))))

      connector.updateServices(email, request).futureValue shouldBe developer
    }

    "successfully update targets" in new Setup {
      private val request = AccountSetupRequest(targets = Some(Seq("aTarget")), targetsOther = Some("otherTargets"))
      when(connector.http.PUT(endpoint(s"developer/account-setup/$email/targets"), request)).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(developer)))))

      connector.updateTargets(email, request).futureValue shouldBe developer
    }
  }

  "change password" should {

    val changePasswordRequest = ChangePassword("email@example.com", "oldPassword123", "newPassword321")
    val encryptedBody = EncryptedJson.toSecretRequestJson(changePasswordRequest)

    "throw Invalid Credentials if the response is Unauthorised" in new Setup {
      when(connector.http.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Unauthorised error", Status.UNAUTHORIZED, Status.UNAUTHORIZED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[InvalidCredentials]

    }

    "throw Unverified Account if the response is Forbidden" in new Setup {
      when(connector.http.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Forbidden error", Status.FORBIDDEN, Status.FORBIDDEN)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[UnverifiedAccount]
    }

    "throw Locked Account if the response is Locked" in new Setup {
      when(connector.http.POST(endpoint("change-password"), encryptedBody, Seq("Content-Type" -> "application/json"))).
        thenReturn(Future.failed(Upstream4xxResponse("Locked error", Status.LOCKED, Status.LOCKED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[LockedAccount]
    }
  }

  "create MFA" should {

    "return the created secret" in new Setup {

      val email = "john.smith@example.com"
      val expectedSecret = "ABCDEF"

      when(connector.http.POSTEmpty(endpoint(s"developer/$email/mfa"))).
        thenReturn(Future.successful(HttpResponse(Status.CREATED, Some(Json.obj("secret" -> expectedSecret)))))

      connector.createMfaSecret(email).futureValue shouldBe expectedSecret

      verify(connector.http).POSTEmpty(endpoint(s"developer/$email/mfa"))

    }

  }

  "verify MFA" should {
    "return false if verification fails due to InvalidCode" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(connector.http.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.failed(Upstream4xxResponse("Bad request", Status.BAD_REQUEST, Status.BAD_REQUEST)))

      val result = connector.verifyMfa(email, code)

      result.futureValue shouldBe false
    }

    "return true if verification is successful" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(connector.http.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.successful(HttpResponse(Status.NO_CONTENT)))

      val result = connector.verifyMfa(email, code)

      result.futureValue shouldBe true
    }

    "throw if verification fails due to error" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest =  VerifyMfaRequest(code)

      when(connector.http.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON))).
        thenReturn(Future.failed(Upstream5xxResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse]{
        await(connector.verifyMfa(email, code))
      }
    }
  }

  "enableMFA" should {
    "return no_content if successfully enabled" in new Setup {
      val email = "john.smith@example.com"

      when(connector.http.PUT(endpoint(s"developer/:$email/mfa/enable"), "")).
        thenReturn(Future.successful(HttpResponse(NO_CONTENT)));

      val result = await(connector.enableMfa(email))

      result shouldBe NO_CONTENT
    }
  }
}
