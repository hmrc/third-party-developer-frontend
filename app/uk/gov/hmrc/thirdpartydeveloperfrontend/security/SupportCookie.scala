/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.security

import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseController

trait SupportCookie extends FrontendHeaderCarrierProvider with CookieEncoding with ApplicationLogger {
  self: BaseController =>

  def extractSupportSessionIdFromCookie(request: RequestHeader): Option[String] = {
    request.cookies.get(supportCookieName) match {
      case Some(cookie) => decodeCookie(cookie.value)
      case _            => None
    }
  }

  def withSupportCookie(result: Result, sessionId: String): Result = {
    result.withCookies(createSupportCookie(sessionId))
  }
}
