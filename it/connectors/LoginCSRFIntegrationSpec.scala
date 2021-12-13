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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{status => _, _}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import controllers.routes
import org.scalatest.BeforeAndAfterEach
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
import domain.models.developers.UserId
import connectors.ThirdPartyDeveloperConnector.FindUserIdRequest
import connectors.ThirdPartyDeveloperConnector.JsonFormatters.FindUserIdRequestWrites
import play.api.libs.json.Json

class LoginCSRFIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {
  private lazy val config = Configuration(
    "play.filters.csrf.token.sign" -> false,
    "microservice.services.third-party-developer.port" -> stubPort,
    "microservice.services.third-party-application-production.port" -> stubPort,
    "microservice.services.third-party-application-sandbox.port" -> stubPort,
    "microservice.services.api-definition.port" -> stubPort,
    "microservice.services.api-documentation-frontend.port" -> stubPort,
    "microservice.services.third-party-developer-frontend.port" -> 9685,
    "microservice.services.hmrc-deskpro.port" -> stubPort,
    "microservice.services.api-subscription-fields-production.port" -> stubPort,
    "microservice.services.api-subscription-fields-sandbox.port" -> stubPort,
    "microservice.services.api-platform-microservice.port" -> stubPort,
    "microservice.services.push-pull-notifications-api-production.port" -> stubPort,
    "microservice.services.push-pull-notifications-api-sandbox.port" -> stubPort
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  override val stubPort = sys.env.getOrElse("WIREMOCK", "11111").toInt
  override val stubHost = "localhost"
  override val wireMockUrl = s"http://$stubHost:$stubPort"
  override val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))
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
    val userId = UserId.random
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
        private val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is no CSRF token in the request body but it is present in the headers" should {
      "redirect back to the login page" in new Setup {
        private val request = addCSRFToken(loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword))
        private val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a CSRF token in the request body but not in the headers" should {
      "redirect back to the login page" in new Setup {
        private val request = loginRequest.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> "test")
        private val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "there is a valid CSRF token" should {
      "redirect to the 2SV sign-up reminder if user does not have it set up" in new Setup {
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

        setupThirdPartyDeveloperFindUserIdByEmailAddress(userEmail, userId)
        setupThirdPartyApplicationSearchApplicationByUserIdStub(userId)

        private val request = loginRequestWithCSRF.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> csrftoken.get.value)

        private val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.get2svRecommendationPage().url)

        verify(1, postRequestedFor(urlMatching("/authenticate")))
      }

      "redirect to the 2SV code entry page if user has it configured" in new Setup {

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

        setupThirdPartyDeveloperFindUserIdByEmailAddress(userEmail, userId)
        setupThirdPartyApplicationSearchApplicationByUserIdStub(userId)

        private val request = loginRequestWithCSRF.withFormUrlEncodedBody("emailaddress" -> userEmail, "password" -> userPassword, "csrfToken" -> csrftoken.get.value)

        private val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UserLoginAccount.enterTotp().url)
      }
    }
  }

  private def setupThirdPartyDeveloperFindUserIdByEmailAddress(emailAddress: String, userId: UserId) = {
    stubFor(
      post(urlEqualTo("/developers/find-user-id"))
        .withRequestBody(equalToJson(Json.toJson(FindUserIdRequest(emailAddress)).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{"userId":"${userId.asText}"}""")
        )
    )
  }

  private def setupThirdPartyApplicationSearchApplicationByUserIdStub(userId: UserId): Unit = {
    stubFor(
      get(urlEqualTo(s"/developer/applications?userId=${userId.asText}&environment=PRODUCTION"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader(contentType, contentTypeApplicationJson)
            .withBody("[]")
        )
    )
  }
}
