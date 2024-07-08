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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.UpdateProfileRequest
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{LoggedInState, Session, SessionInvalid}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{InvalidCredentials, InvalidEmail, LockedAccount, UnverifiedAccount}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WireMockExtensions}

class ThirdPartyDeveloperConnectorIntegrationSpec extends BaseConnectorIntegrationSpec
    with GuiceOneAppPerSuite with DeveloperBuilder with LocalUserIdTracker with WireMockExtensions {

  private val stubConfig = Configuration(
    "microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key"                              -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val userEmail: LaxEmailAddress = "thirdpartydeveloper@example.com".toLaxEmail
    val userId: UserId             = idOf(userEmail)

    val userPassword                                                     = "password1!"
    val sessionId                                                        = "sessionId"
    val loginRequest: LoginRequest                                       = LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)
    val accessCode                                                       = "123456"
    val nonce                                                            = "ABC-123"
    val mfaId: MfaId                                                     = MfaId.random
    val accessCodeAuthenticationRequest: AccessCodeAuthenticationRequest = AccessCodeAuthenticationRequest(userEmail, accessCode, nonce, mfaId)

    val payloadEncryption: PayloadEncryption        = app.injector.instanceOf[PayloadEncryption]
    val encryptedLoginRequest: JsValue              = Json.toJson(SecretRequest(payloadEncryption.encrypt(loginRequest).as[String]))
    val encryptedTotpAuthenticationRequest: JsValue = Json.toJson(SecretRequest(payloadEncryption.encrypt(accessCodeAuthenticationRequest).as[String]))
    val underTest: ThirdPartyDeveloperConnector     = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "verify" should {
    "successfully verify a developer" in new Setup {
      val code = "A1234"

      stubFor(
        get(urlPathEqualTo("/verification"))
          .withQueryParam("code", equalTo(code))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.verify(code)) shouldBe OK
    }
  }

  "fetchSession" should {
    "return the session" in new Setup {
      stubFor(
        get(urlPathEqualTo(s"/session/$sessionId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""{
                           |  "sessionId": "$sessionId",
                           |  "loggedInState": "LOGGED_IN",
                           |  "developer": {
                           |    "userId":"$userId",
                           |    "email":"${userEmail.text}",
                           |    "firstName":"John",
                           |    "lastName": "Doe",
                           |    "emailPreferences": { "interests" : [], "topics": [] }
                           |  }
                           |}""".stripMargin)
          )
      )

      private val result = await(underTest.fetchSession(sessionId))

      result shouldBe Session(sessionId, buildDeveloper(userEmail), loggedInState = LoggedInState.LOGGED_IN)
    }

    "return Fail with session invalid when the session doesnt exist" in new Setup {
      stubFor(
        get(urlPathEqualTo(s"/session/$sessionId"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      intercept[SessionInvalid](await(underTest.fetchSession(sessionId)))
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      stubFor(
        get(urlPathEqualTo(s"/session/$sessionId"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchSession(sessionId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "deleteSession" should {
    "delete the session" in new Setup {
      stubFor(
        delete(urlPathEqualTo(s"/session/$sessionId"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )
      await(underTest.deleteSession(sessionId)) shouldBe NO_CONTENT
    }

    "be successful when not found" in new Setup {
      stubFor(
        delete(urlPathEqualTo(s"/session/$sessionId"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      await(underTest.deleteSession(sessionId)) shouldBe NO_CONTENT
    }
  }

  "updateSessionLoggedInState" should {

    "update session logged in state" in new Setup {
      val url                                                    = s"/session/$sessionId/loggedInState/LOGGED_IN"
      val updateLoggedInStateRequest: UpdateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
      val session: Session                                       = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)

      stubFor(
        put(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(session)
          )
      )
      private val updatedSession = await(underTest.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      updatedSession shouldBe session
    }

    "error with SessionInvalid if we get a 404 response" in new Setup {
      val url                                                    = s"/session/$sessionId/loggedInState/LOGGED_IN"
      val updateLoggedInStateRequest: UpdateLoggedInStateRequest = UpdateLoggedInStateRequest(LoggedInState.LOGGED_IN)
      stubFor(
        put(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[SessionInvalid] {
        await(underTest.updateSessionLoggedInState(sessionId, updateLoggedInStateRequest))
      }
    }
  }

  "updateProfile" should {

    "update profile" in new Setup {
      val updateProfileRequest: UpdateProfileRequest = UpdateProfileRequest("First", "Last")
      val url                                        = s"/developer/${userId}"

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(updateProfileRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.updateProfile(userId, updateProfileRequest)) shouldBe OK
    }
  }

  "Resend verification" should {
    "send verification mail" in new Setup {
      val email: LaxEmailAddress                                                     = "john.smith@example.com".toLaxEmail
      implicit val writes1: OWrites[ThirdPartyDeveloperConnector.FindUserIdRequest]  = Json.writes[ThirdPartyDeveloperConnector.FindUserIdRequest]
      implicit val writes2: OWrites[ThirdPartyDeveloperConnector.FindUserIdResponse] = Json.writes[ThirdPartyDeveloperConnector.FindUserIdResponse]

      stubFor(
        post(urlEqualTo("/developers/find-user-id"))
          .withJsonRequestBody(ThirdPartyDeveloperConnector.FindUserIdRequest(email))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(ThirdPartyDeveloperConnector.FindUserIdResponse(userId))
          )
      )
      stubFor(
        post(urlEqualTo(s"/$userId/resend-verification"))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.resendVerificationEmail(email)) shouldBe OK
    }
  }

  "Reset password" should {
    val email   = "user@example.com".toLaxEmail
    val request = PasswordResetRequest(email)

    "successfully request reset" in new Setup {
      stubFor(
        post(urlEqualTo("/password-reset-request"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.requestReset(email))
    }

    "forbidden response results in UnverifiedAccount exception for request reset" in new Setup {
      stubFor(
        post(urlEqualTo("/password-reset-request"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(FORBIDDEN)
          )
      )
      intercept[UnverifiedAccount] {
        await(underTest.requestReset(email))
      }
    }

    "successfully validate reset code" in new Setup {
      val code = "ABC123"

      import ThirdPartyDeveloperConnector.EmailForResetResponse
      implicit val writes: OWrites[EmailForResetResponse] = Json.writes[EmailForResetResponse]

      stubFor(
        get(urlPathEqualTo("/reset-password"))
          .withQueryParam("code", equalTo(code))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(EmailForResetResponse(email))
          )
      )
      await(underTest.fetchEmailForResetCode(code)) shouldBe email
    }

    "successfully reset password" in new Setup {
      val passwordReset: PasswordReset = PasswordReset("user@example.com".toLaxEmail, "newPassword")
      val payload: JsValue             = Json.toJson(passwordReset)
      val encryptedBody: SecretRequest = SecretRequest(payloadEncryption.encrypt(payload).as[String])

      stubFor(
        post(urlPathEqualTo("/reset-password"))
          .withJsonRequestBody(encryptedBody)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.reset(passwordReset))
    }
  }

  "accountSetupQuestions" should {
    val developer = buildDeveloper()
    val baseUrl   = s"/developer/account-setup/${developer.userId.value}"

    "successfully complete a developer account setup" in new Setup {
      stubFor(
        post(urlPathEqualTo(s"$baseUrl/complete"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(developer)
          )
      )
      await(underTest.completeAccountSetup(developer.userId)) shouldBe developer
    }

    "successfully update roles" in new Setup {
      private val request = AccountSetupRequest(roles = Some(List("aRole")), rolesOther = Some("otherRole"))
      stubFor(
        put(urlPathEqualTo(s"$baseUrl/roles"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(developer)
          )
      )
      await(underTest.updateRoles(developer.userId, request)) shouldBe developer
    }

    "successfully update services" in new Setup {
      private val request = AccountSetupRequest(services = Some(List("aService")), servicesOther = Some("otherService"))
      stubFor(
        put(urlPathEqualTo(s"$baseUrl/services"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(developer)
          )
      )
      await(underTest.updateServices(developer.userId, request)) shouldBe developer
    }

    "successfully update targets" in new Setup {
      private val request = AccountSetupRequest(targets = Some(List("aTarget")), targetsOther = Some("otherTargets"))
      stubFor(
        put(urlPathEqualTo(s"$baseUrl/targets"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(developer)
          )
      )
      await(underTest.updateTargets(developer.userId, request)) shouldBe developer
    }
  }

  "change password" should {
    val changePasswordRequest = ChangePassword("email@example.com".toLaxEmail, "oldPassword123", "newPassword321")
    val payload               = Json.toJson(changePasswordRequest)

    "throw Invalid Credentials if the response is Unauthorised" in new Setup {
      val encryptedBody: SecretRequest = SecretRequest(payloadEncryption.encrypt(payload).as[String])

      stubFor(
        post(urlPathEqualTo("/change-password"))
          .withJsonRequestBody(encryptedBody)
          .willReturn(
            aResponse()
              .withStatus(UNAUTHORIZED)
          )
      )
      await(underTest.changePassword(changePasswordRequest).failed) shouldBe a[InvalidCredentials]
    }

    "throw Unverified Account if the response is Forbidden" in new Setup {
      val encryptedBody: SecretRequest = SecretRequest(payloadEncryption.encrypt(payload).as[String])

      stubFor(
        post(urlPathEqualTo("/change-password"))
          .withJsonRequestBody(encryptedBody)
          .willReturn(
            aResponse()
              .withStatus(FORBIDDEN)
          )
      )
      await(underTest.changePassword(changePasswordRequest).failed) shouldBe a[UnverifiedAccount]
    }

    "throw Locked Account if the response is Locked" in new Setup {
      val encryptedBody: SecretRequest = SecretRequest(payloadEncryption.encrypt(payload).as[String])

      stubFor(
        post(urlPathEqualTo("/change-password"))
          .withJsonRequestBody(encryptedBody)
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
          )
      )
      await(underTest.changePassword(changePasswordRequest).failed) shouldBe a[LockedAccount]
    }
  }

  "authenticate" should {
    "return the session containing the user when the credentials are valid and MFA is disabled" in new Setup {

      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""
                           |{
                           |  "accessCodeRequired": false,
                           |  "mfaEnabled": false,
                           |  "session": {
                           |    "sessionId": "$sessionId",
                           |    "loggedInState": "LOGGED_IN",
                           |    "developer": {
                           |      "userId":"$userId",
                           |      "email":"${userEmail.text}",
                           |      "firstName":"John",
                           |      "lastName": "Doe",
                           |      "emailPreferences": { "interests" : [], "topics": [] }
                           |    }
                           |  }
                           |}""".stripMargin)
          )
      )

      val result: UserAuthenticationResponse = await(underTest.authenticate(loginRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate")).withRequestBody(equalToJson(encryptedLoginRequest.toString)))
      result shouldBe UserAuthenticationResponse(
        accessCodeRequired = false,
        mfaEnabled = false,
        session = Some(Session(sessionId, buildDeveloper(userEmail), LoggedInState.LOGGED_IN))
      )
    }

    "return the nonce when the credentials are valid and MFA is enabled" in new Setup {

      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""
                           |{
                           |  "accessCodeRequired": true,
                           |  "mfaEnabled": true,
                           |  "nonce": "$nonce"
                           |}""".stripMargin)
          )
      )

      val result: UserAuthenticationResponse = await(underTest.authenticate(loginRequest))

      verify(1, postRequestedFor(urlEqualTo("/authenticate")).withRequestBody(equalToJson(encryptedLoginRequest.toString)))
      result shouldBe UserAuthenticationResponse(accessCodeRequired = true, mfaEnabled = true, Some(nonce))
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(UNAUTHORIZED)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidCredentials](await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None))))
    }

    "throw LockedAccount exception when the account is locked" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[LockedAccount] {
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)))
      }
    }

    "throw UnverifiedAccount exception when the account is unverified" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(FORBIDDEN)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[UnverifiedAccount] {
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)))
      }
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate"))
          .withRequestBody(equalToJson(encryptedLoginRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "authenticateMfaAccessCode" should {
    "return the session containing the user when the accessCode and nonce are valid" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-mfa"))
          .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""
                           |{
                           |  "sessionId": "$sessionId",
                           |  "loggedInState": "LOGGED_IN",
                           |  "developer": {
                           |    "userId":"$userId",
                           |    "email":"${userEmail.text}",
                           |    "firstName":"John",
                           |    "lastName": "Doe",
                           |    "emailPreferences": { "interests" : [], "topics": [] }
                           |  }
                           |}""".stripMargin)
          )
      )

      val result: Session = await(underTest.authenticateMfaAccessCode(accessCodeAuthenticationRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate-mfa")).withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString)))
      result shouldBe Session(sessionId, buildDeveloper(emailAddress = userEmail), LoggedInState.LOGGED_IN)
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-mfa"))
          .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidCredentials](await(underTest.authenticateMfaAccessCode(accessCodeAuthenticationRequest)))
    }

    "throw InvalidEmail when the email is not found" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-mfa"))
          .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidEmail](await(underTest.authenticateMfaAccessCode(accessCodeAuthenticationRequest)))
    }
  }
}
