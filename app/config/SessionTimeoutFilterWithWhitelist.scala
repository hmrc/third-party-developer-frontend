/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.{DateTime, DateTimeZone, Duration}
import play.api.mvc._

import scala.concurrent.Future
import uk.gov.hmrc.play.frontend.filters.{ MicroserviceFilterSupport, SessionTimeoutFilter }

case class WhitelistedCall(uri: String, method: String)

class SessionTimeoutFilterWithWhitelist(clock: () => DateTime = () => DateTime.now(DateTimeZone.UTC),
                                        timeoutDuration: Duration,
                                        additionalSessionKeysToKeep: Set[String] = Set.empty,
                                        onlyWipeAuthToken: Boolean = false, whitelistedCalls: Set[WhitelistedCall])
  extends SessionTimeoutFilter(clock, timeoutDuration, additionalSessionKeysToKeep, onlyWipeAuthToken)
    with Filter with MicroserviceFilterSupport {

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    if (whitelistedCalls.contains(WhitelistedCall(rh.path, rh.method))) f(rh)
    else super.apply(f)(rh)
  }

}
