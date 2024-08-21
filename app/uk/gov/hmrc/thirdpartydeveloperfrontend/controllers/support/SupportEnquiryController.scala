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

import views.html.{SupportEnquiryView, SupportThankyouView}

import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class SupportEnquiryController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportEnquiryView: SupportEnquiryView,
    supportThankyouView: SupportThankyouView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) {

  def supportEnquiryPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    lazy val prefilledForm = fullyloggedInDeveloper
      .fold[Form[SupportEnquiryForm]](supportForm) { userSession =>
        supportForm.bind(Map("fullname" -> userSession.developer.displayedName, "emailaddress" -> userSession.developer.email.text)).discardingErrors
      }

    successful(Ok(supportEnquiryView(fullyloggedInDeveloper.map(_.developer.displayedName), prefilledForm)))
  }

  def submitSupportEnquiry() = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val requestForm = supportForm.bindFromRequest()
    val displayName = fullyloggedInDeveloper.map(_.developer.displayedName)
    val userId      = fullyloggedInDeveloper.map(_.developer.userId)

    requestForm.fold(
      formWithErrors => {
        logSpamSupportRequest(formWithErrors)
        successful(BadRequest(supportEnquiryView(displayName, formWithErrors)))
      },
      formData => deskproService.submitSupportEnquiry(userId, formData).map { _ => Redirect(routes.SupportEnquiryController.thankyouPage(), SEE_OTHER) }
    )
  }

  def thankyouPage() = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val displayName = fullyloggedInDeveloper.map(_.developer.displayedName)
    successful(Ok(supportThankyouView("Thank you", displayName)))
  }

  private def logSpamSupportRequest(form: Form[SupportEnquiryForm]) = {
    form.errors("comments").map((formError: FormError) => {
      if (formError.message == FormKeys.commentsSpamKey.value) {
        logger.info("Spam support request attempted")
      }
    })
  }
}
