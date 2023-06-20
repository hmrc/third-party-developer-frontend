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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.kenshoo.play.metrics.Metrics

import uk.gov.hmrc.play.http.metrics.common.API

sealed trait Timer {
  def stop(): Unit
}

trait ConnectorMetrics {
  def record[A](api: API)(f: => Future[A])(implicit ec: ExecutionContext): Future[A]
}

@Singleton
class ConnectorMetricsImpl @Inject() (metrics: Metrics) extends ConnectorMetrics {

  def record[A](api: API)(f: => Future[A])(implicit ec: ExecutionContext): Future[A] = {
    val timer = startTimer(api)

    f.andThen {
      case _ => timer.stop()
    }.andThen {
      case Success(_) => recordSuccess(api)
      case Failure(_) => recordFailure(api)
    }
  }

  private def recordFailure(api: API): Unit =
    metrics.defaultRegistry.counter(api.name ++ "-failed-counter").inc()

  private def recordSuccess(api: API): Unit =
    metrics.defaultRegistry.counter(api.name ++ "-success-counter").inc()

  private def startTimer(api: API): Timer = {
    val context = metrics.defaultRegistry.timer(api.name ++ "-timer").time()

    new Timer {
      def stop(): Unit = context.stop()
    }
  }
}

@Singleton
class NoopConnectorMetrics extends ConnectorMetrics {
  def record[A](api: API)(f: => Future[A])(implicit ec: ExecutionContext): Future[A] = f
}
