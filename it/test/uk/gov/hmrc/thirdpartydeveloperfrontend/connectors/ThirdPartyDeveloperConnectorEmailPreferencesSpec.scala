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

import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailTopic._
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.{EmailPreferences, TaxRegimeInterests}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.FindUserIdResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.InvalidEmail
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{AccessCodeAuthenticationRequest, LoginRequest}

class ThirdPartyDeveloperConnectorEmailPreferencesSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

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

    val userEmail: LaxEmailAddress                                 = "thirdpartydeveloper@example.com".toLaxEmail
    val userPassword                                               = "password1!"
    val sessionId                                                  = "sessionId"
    val loginRequest: LoginRequest                                 = LoginRequest(userEmail, userPassword, mfaMandatedForUser = false, None)
    val accessCode                                                 = "123456"
    val nonce                                                      = "ABC-123"
    val mfaId: MfaId                                               = MfaId.random
    val totpAuthenticationRequest: AccessCodeAuthenticationRequest = AccessCodeAuthenticationRequest(userEmail, accessCode, nonce, mfaId)

    val payloadEncryption: PayloadEncryption        = app.injector.instanceOf[PayloadEncryption]
    val encryptedLoginRequest: JsValue              = Json.toJson(SecretRequest(payloadEncryption.encrypt(loginRequest).as[String]))
    val encryptedTotpAuthenticationRequest: JsValue = Json.toJson(SecretRequest(payloadEncryption.encrypt(totpAuthenticationRequest).as[String]))
    val underTest: ThirdPartyDeveloperConnector     = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "resendVerificationEmail" should {
    "return" in new Setup {
      val email: LaxEmailAddress = "foo@bar.com".toLaxEmail
      val userId: UserId         = UserId.random

      implicit val writes: Writes[FindUserIdResponse] = Json.writes[FindUserIdResponse]

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
        post(urlEqualTo(s"/$userId/resend-verification"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
              .withHeader("Content-Type", "application/json")
          )
      )

      await(underTest.resendVerificationEmail(email)) shouldBe 204

      verify(
        1,
        postRequestedFor(urlMatching(s"/$userId/resend-verification"))
          .withHeader(HeaderNames.CONTENT_LENGTH, equalTo("0"))
      )
    }
  }

  "removeEmailPreferences" should {
    val userId = UserId.random

    "return true when NO_CONTENT is returned" in new Setup {
      stubFor(
        delete(urlEqualTo(s"/developer/$userId/email-preferences"))
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
        delete(urlEqualTo(s"/developer/$userId/email-preferences"))
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
      val userId: UserId = UserId.random

      stubFor(
        put(urlEqualTo(s"/developer/$userId/email-preferences"))
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
      val userId: UserId = UserId.random

      stubFor(
        put(urlEqualTo(s"/developer/$userId/email-preferences"))
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
