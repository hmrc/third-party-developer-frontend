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

package unit.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors._
import domain._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}

class ThirdPartyDeveloperConnectorIntegrationTest extends BaseConnectorSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration(
    "Test.microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key" -> "abcdefghijklmnopqrstuv=="
  )
  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()

    val userEmail = "thirdpartydeveloper@example.com"
    val userPassword = "password1!"
    val sessionId = "sessionId"
    val loginRequest = LoginRequest(userEmail, userPassword)
    val totp = "123456"
    val nonce = "ABC-123"
    val totpAuthenticationRequest = TotpAuthenticationRequest(userEmail, totp, nonce)

    val payloadEncryption = app.injector.instanceOf[PayloadEncryption]
    val encryptedLoginRequest = Json.toJson(SecretRequest(payloadEncryption.encrypt(loginRequest).as[String]))
    val encryptedTotpAuthenticationRequest = Json.toJson(SecretRequest(payloadEncryption.encrypt(totpAuthenticationRequest).as[String]))
    val underTest = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "fetchSession" should {

    "return the session" in new Setup {

      stubFor(get(urlPathEqualTo(s"/session/$sessionId"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""{
                 |  "sessionId": "$sessionId",
                 |  "developer": {
                 |    "email":"$userEmail",
                 |    "firstName":"John",
                 |    "lastName": "Doe"
                 |  }
                 |}""".stripMargin)
        ))

      val result = await(underTest.fetchSession(sessionId))

      result shouldBe Session(sessionId, Developer(userEmail, "John", "Doe"))
    }

    "return Fail with session invalid when the session doesnt exist" in new Setup {

      stubFor(get(urlPathEqualTo(s"/session/$sessionId"))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
        ))


      intercept[SessionInvalid](await(underTest.fetchSession(sessionId)))
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      stubFor(get(urlPathEqualTo(s"/session/$sessionId"))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        ))

      intercept[Upstream5xxResponse] {await(underTest.fetchSession(sessionId))}
    }

  }

  "removeMfa" should {
    "return OK on successful removal" in new Setup {
      val email = "test.user@example.com"
      stubFor(delete(urlPathEqualTo(s"/developer/$email/mfa")).willReturn(aResponse().withStatus(OK)))

      val result: Int = await(underTest.removeMfa(email))

      result shouldBe OK
    }

    "throw NotFoundException if user not found" in new Setup {
      val email = "invalid.user@example.com"
      stubFor(delete(urlPathEqualTo(s"/developer/$email/mfa")).willReturn(aResponse().withStatus(NOT_FOUND)))

      intercept[NotFoundException](await(underTest.removeMfa(email)))
    }

    "throw Upstream5xxResponse if it failed to remove MFA" in new Setup {
      val email = "test.user@example.com"
      stubFor(delete(urlPathEqualTo(s"/developer/$email/mfa")).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse](await(underTest.removeMfa(email)))
    }
  }

  "authenticate" should {

    "return the session containing the user when the credentials are valid and MFA is disabled" in new Setup {

      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""
                 |{
                 |  "accessCodeRequired": false,
                 |  "session": {
                 |    "sessionId": "$sessionId",
                 |    "developer": {
                 |      "email":"$userEmail",
                 |      "firstName":"John",
                 |      "lastName": "Doe"
                 |    }
                 |  }
                 |}""".stripMargin)
        ))

      val result: UserAuthenticationResponse = await(underTest.authenticate(loginRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate")).withRequestBody(equalToJson(encryptedLoginRequest.toString)))
      result shouldBe UserAuthenticationResponse(accessCodeRequired = false, session = Some(Session(sessionId, Developer(userEmail, "John", "Doe"))))
    }

    "return the nonce when the credentials are valid and MFA is enabled" in new Setup {

      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""
                 |{
                 |  "accessCodeRequired": true,
                 |  "nonce": "$nonce"
                 |}""".stripMargin)
        ))

      val result: UserAuthenticationResponse = await(underTest.authenticate(loginRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate")).withRequestBody(equalToJson(encryptedLoginRequest.toString)))
      result shouldBe UserAuthenticationResponse(accessCodeRequired = true, Some(nonce))
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(UNAUTHORIZED)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[InvalidCredentials](await(underTest.authenticate(LoginRequest(userEmail, userPassword))))
    }

    "throw LockedAccount exception when the account is locked" in new Setup {

      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(LOCKED)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[LockedAccount]{await(underTest.authenticate(LoginRequest(userEmail, userPassword)))}
    }

    "throw UnverifiedAccount exception when the account is unverified" in new Setup {

      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(FORBIDDEN)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[UnverifiedAccount]{await(underTest.authenticate(LoginRequest(userEmail, userPassword)))}
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {

      stubFor(post(urlEqualTo("/authenticate"))
        .withRequestBody(equalToJson(encryptedLoginRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        ))

      intercept[Upstream5xxResponse]{await(underTest.authenticate(LoginRequest(userEmail, userPassword)))}
    }
  }

  "authenticateTotp" should {

    "return the session containing the user when the TOTP and nonce are valid" in new Setup {

      stubFor(post(urlEqualTo("/authenticate-totp"))
        .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""
                 |{
                 |  "sessionId": "$sessionId",
                 |  "developer": {
                 |    "email":"$userEmail",
                 |    "firstName":"John",
                 |    "lastName": "Doe"
                 |  }
                 |}""".stripMargin)
        ))

      val result: Session = await(underTest.authenticateTotp(totpAuthenticationRequest))

      verify(1, postRequestedFor(urlMatching("/authenticate-totp")).withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString)))
      result shouldBe Session(sessionId, Developer(userEmail, "John", "Doe"))
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      stubFor(post(urlEqualTo("/authenticate-totp"))
        .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[InvalidCredentials](await(underTest.authenticateTotp(totpAuthenticationRequest)))
    }

    "throw InvalidEmail when the email is not found" in new Setup {
      stubFor(post(urlEqualTo("/authenticate-totp"))
        .withRequestBody(equalToJson(encryptedTotpAuthenticationRequest.toString))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[InvalidEmail](await(underTest.authenticateTotp(totpAuthenticationRequest)))
    }
  }
}
