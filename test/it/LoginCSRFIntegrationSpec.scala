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

package it

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._

class LoginCSRFIntegrationSpec extends UnitSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {
  private val config = Configuration("play.filters.csrf.token.sign" -> false)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  val stubPort = sys.env.getOrElse("WIREMOCK", "11111").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  val sessionId = "1234567890"

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterEach() {
    wireMockServer.resetMappings()
    wireMockServer.stop()
  }

  trait Setup {
    val userEmail = "thirdpartydeveloper@example.com"
    val userPassword = "password1!"
    val loginRequest = FakeRequest(POST, "/developer/login")
  }

  "CSRF handling for login" when {
    "there is no CSRF token" should {
      "redirect back to the login page" in new Setup {
        val request = loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword)
        val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is no CSRF token in the request body but it is present in the headers" should {
      "redirect back to the login page" in new Setup {
        val request = loginRequest.withCSRFToken.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword)
        val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a CSRF token in the request body but not in the headers" should {
      "redirect back to the login page" in new Setup {
        val request = loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> "test")
        val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a valid CSRF token" should {
      "redirect back to the applications page" in new Setup {
        stubFor(post(urlEqualTo("/authenticate"))
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
                   |}""".stripMargin)))

        val request = loginRequest.withCSRFToken
          .withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> "test")

        val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/applications")
        verify(1, postRequestedFor(urlMatching("/authenticate")))
      }
    }
  }
}
