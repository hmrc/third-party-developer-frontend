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
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import config.SessionTimeoutFilterWithWhitelist
import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import domain.{Developer, LoggedInState}
import javax.inject.Inject
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Writeable
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{DefaultSessionCookieBaker, Request, SessionCookieBaker}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.http.SessionKeys._
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCryptoFilter
import uk.gov.hmrc.play.bootstrap.filters.frontend.{SessionTimeoutFilter, SessionTimeoutFilterConfig}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

object SessionTimeoutFilterWithWhitelistIntegrationSpec {
  val fixedTime = new DateTime(2019, 1, 1, 0, 0)
  val whitelistedUrl = "/developer/login"
  val notWhitelistedUrl = "/developer/applications"

  val stubPort = sys.env.getOrElse("WIREMOCK_PORT", "11111").toInt
  private val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  private val wireMockConfiguration = WireMockConfiguration.wireMockConfig().port(stubPort)

  class StaticDateSessionTimeoutFilterWithWhitelist @Inject()(config: SessionTimeoutFilterConfig)(implicit ec: ExecutionContext, mat: Materializer)
    extends SessionTimeoutFilterWithWhitelist(config)(ec, mat) {
    println(s"Config : $config")
    override def clock(): DateTime = fixedTime

  }

  val noCrypt = new Encrypter with Decrypter {
    def encrypt(plain: PlainContent): Crypted = plain match {
      case PlainText(value) => Crypted(value)
      case PlainBytes(value) => Crypted(value.mkString)
    }

    def decrypt(reversiblyEncrypted: Crypted): PlainText = PlainText(reversiblyEncrypted.value)

    def decryptAsBytes(reversiblyEncrypted: Crypted): PlainBytes = PlainBytes(reversiblyEncrypted.value.getBytes)
  }

  class PlainCookieCryptoFilter @Inject()(implicit override val mat: Materializer,
                                          override val ec: ExecutionContext) extends SessionCookieCryptoFilter {
    override protected lazy val encrypter: Encrypter = noCrypt
    override protected lazy val decrypter: Decrypter = noCrypt

    override protected def sessionBaker: SessionCookieBaker = new DefaultSessionCookieBaker
  }
}

class SessionTimeoutFilterWithWhitelistIntegrationSpec extends UnitSpec with GuiceOneAppPerSuite with BeforeAndAfterAll with BeforeAndAfterEach {

  import SessionTimeoutFilterWithWhitelistIntegrationSpec._

  val token = "AUTH_TOKEN"
  val sessionTimeoutSeconds = 15*60  // TODO - go back to 900
  val email = "thirdpartydeveloper@example.com"
  val config = Configuration(
    "session.timeoutSeconds" -> sessionTimeoutSeconds,
    "session.wipeIdleSession" -> false,
    "session.additionalSessionKeysToKeep" -> Seq("access_uri")
  )

  val postBody = Seq("emailaddress" -> email, "password" -> "password1!")

  val wireMockServer = new WireMockServer(wireMockConfiguration)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics],
        bind[SessionTimeoutFilter].to[StaticDateSessionTimeoutFilterWithWhitelist],
        bind[SessionCookieCryptoFilter].to[PlainCookieCryptoFilter])
      .in(Mode.Test)
      .build()


  override def beforeAll() = {
    super.beforeAll()
    wireMockServer.start()
  }

  override def afterAll() = {
    wireMockServer.stop()
    super.afterAll()
  }

  class Setup[T](implicit request: Request[T], w: Writeable[T]) {
    WireMock.configureFor(stubPort)
    stubs.ApplicationStub.configureUserApplications(email)

    val developer = Developer(email, "bob", "smith", None, Some(false))
    stubs.ThirdPartyDeveloperStub.configureAuthenticate(Some(domain.Session(sessionId, developer, LoggedInState.LOGGED_IN)))
    val result = await(route(app, request).get)
    val outputSession = result.session
  }

  "SessionTimeoutFilterWithWhitelist" can {

    "if the session has not expired" when {
      val fixedPointInThePast = fixedTime.minusSeconds(sessionTimeoutSeconds / 2).getMillis.toString
      val session = Seq(lastRequestTimestamp -> fixedPointInThePast, authToken -> token)

      "making a GET request to the login page" should {
        implicit lazy val request = addCSRFToken(FakeRequest(GET, whitelistedUrl).withSession(session: _*))

        "ignore the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInThePast)
        }

        "preserve the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe Some(token)
        }
      }

      "making a POST request to the login page" should {
        implicit lazy val request = addCSRFToken(FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*))

        "ignore the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInThePast)
        }

        "preserve the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe Some(token)
        }
      }

      "making a request to a url not in the whitelist" should {
        implicit lazy val request = addCSRFToken(FakeRequest(GET, notWhitelistedUrl).withSession(session: _*))

        "update the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedTime.getMillis.toString)
        }

        "preserve the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe Some(token)
        }
      }
    }

    "if session has expired" when {
      val fixedPointInTheDistantPast = fixedTime.minusSeconds(sessionTimeoutSeconds * 2).getMillis.toString
      val session = Seq(lastRequestTimestamp -> fixedPointInTheDistantPast, authToken -> token)

      "making a GET request to the login page" should {
        implicit lazy val request = addCSRFToken(FakeRequest(GET, whitelistedUrl).withSession(session: _*))

        "ignore the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInTheDistantPast)
        }

        "preserve the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe Some(token)
        }
      }

      "making a POST request to the login page" should {
        implicit lazy val request = addCSRFToken(FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*))

        "ignore the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedPointInTheDistantPast)
        }

        "preserve the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe Some(token)
        }
      }

      "making a request to a url not in the whitelist" should {
        import play.api.http.HeaderNames.HOST
        implicit lazy val request = addCSRFToken(FakeRequest(GET, notWhitelistedUrl).withSession(session: _*).withHeaders(HOST -> "localhost", AUTHORIZATION -> authToken))

        println(s"request headers: ${request.headers}")

        "update the session timeout" in new Setup {
          outputSession.get(lastRequestTimestamp) should not be Some(fixedPointInTheDistantPast)

          outputSession.get(lastRequestTimestamp) shouldBe Some(fixedTime.getMillis.toString)
        }

        "wipe the session's auth token" in new Setup {
          outputSession.get(authToken) shouldBe None
        }
      }
    }
  }
}
