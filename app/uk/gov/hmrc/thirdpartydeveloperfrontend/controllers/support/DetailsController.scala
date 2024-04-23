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

import play.api.data.Form
import play.api.libs.crypto.CookieSigner

import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler

import views.html.SupportEnquiryView
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import views.html.support.SupportPageDetailView
import java.util.UUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApiSupportDetailsForm
import play.api.mvc.Result

@Singleton
class DetailsController @Inject() (
    mcc: MessagesControllerComponents,
    val deskproService: DeskproService,
    supportPageDetailView: SupportPageDetailView,
    supportEnquiryView: SupportEnquiryView,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    supportService: SupportService,
    val errorHandler: ErrorHandler
 )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) {

  private val apiSupportAction = uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.SupportController.apiSupportAction()
  
  private val supportConfirmationPage = uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.SupportController.supportConfirmationPage()

  def supportDetailsPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderPage(flow: SupportFlow) =
      Ok(
        supportPageDetailView(
          fullyloggedInDeveloper,
          ApiSupportDetailsForm.form,
          apiSupportAction.url,
          flow
        )
      )

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).map(renderPage)
  }

  def supportDetailsAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderApiSupportDetailsPageErrorView(flow: SupportFlow)(form: Form[ApiSupportDetailsForm]) = {
      Future.successful(
        BadRequest(
          supportPageDetailView(
            fullyloggedInDeveloper,
            form,
            apiSupportAction.url,
            flow
          )
        )
      )
    }

    def handleValidForm(sessionId: String, flow: SupportFlow)(form: ApiSupportDetailsForm): Future[Result] = {
      supportService.submitTicket(flow, form).map(_ =>
        withSupportCookie(Redirect(supportConfirmationPage), sessionId)
      )
    }

    def handleInvalidForm(flow: SupportFlow)(formWithErrors: Form[ApiSupportDetailsForm]): Future[Result] = {
      renderApiSupportDetailsPageErrorView(flow)(formWithErrors)
    }

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)

    supportService.getSupportFlow(sessionId).flatMap(flow =>
      ApiSupportDetailsForm.form.bindFromRequest().fold(handleInvalidForm(flow), handleValidForm(sessionId, flow))
    )
  }
}
