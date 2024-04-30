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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.{CdsAccessIsNotRequiredView, CheckCdsAccessIsRequiredView, ChooseAPrivateApiView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

@Singleton
class CheckCdsAccessIsRequiredController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    chooseAPrivateApiView: ChooseAPrivateApiView,
    checkCdsAccessIsRequiredView: CheckCdsAccessIsRequiredView,
    cdsAccessIsNotRequiredView: CdsAccessIsNotRequiredView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) with SupportCookie {

  def checkCdsAccessIsRequiredPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    successful(Ok(
      checkCdsAccessIsRequiredView(
        fullyloggedInDeveloper,
        CheckCdsAccessIsRequiredForm.form,
        routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
      )
    ))
  }

  def submitCdsAccessIsRequired(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderChooseAPrivateApiErrorView(form: Form[CheckCdsAccessIsRequiredForm]) = {
      successful(BadRequest(
        checkCdsAccessIsRequiredView(
          fullyloggedInDeveloper,
          form,
          routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
        )
      ))
    }

    def handleValidForm(form: CheckCdsAccessIsRequiredForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)

      if (form.confirmCdsIntegration) successful(withSupportCookie(Redirect(routes.ApplyForPrivateApiAccessController.applyForPrivateApiAccessPage()), sessionId))
      else successful(Redirect(routes.CheckCdsAccessIsRequiredController.cdsAccessIsNotRequiredPage()))
    }

    def handleInvalidForm(formWithErrors: Form[CheckCdsAccessIsRequiredForm]): Future[Result] =
      renderChooseAPrivateApiErrorView(formWithErrors)

    CheckCdsAccessIsRequiredForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def cdsAccessIsNotRequiredPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    successful(Ok(
      cdsAccessIsNotRequiredView(
        fullyloggedInDeveloper,
        routes.CheckCdsAccessIsRequiredController.checkCdsAccessIsRequiredPage().url
      )
    ))
  }
}
