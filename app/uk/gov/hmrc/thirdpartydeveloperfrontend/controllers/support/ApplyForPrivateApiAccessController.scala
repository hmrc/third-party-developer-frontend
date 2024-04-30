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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.{ApplyForPrivateApiAccessView, ChooseAPrivateApiView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

@Singleton
class ApplyForPrivateApiAccessController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    applyForPrivateApiAccessView: ApplyForPrivateApiAccessView,
    chooseAPrivateApiView: ChooseAPrivateApiView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) with SupportCookie {

  def applyForPrivateApiAccessPage: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderPage(flow: SupportFlow) =
      flow.privateApi.fold(
        Redirect(routes.ChooseAPrivateApiController.chooseAPrivateApiPage())
      )(apiName =>
        Ok(
          applyForPrivateApiAccessView(
            fullyloggedInDeveloper,
            apiName,
            ApplyForPrivateApiAccessForm.form,
            routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
          )
        )
      )

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).map(renderPage)
  }

  def submitApplyForPrivateApiAccess: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderErrorView(form: Form[ApplyForPrivateApiAccessForm]) = {
      Future.successful(BadRequest(
        applyForPrivateApiAccessView(
          fullyloggedInDeveloper,
          "Business Rates 2.0", // TODO - change
          form,
          routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
        )
      ))
    }

    def handleValidForm(form: ApplyForPrivateApiAccessForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)

      for {
        flow <- supportService.getSupportFlow(sessionId)
        ticket <- supportService.submitTicket(flow, form)
      } yield withSupportCookie(Redirect(routes.SupportDetailsController.supportConfirmationPage()), sessionId)
    }

    def handleInvalidForm(formWithErrors: Form[ApplyForPrivateApiAccessForm]): Future[Result] =
      renderErrorView(formWithErrors)

    ApplyForPrivateApiAccessForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
