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
import config.SessionTimeoutFilterWithWhitelist
import connectors.{ConnectorMetrics, NoopConnectorMetrics}
import javax.inject.Inject
import org.joda.time.DateTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Writeable
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.crypto._
import uk.gov.hmrc.http.SessionKeys._
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.CookieCryptoFilter
import uk.gov.hmrc.play.bootstrap.filters.frontend.{SessionTimeoutFilter, SessionTimeoutFilterConfig}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

object SessionTimeoutFilterWithWhitelistIntegrationSpec {
  val now = new DateTime(2019, 1, 1, 0, 0)
  val whitelistedUrl = "/developer/login"
  val notWhitelistedUrl = "/developer/registration"

  class StaticDateSessionTimeoutFilterWithWhitelist @Inject()(config: SessionTimeoutFilterConfig)(implicit ec: ExecutionContext, mat: Materializer)
    extends SessionTimeoutFilterWithWhitelist(config)(ec, mat) {
    override val clock: DateTime = now
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
                                          override val ec: ExecutionContext) extends CookieCryptoFilter {
    override protected lazy val encrypter: Encrypter = noCrypt
    override protected lazy val decrypter: Decrypter = noCrypt
  }
}

class SessionTimeoutFilterWithWhitelistIntegrationSpec extends UnitSpec with GuiceOneAppPerSuite {

  import SessionTimeoutFilterWithWhitelistIntegrationSpec._

  val token = "AUTH_TOKEN"
  val sessionTimeoutSeconds = 900
  val config = Configuration(
    "session.timeoutSeconds" -> sessionTimeoutSeconds,
    "session.wipeIdleSession" -> false,
    "session.additionalSessionKeysToKeep" -> Seq("access_uri"))
  val postBody = Seq("emailaddress" -> "thirdpartydeveloper@example.com", "password" -> "password1!")

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(config)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics],
        bind[SessionTimeoutFilter].to[StaticDateSessionTimeoutFilterWithWhitelist],
        bind[CookieCryptoFilter].to[PlainCookieCryptoFilter])
      .in(Mode.Test)
      .build()

  class Setup[T](implicit request: Request[T], w: Writeable[T]) {
    val session = await(route(app, request)).get.session
  }

  "SessionTimeoutFilterWithWhitelist" when {

    "the session has not expired" when {
      val timestamp = now.minusSeconds(sessionTimeoutSeconds / 2).getMillis.toString
      val session = Seq(lastRequestTimestamp -> timestamp, authToken -> token)

      "making a GET request to the login page" should {
        implicit lazy val request = FakeRequest(GET, whitelistedUrl).withSession(session: _*)

        "ignore the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(timestamp)
        }

        "preserve the session's auth token" in new Setup {
          session.get(authToken) shouldBe Some(token)
        }
      }

      "making a POST request to the login page" should {
        implicit lazy val request = FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*)

        "ignore the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(timestamp)
        }

        "preserve the session's auth token" in new Setup {
          session.get(authToken) shouldBe Some(token)
        }
      }

      "making a request to a url not in the whitelist" should {
        implicit lazy val request = FakeRequest(GET, notWhitelistedUrl).withSession(session: _*)

        "update the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(now.getMillis.toString)
        }

        "preserve the session's auth token" in new Setup {
          session.get(authToken) shouldBe Some(token)
        }
      }
    }

    "the session has expired" when {
      val timestamp = now.minusSeconds(sessionTimeoutSeconds * 2).getMillis.toString
      val session = Seq(lastRequestTimestamp -> timestamp, authToken -> token)

      "making a GET request to the login page" should {
        implicit lazy val request = FakeRequest(GET, whitelistedUrl).withSession(session: _*)

        "ignore the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(timestamp)
        }

        "preserve the session's auth token" in new Setup {
          session.get(authToken) shouldBe Some(token)
        }
      }

      "making a POST request to the login page" should {
        implicit lazy val request = FakeRequest(POST, whitelistedUrl).withSession(session: _*).withFormUrlEncodedBody(postBody: _*)

        "ignore the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(timestamp)
        }

        "preserve the session's auth token" in new Setup {
          session.get(authToken) shouldBe Some(token)
        }
      }

      "making a request to a url not in the whitelist" should {
        implicit lazy val request = FakeRequest(GET, notWhitelistedUrl).withSession(session: _*)

        "update the session timeout" in new Setup {
          session.get(lastRequestTimestamp) shouldBe Some(now.getMillis.toString)
        }

        "wipe the session's auth token" in new Setup {
          session.get(authToken) shouldBe None
        }
      }
    }
  }
}
