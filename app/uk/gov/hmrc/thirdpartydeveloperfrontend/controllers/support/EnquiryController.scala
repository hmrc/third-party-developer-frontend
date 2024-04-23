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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import javax.inject.{Inject, Singleton}

import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner

import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys

import views.html.SupportEnquiryView
import views.html.support.LandingPageView

@Singleton
class EnquiryController @Inject() (
    mcc: MessagesControllerComponents,
    val deskproService: DeskproService,
    landingPageView: LandingPageView,
    supportEnquiryView: SupportEnquiryView,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    supportService: SupportService,
    val errorHandler: ErrorHandler
 )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) {

  private val thankyouPage = uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.SupportController.thankyou()

  def raiseSupportEnquiry(useNewSupport: Boolean): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    lazy val prefilledForm = fullyloggedInDeveloper
      .fold[Form[SupportEnquiryForm]](supportForm) { user =>
        supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email.text)).discardingErrors
      }

    if (useNewSupport)
      Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
    else
      Future.successful(Ok(supportEnquiryView(fullyloggedInDeveloper.map(_.displayedName), prefilledForm)))
  }
  
  def submitSupportEnquiry = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val requestForm = supportForm.bindFromRequest()
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    val userId      = fullyloggedInDeveloper.map(_.developer.userId)

    requestForm.fold(
      formWithErrors => {
        logSpamSupportRequest(formWithErrors)
        Future.successful(BadRequest(supportEnquiryView(displayName, formWithErrors)))
      },
      formData => deskproService.submitSupportEnquiry(userId, formData).map { _ => Redirect(thankyouPage, SEE_OTHER) }
    )
  }

  private def logSpamSupportRequest(form: Form[SupportEnquiryForm]) = {
    form.errors("comments").map((formError: FormError) => {
      if (formError.message == FormKeys.commentsSpamKey) {
        logger.info("Spam support request attempted")
      }
    })
  }
}
