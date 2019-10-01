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
import domain.LoggedInState.LOGGED_IN
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.OptionalAuthElement
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.AnyContent
import service.{DeskproService, SessionService}
import views.html.{supportEnquiry, supportThankyou}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Support @Inject()(val deskproService: DeskproService,
                        val sessionService: SessionService,
                        val errorHandler: ErrorHandler,
                        val messagesApi: MessagesApi,
                        implicit val appConfig: ApplicationConfig)
                       (implicit ec: ExecutionContext)
  extends BaseController with OptionalAuthElement {

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  private def fullyLoggedInUser(implicit request: RequestWithAttributes[AnyContent] ) : Option[User] =
    loggedIn.filter(user => user.loggedInState == LOGGED_IN)

  def raiseSupportEnquiry = AsyncStack { implicit request: RequestWithAttributes[AnyContent] =>
    val prefilledForm = fullyLoggedInUser
      .fold(supportForm) { user =>
        supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email)).discardingErrors
      }
    Future.successful(Ok(supportEnquiry(fullyLoggedInUser.map(_.displayedName), prefilledForm)))
  }

  def submitSupportEnquiry = AsyncStack { implicit request =>
    val requestForm = supportForm.bindFromRequest
    val displayName = fullyLoggedInUser.map(_.displayedName)
    requestForm.fold(
      formWithErrors => Future.successful(BadRequest(supportEnquiry(displayName, formWithErrors))),
      formData => deskproService.submitSupportEnquiry(formData).map { _ => Redirect(routes.Support.thankyou().url, SEE_OTHER) })
  }

  def thankyou = AsyncStack { implicit request =>
    val displayName = fullyLoggedInUser.map(_.displayedName)
    Future.successful(Ok(supportThankyou("Thank you", displayName)))
  }
}
