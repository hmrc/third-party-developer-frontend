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
import domain.models.connectors._
import domain.models.developers._
import domain.models.emailpreferences.EmailTopic._
import domain.models.emailpreferences.{EmailPreferences, TaxRegimeInterests}
import domain.{InvalidCredentials, InvalidEmail, LockedAccount, UnverifiedAccount}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class ThirdPartyDeveloperConnectorSpec extends AsyncHmrcSpec {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockHttp: HttpClient = mock[HttpClient]
    val mockPayloadEncryption: PayloadEncryption = mock[PayloadEncryption]
    val encryptedJson = new EncryptedJson(mockPayloadEncryption)
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
    val mockMetrics = new NoopConnectorMetrics()
    val encryptedString: JsString = JsString("someEncryptedStringOfData")
    val encryptedBody: JsValue = Json.toJson(SecretRequest(encryptedString.as[String]))

    when(mockAppConfig.thirdPartyDeveloperUrl).thenReturn("http://THIRD_PARTY_DEVELOPER:9000")
    when(mockPayloadEncryption.encrypt(*)(*)).thenReturn(encryptedString)

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

      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("developer")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(successful(HttpResponse(Status.CREATED,"")))

      await(connector.register(registrationToTest)) shouldBe RegistrationSuccessful

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(registrationToTest)))(*)
    }

    "fail to register a developer when the email address is already in use" in new Setup {
      val registrationToTest = Registration("john", "smith", "john.smith@example.com", "XXXYYYY")

      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("developer")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(failed(UpstreamErrorResponse("409 exception", Status.CONFLICT, Status.CONFLICT)))

      await(connector.register(registrationToTest)) shouldBe EmailAlreadyInUse

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(registrationToTest)))(*)
    }

    "successfully verify a developer" in new Setup {
      val code = "A1234"

      when(mockHttp.GET(endpoint(s"verification?code=$code"))).thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.verify(code)) shouldBe Status.OK

      verify(mockHttp).GET(endpoint(s"verification?code=$code"))
    }
  }

  "createUnregisteredUser" should {
    val email = "john.smith@example.com"

    "successfully create an unregistered user" in new Setup {
      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("unregistered-developer")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(successful(HttpResponse(Status.OK,"")))

      val result = await(connector.createUnregisteredUser(email))

      result shouldBe Status.OK
      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(UnregisteredUserCreationRequest(email))))(*)
    }

    "propagate error when the request fails" in new Setup {
      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("unregistered-developer")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(failed(UpstreamErrorResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse] {
        await(connector.createUnregisteredUser(email))
      }
    }
  }

  "fetchSession" should {
    val sessionId = "sessionId"
    val session = Session(sessionId, Developer("John", "Smith", "john.smith@example.com"), LoggedInState.LOGGED_IN)

    "return session" in new Setup {
      when(mockHttp.GET(endpoint(s"session/$sessionId"))).thenReturn(successful(HttpResponse(Status.OK, Some(Json.toJson(session)))))

      private val fetchedSession = await(connector.fetchSession(sessionId))
      fetchedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.GET(endpoint(s"session/$sessionId"))).thenReturn(failed(new NotFoundException("")))

      private val error = await(connector.fetchSession(sessionId).failed)
      error shouldBe a[SessionInvalid]
    }
  }

  "deleteSession" should {
    val sessionId = "sessionId"

    "delete the session" in new Setup {
      when(mockHttp.DELETE(endpoint(s"session/$sessionId"))).thenReturn(successful(HttpResponse(Status.NO_CONTENT,"")))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }

    "be successful when not found" in new Setup {
      when(mockHttp.DELETE(endpoint(s"session/$sessionId"))).thenReturn(failed(new NotFoundException("")))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }
  }

  "updateSessionLoggedInState" should {
    val sessionId = "sessionId"
    val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
    val session = Session(sessionId, Developer("John", "Smith", "john.smith@example.com"), LoggedInState.LOGGED_IN)

    "update session logged in state" in new Setup {
      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(Some(session)))

      private val updatedSession = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      updatedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(None))

      private val error = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest).failed)

      error shouldBe a[SessionInvalid]
    }
  }

  "Update profile" should {
    "update profile" in new Setup {
      val email = "john.smith@example.com"
      val updated = UpdateProfileRequest("First", "Last")

      when(mockHttp.POST(endpoint(s"developer/$email"), updated)).thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.updateProfile(email, updated)) shouldBe Status.OK
    }
  }

  "Resend verification" should {
    "send verification mail" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.POSTEmpty[HttpResponse](eqTo(endpoint(s"$email/resend-verification")), *)(*, *, *)).thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.resendVerificationEmail(email)) shouldBe Status.OK

      verify(mockHttp).POSTEmpty[HttpResponse](eqTo(endpoint(s"$email/resend-verification")), *)(*, *, *)
    }
  }

  "Reset password" should {
    "successfully request reset" in new Setup {
      val email = "user@example.com"
      when(mockHttp.POSTEmpty[HttpResponse](eqTo(endpoint(s"$email/password-reset-request")),*)(*, *, *)).thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.requestReset(email))

      verify(mockHttp).POSTEmpty[HttpResponse](eqTo(endpoint(s"$email/password-reset-request")), *)(*, *, *)
    }

    "successfully validate reset code" in new Setup {
      val email = "user@example.com"
      val code = "ABC123"
      when(mockHttp.GET(endpoint(s"reset-password?code=$code"))).thenReturn(successful(HttpResponse(Status.OK, responseJson = Some(Json.obj("email" -> email)))))

      await(connector.fetchEmailForResetCode(code)) shouldBe email

      verify(mockHttp).GET(endpoint(s"reset-password?code=$code"))
    }

    "successfully reset password" in new Setup {
      val passwordReset = PasswordReset("user@example.com", "newPassword")

      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("reset-password")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.reset(passwordReset))

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(passwordReset)))(*)
    }
  }

  "accountSetupQuestions" should {

    val email = "john.smith@example.com"
    val developer = Developer(email, "test", "testington", None)

    "successfully complete a developer account setup" in new Setup {
      when(mockHttp.POSTEmpty[HttpResponse](eqTo(endpoint(s"developer/account-setup/$email/complete")), *)(*, *, *)).thenReturn(successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      await(connector.completeAccountSetup(email)) shouldBe developer
    }

    "successfully update roles" in new Setup {
      private val request = AccountSetupRequest(roles = Some(Seq("aRole")), rolesOther = Some("otherRole"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/roles"), request)).thenReturn(successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      await(connector.updateRoles(email, request)) shouldBe developer
    }

    "successfully update services" in new Setup {
      private val request = AccountSetupRequest(services = Some(Seq("aService")), servicesOther = Some("otherService"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/services"), request)).thenReturn(successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      await(connector.updateServices(email, request)) shouldBe developer
    }

    "successfully update targets" in new Setup {
      private val request = AccountSetupRequest(targets = Some(Seq("aTarget")), targetsOther = Some("otherTargets"))
      when(mockHttp.PUT(endpoint(s"developer/account-setup/$email/targets"), request)).thenReturn(successful(HttpResponse(Status.OK, Some(Json.toJson(developer)))))

      await(connector.updateTargets(email, request)) shouldBe developer
    }
  }

  "change password" should {

    val changePasswordRequest = ChangePassword("email@example.com", "oldPassword123", "newPassword321")

    "throw Invalid Credentials if the response is Unauthorised" in new Setup {
      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("change-password")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(failed(UpstreamErrorResponse("Unauthorised error", Status.UNAUTHORIZED, Status.UNAUTHORIZED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[InvalidCredentials]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }

    "throw Unverified Account if the response is Forbidden" in new Setup {
      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("change-password")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(failed(UpstreamErrorResponse("Forbidden error", Status.FORBIDDEN, Status.FORBIDDEN)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[UnverifiedAccount]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }

    "throw Locked Account if the response is Locked" in new Setup {
      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("change-password")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(failed(UpstreamErrorResponse("Locked error", Status.LOCKED, Status.LOCKED)))

      await(connector.changePassword(changePasswordRequest).failed) shouldBe a[LockedAccount]

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(changePasswordRequest)))(*)
    }
  }

  "create MFA" should {

    "return the created secret" in new Setup {

      val email = "john.smith@example.com"
      val expectedSecret = "ABCDEF"

      when(mockHttp.POSTEmpty[HttpResponse](eqTo(endpoint(s"developer/$email/mfa")), *)(*, *, *)).thenReturn(successful(HttpResponse(Status.CREATED, Some(Json.obj("secret" -> expectedSecret)))))

      await(connector.createMfaSecret(email)) shouldBe expectedSecret

      verify(mockHttp).POSTEmpty[HttpResponse](eqTo(endpoint(s"developer/$email/mfa")), *)(*, *, *)
    }
  }

  "verify MFA" should {
    "return false if verification fails due to InvalidCode" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON)))
        .thenReturn(failed(new BadRequestException("Bad Request")))

      await(connector.verifyMfa(email, code)) shouldBe false
    }

    "return true if verification is successful" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON)))
        .thenReturn(successful(HttpResponse(Status.NO_CONTENT,"")))

      await(connector.verifyMfa(email, code)) shouldBe true
    }

    "throw if verification fails due to error" in new Setup {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

      when(mockHttp.POST(endpoint(s"developer/$email/mfa/verification"), verifyMfaRequest, Seq(CONTENT_TYPE -> JSON)))
        .thenReturn(failed(UpstreamErrorResponse("Internal server error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse] {
        await(connector.verifyMfa(email, code))
      }
    }
  }

  import uk.gov.hmrc.http.HttpReads.Implicits._

  "enableMFA" should {
    "return no_content if successfully enabled" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.PUT[String, Unit](eqTo(endpoint(s"developer/$email/mfa/enable")), eqTo(""), *)(*,*,*,*)).thenReturn(successful(()))

      await(connector.enableMfa(email))
    }
  }

  "removeEmailPreferences" should {
    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      val email = "john.smith@example.com"
      when(mockHttp.DELETE[Option[Unit]](eqTo(endpoint(s"developer/$email/email-preferences")), *)(*,*,*)).thenReturn(successful(Some(())))
      
      await(connector.removeEmailPreferences(email))
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      val email = "john.smith@example.com"
      when(mockHttp.DELETE[Option[Unit]](eqTo(endpoint(s"developer/$email/email-preferences")), *)(*,*,*)).thenReturn(successful(None))

      intercept[InvalidEmail] {
        await(connector.removeEmailPreferences(email))
      }

    }
  }

  "updateEmailPreferences" should {
    val email = "john.smith@example.com"
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set("API1", "API2"))), Set(BUSINESS_AND_POLICY))

    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, Option[Unit]](eqTo(endpoint(s"developer/$email/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(Some(())))
      private val result = await(connector.updateEmailPreferences(email, emailPreferences))

      result shouldBe true
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, Option[Unit]](eqTo(endpoint(s"developer/$email/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(None))

      intercept[InvalidEmail] {
        await(connector.updateEmailPreferences(email, emailPreferences))
      }
    }
  }
}
