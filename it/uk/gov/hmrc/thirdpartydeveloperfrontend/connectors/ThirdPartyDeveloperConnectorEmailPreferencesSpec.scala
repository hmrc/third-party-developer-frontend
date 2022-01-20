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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import domain.InvalidEmail
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.emailpreferences.EmailPreferences
import domain.models.emailpreferences.TaxRegimeInterests
import domain.models.emailpreferences.EmailTopic._
import domain.models.connectors.LoginRequest
import domain.models.connectors.TotpAuthenticationRequest
import play.api.http.HeaderNames
import domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.FindUserIdResponse

class ThirdPartyDeveloperConnectorEmailPreferencesSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
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

  "resendVerificationEmail" should {
    "return" in new Setup {
      val email = "foo@bar.com"
      val userId = UserId.random

      implicit val writes = Json.writes[FindUserIdResponse]

      stubFor(
        post(urlEqualTo("/developers/find-user-id"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(FindUserIdResponse(userId)).toString)
              .withHeader("Content-Type", "application/json")
          )
      )
      
      stubFor(
        post(urlEqualTo(s"/${userId.value}/resend-verification"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      await(underTest.resendVerificationEmail(email)) shouldBe 204

      verify(
        1,
        postRequestedFor(urlMatching(s"/${userId.value}/resend-verification"))
          .withHeader(HeaderNames.CONTENT_LENGTH, equalTo("0"))
      )
    }
  }

  "removeEmailPreferences" should {
    val userId = UserId.random

    "return true when NO_CONTENT is returned" in new Setup {
      stubFor(
        delete(urlEqualTo(s"/developer/${userId.value}/email-preferences"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      await(underTest.removeEmailPreferences(userId)) shouldBe true
    }

    "throw InvalidEmail when the email is not found" in new Setup {
      stubFor(
        delete(urlEqualTo(s"/developer/${userId.value}/email-preferences"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidEmail](await(underTest.removeEmailPreferences(userId)))
    }
  
  }

  "updateEmailPreferences" should {
      val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set("API1", "API2"))), Set(BUSINESS_AND_POLICY))
  
  "return true when NO_CONTENT is returned" in new Setup {
      val userId = UserId.random
      
      stubFor(
        put(urlEqualTo(s"/developer/${userId.value}/email-preferences"))
          .withRequestBody(equalToJson(Json.toJson(emailPreferences).toString()))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      await(underTest.updateEmailPreferences(userId, emailPreferences)) shouldBe true
    }

    "throw InvalidEmail when the email is not found" in new Setup {
      val userId = UserId.random

      stubFor(
        put(urlEqualTo(s"/developer/${userId.value}/email-preferences"))
          .withRequestBody(equalToJson(Json.toJson(emailPreferences).toString()))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      intercept[InvalidEmail](await(underTest.updateEmailPreferences(userId, emailPreferences)))
    }
  
  }
}
