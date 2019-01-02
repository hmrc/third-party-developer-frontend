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

package config

import akka.stream.Materializer
import javax.inject.Inject
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.filters.frontend.{SessionTimeoutFilter, SessionTimeoutFilterConfig}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WhitelistedCall(uri: String, method: String)

class SessionTimeoutFilterWithWhitelist @Inject()(config: SessionTimeoutFilterConfig)(implicit ec: ExecutionContext, override val mat: Materializer)
  extends SessionTimeoutFilter(config) {

  val whitelistedCalls: Set[WhitelistedCall] = Set(WhitelistedCall("/login", "GET"), WhitelistedCall("/login", "POST"))

  override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    if (whitelistedCalls.contains(WhitelistedCall(rh.path, rh.method))) f(rh)
    else super.apply(f)(rh)
  }
}
