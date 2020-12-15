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

import builder.DeveloperBuilder
import config.ApplicationConfig
import connectors.ThirdPartyDeveloperConnector.JsonFormatters._
import connectors.ThirdPartyDeveloperConnector.UnregisteredUserCreationRequest
import domain.models.connectors._
import domain.models.developers._
import domain.models.emailpreferences.EmailTopic._
import domain.models.emailpreferences.{EmailPreferences, TaxRegimeInterests}
import domain.{InvalidCredentials, InvalidEmail, LockedAccount, UnverifiedAccount}
import play.api.http.Status._
import play.api.http.Status
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}
import connectors.ThirdPartyDeveloperConnector.CreateMfaResponse

class ThirdPartyDeveloperConnectorSpec extends AsyncHmrcSpec with CommonResponseHandlers { 

  trait Setup extends DeveloperBuilder {

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

      when(mockHttp.GET[ErrorOr[HttpResponse]](eqTo(endpoint(s"verification")), eqTo(Seq("code" -> code)))(*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.verify(code)) shouldBe Status.OK

      verify(mockHttp).GET[ErrorOr[HttpResponse]](eqTo(endpoint(s"verification")), eqTo(Seq("code" -> code)))(*,*,*)  
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

      "return session" in new Setup {
      val session = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)

      when(mockHttp.GET[Option[Session]](eqTo(endpoint(s"session/$sessionId")))(*,*,*))
        .thenReturn(successful(Some(session)))

      private val fetchedSession = await(connector.fetchSession(sessionId))
      fetchedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      when(mockHttp.GET[Option[Session]](eqTo(endpoint(s"session/$sessionId")))(*,*,*))
      .thenReturn(successful(None))

      intercept[SessionInvalid]{
        await(connector.fetchSession(sessionId))
      }
    }
  }

