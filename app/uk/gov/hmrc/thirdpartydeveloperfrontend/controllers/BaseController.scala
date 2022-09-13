/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.DevHubAuthorization
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

import scala.concurrent.ExecutionContext

abstract class BaseController(mcc: MessagesControllerComponents) extends FrontendController(mcc) with DevHubAuthorization with HeaderEnricher with WithUnsafeDefaultFormBinding {
  val errorHandler: ErrorHandler
  val sessionService: SessionService

  implicit def ec: ExecutionContext

  implicit val appConfig: ApplicationConfig
}
