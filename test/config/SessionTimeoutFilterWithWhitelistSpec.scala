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

package config

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.bootstrap.filters.frontend.SessionTimeoutFilterConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.SharedMetricsClearDown

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionTimeoutFilterWithWhitelistSpec extends UnitSpec with MockitoSugar with ScalaFutures with WithFakeApplication with SharedMetricsClearDown {

  trait Setup {
    implicit val sys = ActorSystem("SessionTimeoutFilterWithWhitelistSpec")
    implicit val mat = ActorMaterializer()
    val config = SessionTimeoutFilterConfig(
      timeoutDuration = Duration.standardSeconds(1),
      onlyWipeAuthToken = false)

    val nextOperationFunction = mock[RequestHeader => Future[Result]]
    val whitelistedUrl = controllers.routes.UserLoginAccount.login().url
    val otherUrl = "/applications"
    val accessUri = "http://redirect.to/here"
    val bearerToken = "Bearer Token"

    val filter = new SessionTimeoutFilterWithWhitelist(config) {
      override val whitelistedCalls = Set(WhitelistedCall(whitelistedUrl, "GET"))
    }


    when(nextOperationFunction.apply(any())).thenAnswer(new Answer[Future[Result]] {
      override def answer(invocation: InvocationOnMock): Future[Result] = {
        val headers = invocation.getArguments.head.asInstanceOf[RequestHeader]
        Future.successful(Results.Ok.withSession(headers.session + ("authToken" -> bearerToken)))
      }
    })

    def now: String = {
      DateTime.now(DateTimeZone.UTC).getMillis.toString
    }

    def twoSecondsAgo: String = {
      DateTime.now(DateTimeZone.UTC).minusSeconds(2).getMillis.toString
    }
  }

  "when there is an active session, apply" should {

    "leave the access_uri intact when path in whitelist" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> now, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
    }

    "leave the access_uri intact when path not in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = "/applications")
        .withSession("ts" -> now, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
    }

    "leave the access_uri intact when path in whitelist with different method" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> now, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
    }
  }

  "when the session has expired, apply" should {

    "leave the access_uri intact when path in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = whitelistedUrl)
        .withSession("ts" -> twoSecondsAgo, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 3
        sessionData("authToken") shouldBe bearerToken
        sessionData("access_uri") shouldBe accessUri
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
    }

    "remove the access_uri when path not in whitelist" in new Setup {
      val request = FakeRequest(method = "GET", path = "/applications")
        .withSession("ts" -> twoSecondsAgo, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 1
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
    }

    "remove the session keys when path in whitelist with different method" in new Setup {
      val request = FakeRequest(method = "POST", path = whitelistedUrl)
        .withSession("ts" -> twoSecondsAgo, "access_uri" -> accessUri)

      whenReady(filter.apply(nextOperationFunction)(request)) { result =>
        val sessionData = result.session(request).data
        sessionData.size shouldBe 1
        sessionData.isDefinedAt("ts") shouldBe true
      }

      verify(nextOperationFunction).apply(any())
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

      verify(nextOperationFunction).apply(any())
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

      verify(nextOperationFunction).apply(any())
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

      verify(nextOperationFunction).apply(any())
    }
  }
}
