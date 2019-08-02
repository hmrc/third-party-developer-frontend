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

package helpers

import akka.actor.ActorSystem
import akka.pattern.after
import com.typesafe.config.Config
import config.ApplicationConfig
import uk.gov.hmrc.http.BadRequestException
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Retries {

  protected def actorSystem: ActorSystem

  protected def appConfig: ApplicationConfig

  implicit val ec: ExecutionContext

  def retry[A](block: => Future[A]): Future[A] = {
    def loop(retries: Int)(block: => Future[A]): Future[A] = {
      block.recoverWith {
        case ex: BadRequestException if (retries > 0) => {
          val delay = 500.millis
          val attempt = appConfig.retryCount - retries + 1
          Logger.warn(s"Retry attempt $attempt of ${appConfig.retryCount} in $delay due to '${ex.getMessage}'")
          after(delay, actorSystem.scheduler)(loop(retries - 1)(block))
        }
      }
    }
    loop(appConfig.retryCount)(block)
  }

}
