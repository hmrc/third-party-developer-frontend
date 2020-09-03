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

package controllers

import config.{ApplicationConfig, ErrorHandler}
import domain.models.applications.{ApplicationVerificationFailed, ApplicationVerificationSuccessful}
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import service.{ApplicationService, SessionService}
import views.html.ApplicationVerificationView

import scala.concurrent.ExecutionContext

@Singleton
class ApplicationVerification @Inject()(service: ApplicationService,
                                        val sessionService: SessionService,
                                        val errorHandler: ErrorHandler,
                                        mcc: MessagesControllerComponents,
                                        val cookieSigner: CookieSigner,
                                        applicationVerificationView: ApplicationVerificationView)
                                       (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedOutController(mcc) {

  def verifyUplift(code: String) = Action.async { implicit request =>
    service.verify(code) map {
      case ApplicationVerificationSuccessful => Ok(applicationVerificationView(success = true))
      case ApplicationVerificationFailed     => Ok(applicationVerificationView(success = false))
    }
  }
}
