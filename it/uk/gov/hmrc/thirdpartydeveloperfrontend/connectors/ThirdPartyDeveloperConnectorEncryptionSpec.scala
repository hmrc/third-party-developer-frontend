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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{ChangePassword, PasswordReset}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Registration
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{InvalidCredentials, LockedAccount, UnverifiedAccount}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.EmailAlreadyInUse

class ThirdPartyDeveloperConnectorEncryptionSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions {

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
    implicit val hc = HeaderCarrier()
    val underTest   = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "register" should {
    "send request with encrypted payload" in new Setup {
      stubFor(
        post(urlEqualTo("/developer"))
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withHeader("Content-Type", "application/json")
          )
      )
      await(underTest.register(new Registration("first", "last", "email@example.com", "password")))
      verify(
        1,
        postRequestedFor(urlMatching("/developer"))
          .withRequestBody(
            equalTo("""{"data":"yLR5YLduz4B2c79v3eSrnUuk71jBNoOOytn5CgYL/JbxxGVgD/JJVZAwF5fm/z3LTxtUsa9G6WSLb9F5Sh4YNTQuTO4Cm+8EtimKAMofV6BnHESgQTR9x1Ebgznq7UM9"}""")
          )
      )
    }

    "fail to register a developer when the email address is already in use" in new Setup {
      val registrationToTest = Registration("first", "last", "email@example.com", "password")
      val secretPayload      = SecretRequest("yLR5YLduz4B2c79v3eSrnUuk71jBNoOOytn5CgYL/JbxxGVgD/JJVZAwF5fm/z3LTxtUsa9G6WSLb9F5Sh4YNTQuTO4Cm+8EtimKAMofV6BnHESgQTR9x1Ebgznq7UM9")

      stubFor(
        post(urlEqualTo("/developer"))
          .withJsonRequestBody(secretPayload)
          .willReturn(
            aResponse()
              .withStatus(CONFLICT)
          )
      )
      await(underTest.register(registrationToTest)) shouldBe EmailAlreadyInUse
    }
  }

  "createUnregisteredUser" should {
    "send request with encrypted payload" in new Setup {
      stubFor(
        post(urlEqualTo("/unregistered-developer"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
          )
      )
      await(underTest.createUnregisteredUser("email@example.com"))
      verify(
        1,
        postRequestedFor(urlMatching("/unregistered-developer"))
          .withRequestBody(equalTo("""{"data":"SnD4DUOHcofAQ4I47oLWPJphsTSdnsNimDZYxGLBsNk="}"""))
      )
    }
  }

  "reset-password" should {
    "send request with encrypted payload" in new Setup {
      stubFor(
        post(urlEqualTo("/reset-password"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
          )
      )
      await(underTest.reset(new PasswordReset("email@example.com", "newPassword")))
      verify(
        1,
        postRequestedFor(urlMatching("/reset-password"))
          .withRequestBody(equalTo("""{"data":"SnD4DUOHcofAQ4I47oLWPD9WBDX+EpFdLI9sgOgrmALZCFOLGD3vQHyTdsIGleFrZxxEoEE0fcdRG4M56u1DPQ=="}"""))
      )
    }
  }

  "change-password" should {
    "send request with encrypted payload" in new Setup {
      stubFor(
        post(urlEqualTo("/change-password"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
          )
      )
      await(underTest.changePassword(new ChangePassword("email@example.com", "oldPassword", "newPassword")))
      verify(
        1,
        postRequestedFor(urlMatching("/change-password"))
          .withRequestBody(
            equalTo("""{"data":"SnD4DUOHcofAQ4I47oLWPEvRoYcX4MI3Pvf7Hi00UW3Mvua6Mn/CtJHdVVBMe+PY2iC59W2ezrtf2CctLsjw6bzljfc14VQVDSO1+Zq0IHc2wY/GEYT92qzvXri8Yycg"}""")
          )
      )
    }

    "return a locked response when the account is locked" in new Setup {
      stubFor(
        post(urlEqualTo("/change-password"))
          .willReturn(
            aResponse()
              .withStatus(Status.LOCKED)
          )
      )

      intercept[LockedAccount] {
        await(underTest.changePassword(ChangePassword("email@email.com", "oldPassword", "newPassword")))
      }
    }

    "return an unverified account response when the account is forbidden" in new Setup {
      stubFor(
        post(urlEqualTo("/change-password"))
          .willReturn(
            aResponse()
              .withStatus(Status.FORBIDDEN)
          )
      )

      intercept[UnverifiedAccount] {
        await(underTest.changePassword(ChangePassword("email@email.com", "oldPassword", "newPassword")))
      }
    }

    "return an invalid credentials response when the account is unauthorised" in new Setup {
      stubFor(
        post(urlEqualTo("/change-password"))
          .willReturn(
            aResponse()
              .withStatus(Status.UNAUTHORIZED)
          )
      )

      intercept[InvalidCredentials] {
        await(underTest.changePassword(ChangePassword("email@email.com", "oldPassword", "newPassword")))
      }
    }
  }
}
