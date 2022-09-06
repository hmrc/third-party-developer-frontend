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

package uk.gov.hmrc.apiplatform.modules.mfa.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.apiplatform.modules.mfa.models.{DeviceSession, DeviceSessionInvalid, MfaId}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WireMockExtensions}

import java.util.UUID

class ThirdPartyDeveloperMfaConnectorIntegrationSpec extends BaseConnectorIntegrationSpec
  with GuiceOneAppPerSuite with DeveloperBuilder with LocalUserIdTracker with WireMockExtensions {
  private val stubConfig = Configuration(
    "microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key" -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val userEmail = "thirdpartydeveloper@example.com"
    val userId = idOf(userEmail)
    val mfaId = MfaId.random

    val userPassword = "password1!"
    val sessionId = "sessionId"
    val loginRequest = LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)
    val deviceSessionId = UUID.randomUUID()
    val deviceSession = DeviceSession(deviceSessionId, userId)
    val createDeviceSessionUrl = s"/device-session/user/${userId.value}"
    val fetchDeviceSessionUrl = s"/device-session/$deviceSessionId/user/${userId.value}"
    val deleteDeviceSessionUrl = s"/device-session/$deviceSessionId"

    val underTest: ThirdPartyDeveloperMfaConnector = app.injector.instanceOf[ThirdPartyDeveloperMfaConnector]

  }

  "create device session" should {

    "return DeviceSession if create is successful" in new Setup {

      stubFor(
        post(urlPathEqualTo(createDeviceSessionUrl))
          .withJsonRequestBody("")
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""{
                           |  "deviceSessionId": "$deviceSessionId",
                           |  "userId": "${userId.value}"
                           |}""".stripMargin)
          )
      )
      val result = await(underTest.createDeviceSession(userId))
      result shouldBe Some(deviceSession)
    }

    "return None if create is unsuccessful and TPD returns 404" in new Setup {

      stubFor(
        post(urlPathEqualTo(createDeviceSessionUrl))
          .withJsonRequestBody("")
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      val result = await(underTest.createDeviceSession(userId))
      result shouldBe None
    }

    "throw UpstreamErrorResponse if TPD returns error" in new Setup {

      stubFor(
        post(urlPathEqualTo(createDeviceSessionUrl))
          .withJsonRequestBody("")
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      intercept[UpstreamErrorResponse] {
        await(underTest.createDeviceSession(userId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR

    }
  }

  "fetchDeviceSession" should {
    "return Device Session if TPD call is successful" in new Setup {
      stubFor(
        get(urlPathEqualTo(fetchDeviceSessionUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"""{
                           |  "deviceSessionId": "$deviceSessionId",
                           |  "userId": "${userId.value}"
                           |}""".stripMargin)
          )
      )

      await(underTest.fetchDeviceSession(deviceSessionId.toString, userId))
    }

    "throw DeviceSessionInvalid if TPD call is unsuccessful" in new Setup {

      stubFor(
        post(urlPathEqualTo(fetchDeviceSessionUrl))
          .withJsonRequestBody("")
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[DeviceSessionInvalid] {
        await(underTest.fetchDeviceSession(deviceSessionId.toString, userId))
      }
    }
  }

  "deleteDeviceSession" should {
    "return NO_CONTENT if TPD call is successful" in new Setup {
      stubFor(
        delete(urlPathEqualTo(deleteDeviceSessionUrl))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )
      await(underTest.deleteDeviceSession(deviceSessionId.toString)) shouldBe NO_CONTENT
    }

    "return NO_CONTENT if TPD call returns NOT FOUND" in new Setup {
      stubFor(
        delete(urlPathEqualTo(deleteDeviceSessionUrl))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      await(underTest.deleteDeviceSession(deviceSessionId.toString)) shouldBe NO_CONTENT
    }

    "throw INTERNAL_SERVER_ERROR if TPD call fails" in new Setup {
      stubFor(
        delete(urlPathEqualTo(deleteDeviceSessionUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      intercept[UpstreamErrorResponse]{
        await(underTest.deleteDeviceSession(deviceSessionId.toString))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR

    }
  }

  "verify MFA" should {
    val code = "12341234"
    val verifyMfaRequest = VerifyMfaRequest(code)

    "return false if verification fails due to InvalidCode" in new Setup {
      val url = s"/developer/${userId.value}/mfa/${mfaId.value}/verification"

      stubFor(
        post(urlPathEqualTo(url))
        .withJsonRequestBody(verifyMfaRequest)
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )
      await(underTest.verifyMfa(userId, mfaId, code)) shouldBe false
    }

    "return true if verification is successful" in new Setup {
      val url = s"/developer/${userId.value}/mfa/${mfaId.value}/verification"

      stubFor(
        post(urlPathEqualTo(url))
        .withJsonRequestBody(verifyMfaRequest)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.verifyMfa(userId, mfaId, code)) shouldBe true
    }

    "throw if verification fails due to error in backend" in new Setup {
      val url = s"/developer/${userId.value}/mfa/${mfaId.value}/verification"

      stubFor(
        post(urlPathEqualTo(url))
        .withJsonRequestBody(verifyMfaRequest)
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      intercept[UpstreamErrorResponse] {
        await(underTest.verifyMfa(userId, mfaId, code))
      }
    }
  }

  "removeMfaById" should {
    "return OK on successful removal" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/${userId.value}/mfa/${mfaId.value}"))
        .willReturn(aResponse().withStatus(OK)))

      await(underTest.removeMfaById(userId, mfaId))
    }

    "throw UpstreamErrorResponse with status of 404 if user not found" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/${userId.value}/mfa/${mfaId.value}"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfaById(userId, mfaId))).statusCode shouldBe NOT_FOUND
    }

    "throw UpstreamErrorResponse with status of 500 if it failed to remove MFA" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/${userId.value}/mfa/${mfaId.value}"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfaById(userId, mfaId))).statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
