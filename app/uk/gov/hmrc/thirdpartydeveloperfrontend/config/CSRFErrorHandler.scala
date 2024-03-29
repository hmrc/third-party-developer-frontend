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

import javax.inject.Inject
import scala.concurrent.Future

import play.api.http.Status.FORBIDDEN
import play.api.mvc.Results.Redirect
import play.api.mvc.{RequestHeader, Result}
import play.filters.csrf.CSRF

class CSRFErrorHandler @Inject() (errorHandler: ErrorHandler) extends CSRF.ErrorHandler {

  override def handle(req: RequestHeader, msg: String): Future[Result] = {
    val login = uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.UserLoginAccount.login()

    if (req.path == login.url) Future.successful(Redirect(login))
    else {
      errorHandler.onClientError(req, FORBIDDEN, msg)
    }
  }
}
