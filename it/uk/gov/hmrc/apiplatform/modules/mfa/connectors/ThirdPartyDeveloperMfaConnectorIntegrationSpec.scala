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

package uk.gov.hmrc.apiplatform.modules.mfa.connectors

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsFailureResponse, RegisterSmsSuccessResponse}
import uk.gov.hmrc.apiplatform.modules.mfa.models.{DeviceSession, DeviceSessionInvalid, MfaId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WireMockExtensions}

class ThirdPartyDeveloperMfaConnectorIntegrationSpec extends BaseConnectorIntegrationSpec
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

    val userEmail = "thirdpartydeveloper@example.com".toLaxEmail
    val userId    = idOf(userEmail)
    val mfaId     = MfaId.random

    val userPassword           = "password1!"
    val sessionId              = "sessionId"
    val loginRequest           = LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)
    val deviceSessionId        = UUID.randomUUID()
    val deviceSession          = DeviceSession(deviceSessionId, userId)
    val createDeviceSessionUrl = s"/device-session/user/$userId"
    val fetchDeviceSessionUrl  = s"/device-session/$deviceSessionId/user/$userId"
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
              .withBody(
                s"""{
                   |  "deviceSessionId": "$deviceSessionId",
                   |  "userId": "$userId"
                   |}""".stripMargin
              )
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
              .withBody(
                s"""{
                   |  "deviceSessionId": "$deviceSessionId",
                   |  "userId": "$userId"
                   |}""".stripMargin
              )
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
      intercept[UpstreamErrorResponse] {
        await(underTest.deleteDeviceSession(deviceSessionId.toString))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR

    }
  }

  "createMfaAuthApp" should {
    "return 201 with RegisterAuthAppResponse" in new Setup {
      val url      = s"/developer/$userId/mfa/auth-app"
      val response = RegisterAuthAppResponse(mfaId, "secret")

      stubFor(
        post(urlPathEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(Json.toJson(response).toString)
          )
      )
      await(underTest.createMfaAuthApp(userId)) shouldBe response
    }

    "return 404 with RegisterAuthAppResponse" in new Setup {
      val url = s"/developer/$userId/mfa/auth-app"
      stubFor(
        post(urlPathEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[UpstreamErrorResponse](await(underTest.createMfaAuthApp(userId))).statusCode shouldBe NOT_FOUND
    }
  }

  "createMfaSms" should {
    val mobileNumber    = "0123456789"
    val request         = CreateMfaSmsRequest(mobileNumber)
    val successResponse = RegisterSmsSuccessResponse(mfaId = MfaId.random, mobileNumber = mobileNumber)
    val failureResponse = RegisterSmsFailureResponse()

    "return 201 with RegisterSmsSuccessResponse" in new Setup {
      val url = s"/developer/$userId/mfa/sms"
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(Json.toJson(successResponse).toString)
          )
      )
      await(underTest.createMfaSms(userId, mobileNumber)) shouldBe successResponse
    }

    "return 400 when invalid number is passed to TPD" in new Setup {
      val url = s"/developer/$userId/mfa/sms"
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )
      await(underTest.createMfaSms(userId, mobileNumber)) shouldBe failureResponse
    }

    "return 500 with RegisterSmsFailureResponse when invalid number provided for access codes" in new Setup {
      val badMobileNumber = "05555555555"
      val url             = s"/developer/$userId/mfa/sms"
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(CreateMfaSmsRequest(badMobileNumber))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      await(underTest.createMfaSms(userId, badMobileNumber)) shouldBe failureResponse
    }

    "return 404 when user is not found" in new Setup {
      val url = s"/developer/$userId/mfa/sms"
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      await(underTest.createMfaSms(userId, mobileNumber)) shouldBe failureResponse
    }
  }

  "verify MFA" should {
    val code             = "12341234"
    val verifyMfaRequest = VerifyMfaRequest(code)

    "return false if verification fails due to InvalidCode" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/verification"

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
      val url = s"/developer/$userId/mfa/$mfaId/verification"

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
      val url = s"/developer/$userId/mfa/$mfaId/verification"

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

  "sendSms" should {
    "return false if it fails to send sms" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/send-sms"

      stubFor(
        post(urlPathEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )
      await(underTest.sendSms(userId, mfaId)) shouldBe false
    }

    "return true when sms is successfully sent" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/send-sms"

      stubFor(
        post(urlPathEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
      await(underTest.sendSms(userId, mfaId)) shouldBe true
    }

    "throw if it fails to send sms due to error in backend" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/send-sms"

      stubFor(
        post(urlPathEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      intercept[UpstreamErrorResponse] {
        await(underTest.sendSms(userId, mfaId)) shouldBe false
      }
    }
  }

  "removeMfaById" should {
    "return OK on successful removal" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/$userId/mfa/$mfaId"))
        .willReturn(aResponse().withStatus(OK)))

      await(underTest.removeMfaById(userId, mfaId))
    }

    "throw UpstreamErrorResponse with status of 404 if user not found" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/$userId/mfa/$mfaId"))
        .willReturn(aResponse().withStatus(NOT_FOUND)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfaById(userId, mfaId))).statusCode shouldBe NOT_FOUND
    }

    "throw UpstreamErrorResponse with status of 500 if it failed to remove MFA" in new Setup {
      stubFor(delete(urlPathEqualTo(s"/developer/$userId/mfa/$mfaId"))
        .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      intercept[UpstreamErrorResponse](await(underTest.removeMfaById(userId, mfaId))).statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "change MFA name" should {
    val updatedName          = "updated name"
    val changeMfaNameRequest = ChangeMfaNameRequest(updatedName)

    "return true if call to backend is successful" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/name"

      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(changeMfaNameRequest)
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )
      await(underTest.changeName(userId, mfaId, updatedName)) shouldBe true
    }

    "return false if call to backend returns Bad Request" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/name"

      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(changeMfaNameRequest)
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )
      await(underTest.changeName(userId, mfaId, updatedName)) shouldBe false
    }

    "throw UpstreamErrorResponse if call to backend returns error" in new Setup {
      val url = s"/developer/$userId/mfa/$mfaId/name"

      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(changeMfaNameRequest)
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
      intercept[UpstreamErrorResponse] {
        await(underTest.changeName(userId, mfaId, updatedName))
      }
    }
  }
}
