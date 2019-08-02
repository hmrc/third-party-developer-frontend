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

import java.util.UUID

import akka.actor.ActorSystem
import config.ApplicationConfig
import connectors.{ApiSubscriptionFieldsConnector, ProxiedHttpClient}
import domain.ApiSubscriptionFields._
import domain.Environment
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.{BadRequestException, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec
import helpers.Retries

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RetriesSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  trait Setup {
    val mockAppConfig : ApplicationConfig = mock[ApplicationConfig]
    def underTest = new RetryTestConnector(mockAppConfig)
  }

  class RetryTestConnector(val appConfig:  ApplicationConfig) extends Retries {
    implicit val ec: ExecutionContext = global
    override protected def actorSystem: ActorSystem = ActorSystem("test-actor-system")
  }

  "Retries" should {

    "when retry logic is enabled should retry if call returns 400 Bad Request" in new Setup {

      var retries = 0

      val response = await (underTest.retry {

        if (retries <= 1) {
          Future.failed(new BadRequestException(""))
          retries += 1
        }
        else Future.successful(())
      })

      response shouldBe ()
      retries shouldBe 1

    }

    "Retry three times on Bad Request" in new Setup {
      val expectedRetries = 3
      when(mockAppConfig.retryCount).thenReturn(expectedRetries)
      var actualRetries = 0

      val response = await (underTest.retry {

        if (actualRetries < expectedRetries) {
          actualRetries += 1
          Future.failed(new BadRequestException(""))
        }
        else Future.successful(())
      })

      response shouldBe ()
      actualRetries shouldBe expectedRetries
    }
  }
}
