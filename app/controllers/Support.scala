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
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.OptionalAuthElement
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import service.{DeskproService, SessionService}
import views.html.{supportEnquiry, supportThankyou}

import scala.concurrent.Future

@Singleton
class Support @Inject()(val deskproService: DeskproService,
                        val sessionService: SessionService,
                        val errorHandler: ErrorHandler,
                        implicit val appConfig: ApplicationConfig)
  extends BaseController with OptionalAuthElement {

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  def raiseSupportEnquiry = AsyncStack { implicit request =>
    val prefilledForm = loggedIn.fold(supportForm) { user =>
      supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email)).discardingErrors
    }
    Future.successful(Ok(supportEnquiry(loggedIn.map(_.displayedName), prefilledForm)))
  }

  def submitSupportEnquiry = AsyncStack { implicit request =>
    val requestForm = supportForm.bindFromRequest
    val displayName = loggedIn.map(_.displayedName)
    requestForm.fold(
      formWithErrors => Future.successful(BadRequest(supportEnquiry(displayName, formWithErrors))),
      formData => deskproService.submitSupportEnquiry(formData).map { _ => Redirect(routes.Support.thankyou().url, SEE_OTHER) })
  }

  def thankyou = AsyncStack { implicit request =>
    val displayName = loggedIn.map(_.displayedName)
    Future.successful(Ok(supportThankyou("Thank you", displayName)))
  }
}
