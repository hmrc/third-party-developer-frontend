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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.stream.Materializer
import org.mockito.invocation.InvocationOnMock
import org.scalatest.concurrent.ScalaFutures.whenReady
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.bootstrap.frontend.filters.SessionTimeoutFilterConfig

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class SessionTimeoutFilterWithWhitelistSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite {

  trait Setup {
    implicit val m: Materializer = app.materializer
    val config                   = SessionTimeoutFilterConfig(timeoutDuration = 10.seconds, onlyWipeAuthToken = false)

    val nextOperationFunction = mock[RequestHeader => Future[Result]]
    val whitelistedUrl        = uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.UserLoginAccount.login().url
    val otherUrl              = "/applications"
    val accessUri             = "http://redirect.to/here"
    val bearerToken           = "Bearer Token"

    val filter = new SessionTimeoutFilterWithWhitelist(config) {
      override val whitelistedCalls = Set(WhitelistedCall(whitelistedUrl, "GET"))
    }

    when(nextOperationFunction.apply(*)).thenAnswer((invocation: InvocationOnMock) => {
      val headers = invocation.getArguments.head.asInstanceOf[RequestHeader]
      Future.successful(Results.Ok.withSession(headers.session + ("authToken" -> bearerToken)))
    })

    def now                      = Instant.now()
    def nowInMillis: String      = now.toEpochMilli.toString
    def twentySecondsAgo: String = now.minusSeconds(20).toEpochMilli.toString
  }

  "when there is an active session, apply" should {

    "leave the access_uri intact when path in whitelist" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> nowInMillis, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }

    "leave the access_uri intact when path not in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = "/applications")
        .withSession("ts" -> nowInMillis, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }

    "leave the access_uri intact when path in whitelist with different method" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> nowInMillis, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }
  }

  "when the session has expired, apply" should {

    "leave the access_uri intact when path in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = whitelistedUrl)
        .withSession("ts" -> twentySecondsAgo, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }

    "remove the access_uri when path not in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = otherUrl)
        .withSession("ts" -> twentySecondsAgo, "access_uri" -> accessUri)
      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.keys shouldNot contain("access_uri")
        sessionData.keys should contain("ts")
      }
      verify(nextOperationFunction).apply(*)
    }

    "remove the session keys when path in whitelist with different method" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> twentySecondsAgo, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.keys shouldNot contain("access_uri")
        sessionData.keys should contain("ts")
      }
      verify(nextOperationFunction).apply(*)
    }
  }

  "when there is no active session, apply" should {

    "leave the access_uri intact when path in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = whitelistedUrl)
        .withSession("access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 2
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
      }

      verify(nextOperationFunction).apply(*)
    }

    "leave the access_uri intact when path not in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = otherUrl)
        .withSession("access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }

    "leave the access_uri intact when path in whitelist with different method" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(*)
    }
  }
}
