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
import com.github.tomakehurst.wiremock.client.WireMock._
import domain.models.connectors.{LoginRequest, TotpAuthenticationRequest, UserAuthenticationResponse}
import domain.models.developers.{LoggedInState, Session, SessionInvalid}
import domain.{InvalidCredentials, InvalidEmail, LockedAccount, UnverifiedAccount}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.UserIdTracker

class ThirdPartyDeveloperConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration(
    "Test.microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key" -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup extends DeveloperBuilder {
    import utils.UserIdTracker._
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val userEmail = "thirdpartydeveloper@example.com"
    val userId = idOf(userEmail)

    val userPassword = "password1!"
    val sessionId = "sessionId"
    val loginRequest = LoginRequest(userEmail, userPassword, mfaMandatedForUser = false)
    val totp = "123456"
    val nonce = "ABC-123"
    val totpAuthenticationRequest = TotpAuthenticationRequest(userEmail, totp, nonce)

    val payloadEncryption: PayloadEncryption = app.injector.instanceOf[PayloadEncryption]
    val encryptedLoginRequest: JsValue = Json.toJson(SecretRequest(payloadEncryption.encrypt(loginRequest).as[String]))
    val encryptedTotpAuthenticationRequest: JsValue = Json.toJson(SecretRequest(payloadEncryption.encrypt(totpAuthenticationRequest).as[String]))
    val underTest: ThirdPartyDeveloperConnector = app.injector.instanceOf[ThirdPartyDeveloperConnector]
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
                 |    "userId":"${userId.value}",
                 |    "email":"$userEmail",
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

  "removeMfa" should {
    "return OK on successful removal" in new Setup {
      val email = "test.user@example.com"
      stubFor(post(urlPathEqualTo(s"/developer/${userId.value}/mfa/remove")).willReturn(aResponse().withStatus(OK)))

      await(underTest.removeMfa(userId, email))
    }

    "throw UpstreamErrorResponse with status of 404 if user not found" in new Setup {
      val email = "invalid.user@example.com"
      stubFor(post(urlPathEqualTo(s"/developer/${userId.value}/mfa/remove")).willReturn(aResponse().withStatus(NOT_FOUND)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfa(userId, email))).statusCode shouldBe NOT_FOUND
    }

    "throw UpstreamErrorResponse with status of 500 if it failed to remove MFA" in new Setup {
      val email = "test.user@example.com"
      stubFor(post(urlPathEqualTo(s"/developer/${userId.value}/mfa/remove")).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfa(userId, email))).statusCode shouldBe INTERNAL_SERVER_ERROR
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
                 |  "session": {
                 |    "sessionId": "$sessionId",
                 |    "loggedInState": "LOGGED_IN",
                 |    "developer": {
                 |      "userId":"${userId.value}",
                 |      "email":"$userEmail",
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
      result shouldBe UserAuthenticationResponse(accessCodeRequired = false, session = Some(Session(sessionId, buildDeveloper(userEmail), LoggedInState.LOGGED_IN)))
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
                 |  "nonce": "$nonce"
                 |}""".stripMargin)
          )
      )

      val result: UserAuthenticationResponse = await(underTest.authenticate(loginRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate")).withRequestBody(equalToJson(encryptedLoginRequest.toString)))
      result shouldBe UserAuthenticationResponse(accessCodeRequired = true, Some(nonce))
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

      intercept[InvalidCredentials](await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false))))
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
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false)))
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
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false)))
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
        await(underTest.authenticate(LoginRequest(userEmail, userPassword, mfaMandatedForUser = false)))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "authenticateTotp" should {

    "return the session containing the user when the TOTP and nonce are valid" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-totp"))
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
                 |    "userId":"${userId.value}",
                 |    "email":"$userEmail",
                 |    "firstName":"John",
                 |    "lastName": "Doe",
                 |    "emailPreferences": { "interests" : [], "topics": [] }
                 |  }
                 |}""".stripMargin)
          )
      )

      val result: Session = await(underTest.authenticateTotp(totpAuthenticationRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate-totp")).withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString)))
      result shouldBe Session(sessionId, buildDeveloper(emailAddress = userEmail), LoggedInState.LOGGED_IN)
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-totp"))
          .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidCredentials](await(underTest.authenticateTotp(totpAuthenticationRequest)))
    }

    "throw InvalidEmail when the email is not found" in new Setup {
      stubFor(
        post(urlEqualTo("/authenticate-totp"))
          .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidEmail](await(underTest.authenticateTotp(totpAuthenticationRequest)))
    }
  }
}
