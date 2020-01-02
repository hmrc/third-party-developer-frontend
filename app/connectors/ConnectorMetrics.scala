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

import com.kenshoo.play.metrics.MetricsImpl
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed trait Timer {
  def stop(): Unit
}

trait ConnectorMetrics {
  def record[A](api: API)(f: => Future[A])(implicit ec: ExecutionContext): Future[A]
}

@Singleton
class ConnectorMetricsImpl @Inject()(lifecycle: ApplicationLifecycle, config: Configuration) extends MetricsImpl(lifecycle, config) with ConnectorMetrics {
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
    defaultRegistry.counter(api.name ++ "-failed-counter").inc()

  private def recordSuccess(api: API): Unit =
    defaultRegistry.counter(api.name ++ "-success-counter").inc()

  private def startTimer(api: API): Timer = {
    val context = defaultRegistry.timer(api.name ++ "-timer").time()

    new Timer {
      def stop: Unit = context.stop()
    }
  }
}

@Singleton
class NoopConnectorMetrics extends ConnectorMetrics {
  def record[A](api: API)(f: => Future[A])(implicit ec: ExecutionContext): Future[A] = f
}
