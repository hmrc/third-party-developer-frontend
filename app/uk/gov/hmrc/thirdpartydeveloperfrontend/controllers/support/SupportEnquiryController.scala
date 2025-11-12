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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.TpdfeBaseController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class SupportEnquiryController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends TpdfeBaseController(mcc) {

  def supportEnquiryPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    successful(MovedPermanently(s"${appConfig.devhubSupportFrontendUrl}/devhub-support/"))
  }
}