  "deleteSession" should {
    val sessionId = "sessionId"

    "delete the session" in new Setup {
      when(mockHttp.DELETE[ErrorOr[HttpResponse]](eqTo(endpoint(s"session/$sessionId")),*)(*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.NO_CONTENT,""))))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }

    "be successful when not found" in new Setup {
      when(mockHttp.DELETE[ErrorOr[HttpResponse]](eqTo(endpoint(s"session/$sessionId")),*)(*,*,*))
      .thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      await(connector.deleteSession(sessionId)) shouldBe Status.NO_CONTENT
    }
  }

  "updateSessionLoggedInState" should {
    val sessionId = "sessionId"

    "update session logged in state" in new Setup {
      val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
      val session = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)

      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(Some(session)))

      private val updatedSession = await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      updatedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      val updateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)

      when(mockHttp.PUT[String, Option[Session]](eqTo(endpoint(s"session/$sessionId/loggedInState/LOGGED_IN")), eqTo(""), *)(*,*,*,*))
        .thenReturn(successful(None))

      intercept[SessionInvalid]{
        await(connector.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      }
    }
  }

  "Update profile" should {
    "update profile" in new Setup {
      val email = "john.smith@example.com"
      val updated = UpdateProfileRequest("First", "Last")

      when(mockHttp.POST[UpdateProfileRequest, ErrorOr[HttpResponse]](eqTo(endpoint(s"developer/$email")), eqTo(updated), *)(*,*,*,*))
      .thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.updateProfile(email, updated)) shouldBe Status.OK
    }
  }

  "Resend verification" should {
    "send verification mail" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.POSTEmpty[ErrorOr[HttpResponse]](eqTo(endpoint(s"$email/resend-verification")), *)(*, *, *)).thenReturn(successful(Right(HttpResponse(Status.OK,""))))

      await(connector.resendVerificationEmail(email)) shouldBe Status.OK

      verify(mockHttp).POSTEmpty[ErrorOr[HttpResponse]](eqTo(endpoint(s"$email/resend-verification")), *)(*, *, *)
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
      import ThirdPartyDeveloperConnector.EmailForResetResponse

      when(mockHttp.GET[ErrorOr[EmailForResetResponse]](eqTo(endpoint(s"reset-password?code=$code")))(*,*,*))
      .thenReturn(successful(Right(EmailForResetResponse(email))))

      await(connector.fetchEmailForResetCode(code)) shouldBe email

      verify(mockHttp).GET[ErrorOr[EmailForResetResponse]](eqTo(endpoint(s"reset-password?code=$code")))(*,*,*)
    }

    "successfully reset password" in new Setup {
      val passwordReset = PasswordReset("user@example.com", "newPassword")

      when[Future[HttpResponse]](mockHttp.POST(eqTo(endpoint("reset-password")), eqTo(encryptedBody), eqTo(Seq("Content-Type" -> "application/json")))(*, *, *, *))
        .thenReturn(successful(HttpResponse(Status.OK,"")))

      await(connector.reset(passwordReset))

      verify(mockPayloadEncryption).encrypt(eqTo(Json.toJson(passwordReset)))(*)
    }
  }

  // TODO - remove this to integration testing
  "accountSetupQuestions" should {
    val email = "john.smith@example.com"

    "successfully complete a developer account setup" in new Setup {
      val aDeveloper = buildDeveloper(emailAddress = email)
  
      when(mockHttp.POSTEmpty[Developer](eqTo(endpoint(s"developer/account-setup/$email/complete")), *)(*, *, *))
      .thenReturn(successful(aDeveloper))

      await(connector.completeAccountSetup(email)) shouldBe aDeveloper
    }

    "successfully update roles" in new Setup {
      val developer = buildDeveloper(emailAddress = email)

      private val request = AccountSetupRequest(roles = Some(Seq("aRole")), rolesOther = Some("otherRole"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/$email/roles")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

      await(connector.updateRoles(email, request)) shouldBe developer
    }

    "successfully update services" in new Setup {
      val developer = buildDeveloper(emailAddress = email)

      private val request = AccountSetupRequest(services = Some(Seq("aService")), servicesOther = Some("otherService"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/$email/services")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

      await(connector.updateServices(email, request)) shouldBe developer
    }

    "successfully update targets" in new Setup {
      val developer = buildDeveloper(emailAddress = email)
      
      private val request = AccountSetupRequest(targets = Some(Seq("aTarget")), targetsOther = Some("otherTargets"))
      when(mockHttp.PUT[AccountSetupRequest,Developer](eqTo(endpoint(s"developer/account-setup/$email/targets")), eqTo(request),*)(*,*,*,*)).thenReturn(successful(developer))

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

      when(mockHttp.POSTEmpty[CreateMfaResponse](eqTo(endpoint(s"developer/$email/mfa")), *)(*, *, *)).thenReturn(successful(CreateMfaResponse(expectedSecret)))

      await(connector.createMfaSecret(email)) shouldBe expectedSecret

      verify(mockHttp).POSTEmpty[HttpResponse](eqTo(endpoint(s"developer/$email/mfa")), *)(*, *, *)
    }
  }

  "verify MFA" should {
      val email = "john.smith@example.com"
      val code = "12341234"
      val verifyMfaRequest = VerifyMfaRequest(code)

    "return false if verification fails due to InvalidCode" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/$email/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Left(UpstreamErrorResponse("",BAD_REQUEST))))

      await(connector.verifyMfa(email, code)) shouldBe false
    }

    "return true if verification is successful" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/$email/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Right(())))

      await(connector.verifyMfa(email, code)) shouldBe true
    }

    "throw if verification fails due to error" in new Setup {
      when(mockHttp.POST[VerifyMfaRequest, ErrorOrUnit](eqTo(endpoint(s"developer/$email/mfa/verification")), eqTo(verifyMfaRequest), *)(*,*,*,*))
        .thenReturn(successful(Left(UpstreamErrorResponse("Internal server error", Status.INTERNAL_SERVER_ERROR))))

      intercept[UpstreamErrorResponse] {
        await(connector.verifyMfa(email, code))
      }
    }
  }

  "enableMFA" should {
    "return no_content if successfully enabled" in new Setup {
      val email = "john.smith@example.com"

      when(mockHttp.PUT[String, ErrorOrUnit](eqTo(endpoint(s"developer/$email/mfa/enable")), eqTo(""), *)(*,*,*,*)).thenReturn(successful(Right(())))

      await(connector.enableMfa(email))
    }
  }

  "removeEmailPreferences" should {
    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      val email = "john.smith@example.com"
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/$email/email-preferences")), *)(*,*,*)).thenReturn(successful(Right(())))
      
      await(connector.removeEmailPreferences(email))
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      val email = "john.smith@example.com"
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/$email/email-preferences")), *)(*,*,*)).thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      intercept[InvalidEmail] {
        await(connector.removeEmailPreferences(email))
      }
    }

    "throw UpstreamErrorResponse exception for other issues with TPD" in new Setup {
      val email = "john.smith@example.com"
      when(mockHttp.DELETE[ErrorOrUnit](eqTo(endpoint(s"developer/$email/email-preferences")), *)(*,*,*)).thenReturn(successful(Left(UpstreamErrorResponse("",INTERNAL_SERVER_ERROR))))

      intercept[UpstreamErrorResponse] {
        await(connector.removeEmailPreferences(email))
      }
    }
  }

  "updateEmailPreferences" should {
    val email = "john.smith@example.com"
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set("API1", "API2"))), Set(BUSINESS_AND_POLICY))

    "return true when connector receives NO-CONTENT in response from TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, ErrorOrUnit](eqTo(endpoint(s"developer/$email/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(Right(())))
      private val result = await(connector.updateEmailPreferences(email, emailPreferences))

      result shouldBe true
    }

    "throw InvalidEmail exception if email address not found in TPD" in new Setup {
      when(mockHttp.PUT[EmailPreferences, ErrorOrUnit](eqTo(endpoint(s"developer/$email/email-preferences")), eqTo(emailPreferences), *)(*, *, *, *))
        .thenReturn(successful(Left(UpstreamErrorResponse("",NOT_FOUND))))

      intercept[InvalidEmail] {
        await(connector.updateEmailPreferences(email, emailPreferences))
      }
    }
  }
}
