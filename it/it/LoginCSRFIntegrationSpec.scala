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

package it

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import controllers.routes
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Headers
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import play.filters.csrf.CSRF
import utils.AsyncHmrcSpec

class LoginCSRFIntegrationSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {
  private val config = Configuration("play.filters.csrf.token.sign" -> false)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  private val stubPort = sys.env.getOrElse("WIREMOCK", "11111").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
  val sessionId = "1234567890"

  private val contentType = "Content-Type"
  private val contentTypeApplicationJson = "application/json"

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
    val headers = Headers(AUTHORIZATION -> "AUTH_TOKEN")
    val loginRequest = FakeRequest(POST, "/developer/login").withHeaders(headers)
    val loginRequestWithCSRF = new FakeRequest(addCSRFToken(FakeRequest(POST, "/developer/login").withHeaders(headers)))
    val csrftoken = CSRF.getToken(loginRequestWithCSRF)
  }

  "CSRF handling for login" when {
    "there is no CSRF token" should {
      "redirect back to the login page" in new Setup {
        private val request = loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword)
        private val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is no CSRF token in the request body but it is present in the headers" should {
      "redirect back to the login page" in new Setup {
        private val request = addCSRFToken(loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword))
        private val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a CSRF token in the request body but not in the headers" should {
      "redirect back to the login page" in new Setup {
        private val request = loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> "test")
        private val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a valid CSRF token" should {
      "redirect to the 2SV sign-up reminder if user does not have it set up" in new Setup {
        implicit val materializer: Materializer = fakeApplication().materializer

        stubFor(
          post(urlEqualTo("/authenticate"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withHeader(contentType, contentTypeApplicationJson)
                .withBody(s"""
                   |{
                   |  "accessCodeRequired": false,
                   |  "session": {
                   |    "sessionId": "$sessionId",
                   |    "loggedInState": "LOGGED_IN",
                   |    "developer": {
                   |      "email":"$userEmail",
                   |      "firstName":"John",
                   |      "lastName": "Doe"
                   |    }
                   |  }
                   |}""".stripMargin)
            )
        )

        setupThirdPartyApplicationSearchApplicationByEmailStub()

        private val request = loginRequestWithCSRF.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> csrftoken.get.value)

        private val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.ProtectAccount.get2svRecommendationPage().url)

        verify(1, postRequestedFor(urlMatching("/authenticate")))
      }

      "redirect to the 2SV code entry page if user has it configured" in new Setup {
        implicit val materializer: Materializer = fakeApplication().materializer

        stubFor(
          post(urlEqualTo("/authenticate"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withHeader(contentType, contentTypeApplicationJson)
                .withBody(s"""
                   |{
                   |  "accessCodeRequired": true,
                   |  "nonce": "123456"
                   |}""".stripMargin)
            )
        )

        setupThirdPartyApplicationSearchApplicationByEmailStub()

        private val request = loginRequestWithCSRF.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> csrftoken.get.value)

        private val result = await(route(app, request)).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.enterTotp().url)
      }
    }
  }

  private def setupThirdPartyApplicationSearchApplicationByEmailStub(): Unit = {
    stubFor(
      get(urlEqualTo("/developer/applications?emailAddress=thirdpartydeveloper%40example.com&environment=PRODUCTION"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(contentType, contentTypeApplicationJson)
            .withBody("[]")
        )
    )
  }
}
