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
import views.html.support.SupportEnquiryInitialChoiceView
import play.api.mvc.Result
import java.util.UUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SupportData
import views.html.SupportThankyouView

@Singleton
class SupportEnquiryController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    supportEnquiryInitialChoiceView: SupportEnquiryInitialChoiceView,
    supportEnquiryView: SupportEnquiryView,
    supportThankyouView: SupportThankyouView,
 )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) {

  def supportEnquiryPage(useNewSupport: Boolean): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    lazy val prefilledForm = fullyloggedInDeveloper
      .fold[Form[SupportEnquiryForm]](supportForm) { user =>
        supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email.text)).discardingErrors
      }

    if (useNewSupport)
      Future.successful(Ok(supportEnquiryInitialChoiceView(fullyloggedInDeveloper, InitialChoiceForm.form)))
    else
      Future.successful(Ok(supportEnquiryView(fullyloggedInDeveloper.map(_.displayedName), prefilledForm)))
  }

  def submitInitialChoice(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def handleValidForm(form: InitialChoiceForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      supportService.createFlow(sessionId, form.initialChoice)
      form.initialChoice match {
        case SupportData.UsingAnApi.id  => Future.successful(withSupportCookie(Redirect(routes.HelpWithUsingAnApiController.initialChoicePage()), sessionId))
        case _                          => Future.successful(withSupportCookie(Redirect(routes.SupportDetailsController.supportDetailsPage()), sessionId))
      }
    }

    def handleInvalidForm(formWithErrors: Form[InitialChoiceForm]): Future[Result] = {
      Future.successful(BadRequest(supportEnquiryInitialChoiceView(fullyloggedInDeveloper, formWithErrors)))
    }

    InitialChoiceForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
  
  /* Old route */
  def submitSupportEnquiry() = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val requestForm = supportForm.bindFromRequest()
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    val userId      = fullyloggedInDeveloper.map(_.developer.userId)

    requestForm.fold(
      formWithErrors => {
        logSpamSupportRequest(formWithErrors)
        Future.successful(BadRequest(supportEnquiryView(displayName, formWithErrors)))
      },
      formData => deskproService.submitSupportEnquiry(userId, formData).map { _ => Redirect(routes.SupportEnquiryController.thankyouPage(), SEE_OTHER) }
    )
  }

  def thankyouPage() = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    Future.successful(Ok(supportThankyouView("Thank you", displayName)))
  }
  
  private def logSpamSupportRequest(form: Form[SupportEnquiryForm]) = {
    form.errors("comments").map((formError: FormError) => {
      if (formError.message == FormKeys.commentsSpamKey) {
        logger.info("Spam support request attempted")
      }
    })
  }
}
