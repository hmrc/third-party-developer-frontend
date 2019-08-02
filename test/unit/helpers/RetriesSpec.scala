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

package unit.helpers

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import helpers.Retries
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{RequestHeader, Result, Results}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class RetriesSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
    val mockFutureTimeoutSupport: FutureTimeoutSupport = mock[FutureTimeoutSupport]

    def underTest = new RetryTestConnector(mockFutureTimeoutSupport, mockAppConfig)
  }

  class RetryTestConnector(val futureTimeout: FutureTimeoutSupport,
                           val appConfig: ApplicationConfig) extends Retries {
    implicit val ec: ExecutionContext = global

    override protected def actorSystem: ActorSystem = ActorSystem("test-actor-system")
  }

  "Retries" should {

    "Retry the configured number of times on Bad Request" in new Setup {

      private val expectedRetries = Random.nextInt(3) + 1
      when(mockAppConfig.retryCount).thenReturn(expectedRetries)
      when(mockFutureTimeoutSupport.after[Unit](any(),any())(any())(any())).thenReturn(???)
//        .thenAnswer(new Answer[Unit] {
//        override def answer(invocationOnMock: InvocationOnMock): Unit = {
//          val block = invocationOnMock.getArguments.apply(2).asInstanceOf[() => Future[Unit]]
//          block()
//        }
//      }
//      )




//      when(nextOperationFunction.apply(any())).thenAnswer(new Answer[Future[Result]] {
//        override def answer(invocation: InvocationOnMock): Future[Result] = {
//          val headers = invocation.getArguments.head.asInstanceOf[RequestHeader]
//          Future.successful(Results.Ok.withSession(headers.session+("authToken" -> bearerToken)))
//        }
//      })

      var actualRetries = 0

      private val response: Unit = await(underTest.retry {

        if (actualRetries < expectedRetries) {
          actualRetries += 1
          Future.failed(new BadRequestException(""))
        }
        else Future.successful(())
      })

      response shouldBe ((): Unit)
      actualRetries shouldBe expectedRetries
    }

    "Do not retry when retryCount is configured to zero" in new Setup {

      private val expectedRetries = 0
      when(mockAppConfig.retryCount).thenReturn(expectedRetries)
      var actualCalls = 0

      intercept[BadRequestException](
        await(underTest.retry {
          actualCalls += 1
          Future.failed(new BadRequestException(""))
        }))

      actualCalls shouldBe 1
    }

    "Do not retry on exceptions other than BadRequestException" in new Setup {

      private val expectedRetries = Random.nextInt(3) + 1
      when(mockAppConfig.retryCount).thenReturn(expectedRetries)
      var actualCalls = 0

      intercept[RuntimeException](
        await(underTest.retry {
          actualCalls += 1
          Future.failed(new RuntimeException(""))
        }))

      actualCalls shouldBe 1
    }
  }
}
