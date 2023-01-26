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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html.{SupportEnquiryView, SupportThankyouView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService}

@Singleton
class Support @Inject() (
    val deskproService: DeskproService,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    supportEnquiryView: SupportEnquiryView,
    supportThankyouView: SupportThankyouView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends BaseController(mcc) {

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  private def fullyloggedInDeveloper(implicit request: MaybeUserRequest[AnyContent]): Option[DeveloperSession] =
    request.developerSession.filter(_.loggedInState.isLoggedIn)

  def raiseSupportEnquiry: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val prefilledForm = fullyloggedInDeveloper
      .fold(supportForm) { user =>
        supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email)).discardingErrors
      }
    Future.successful(Ok(supportEnquiryView(fullyloggedInDeveloper.map(_.displayedName), prefilledForm)))

  }

  def submitSupportEnquiry = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val requestForm = supportForm.bindFromRequest
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    val userId      = fullyloggedInDeveloper.map(_.developer.userId.asText).getOrElse("")
    requestForm.fold(
      formWithErrors => Future.successful(BadRequest(supportEnquiryView(displayName, formWithErrors))),
      formData => deskproService.submitSupportEnquiry(userId, formData).map { _ => Redirect(routes.Support.thankyou.url, SEE_OTHER) }
    )
  }

  def thankyou = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    Future.successful(Ok(supportThankyouView("Thank you", displayName)))
  }
}
