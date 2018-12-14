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

import com.github.tomakehurst.wiremock.client.WireMock._
import config.WSHttp
import connectors.{PayloadEncryption, ThirdPartyDeveloperConnector}
import domain._
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.http.metrics.NoopMetrics
import utils.TestPayloadEncryptor

class ThirdPartyDeveloperConnectorIntegrationTest extends BaseConnectorSpec with TestPayloadEncryptor {

  trait Setup {
    implicit val hc = HeaderCarrier()

    val underTest = new ThirdPartyDeveloperConnector {
      val http = WSHttp
      val serviceBaseUrl = wireMockUrl
      val payloadEncryption: PayloadEncryption = TestPayloadEncryption
      val metrics = NoopMetrics
    }
  }

  val userEmail = "thirdpartydeveloper@example.com"
  val userPassword = "password1!"
  val sessionId = "sessionId"

  "createSession" should {

    val encryptedLoginRequest = EncryptedJson.toSecretRequestJson(LoginRequest(userEmail, userPassword)).toString()

    "return the session containing the user when the credentials are valid" in new Setup {

      stubFor(post(urlEqualTo("/session"))
        .withRequestBody(equalToJson(encryptedLoginRequest))
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

      val result = await(underTest.createSession(LoginRequest(userEmail, userPassword)))

      verify(1, postRequestedFor(urlMatching("/session")).withRequestBody(equalToJson(encryptedLoginRequest)))

      result shouldBe  Session(sessionId, Developer(userEmail, "John", "Doe"))
    }

    "throw Invalid credentials when the credentials are invalid" in new Setup {
      val encryptedBody = EncryptedJson.toSecretRequestJson(LoginRequest(userEmail, userPassword)).toString()

      stubFor(post(urlEqualTo("/session"))
        .withRequestBody(equalToJson(encryptedLoginRequest))
        .willReturn(
          aResponse()
            .withStatus(UNAUTHORIZED)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[InvalidCredentials](await(underTest.createSession(LoginRequest(userEmail, userPassword))))
    }

    "throw LockedAccount exception when the account is locked" in new Setup {

      stubFor(post(urlEqualTo("/session"))
        .withRequestBody(equalToJson(encryptedLoginRequest))
        .willReturn(
          aResponse()
            .withStatus(LOCKED)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[LockedAccount]{await(underTest.createSession(LoginRequest(userEmail, userPassword)))}
    }

    "throw UnverifiedAccount exception when the account is unverified" in new Setup {

      stubFor(post(urlEqualTo("/session"))
        .withRequestBody(equalToJson(encryptedLoginRequest))
        .willReturn(
          aResponse()
            .withStatus(FORBIDDEN)
            .withHeader("Content-Type", "application/json")
        ))

      intercept[UnverifiedAccount]{await(underTest.createSession(LoginRequest(userEmail, userPassword)))}
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {

      stubFor(post(urlEqualTo("/session"))
        .withRequestBody(equalToJson(encryptedLoginRequest))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        ))

      intercept[Upstream5xxResponse]{await(underTest.createSession(LoginRequest(userEmail, userPassword)))}
    }
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
}
