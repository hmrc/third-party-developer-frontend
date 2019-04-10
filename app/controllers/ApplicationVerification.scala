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

package controllers

import config.{ApplicationConfig, ErrorHandler}
import domain.ApplicationVerificationFailed
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Action
import service.{ApplicationService, SessionService}

@Singleton
class ApplicationVerification @Inject()(val service: ApplicationService,
                                        val sessionService: SessionService,
                                        val errorHandler: ErrorHandler,
                                        implicit val appConfig: ApplicationConfig) extends LoggedOutController {

  def verifyUplift(code: String) = Action.async { implicit request =>
    service.verify(code) map { _ => Ok(views.html.applicationVerification(success = true))
    } recover {
      case _: ApplicationVerificationFailed =>
        Ok(views.html.applicationVerification(success = false))
    }
  }
}
