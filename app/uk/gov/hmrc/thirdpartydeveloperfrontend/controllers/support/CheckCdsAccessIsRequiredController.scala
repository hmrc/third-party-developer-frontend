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
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import views.html.support.{CdsAccessIsNotRequiredView, CheckCdsAccessIsRequiredView, ChooseAPrivateApiView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
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
    def renderPage(flow: SupportFlow) =
      flow.privateApi match {
        case Some(SupportData.ChooseCDS.id) =>
          Ok(
            checkCdsAccessIsRequiredView(
              fullyloggedInDeveloper,
              CheckCdsAccessIsRequiredForm.form,
              routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
            )
          )
        case _                              => Redirect(routes.ChooseAPrivateApiController.chooseAPrivateApiPage())
      }

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).map(renderPage)
  }

  def submitCdsAccessIsRequired(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def handleValidForm(flow: SupportFlow, sessionId: String)(form: CheckCdsAccessIsRequiredForm): Result = {
      if (form.confirmCdsIntegration == "yes")
        withSupportCookie(Redirect(routes.ApplyForPrivateApiAccessController.applyForPrivateApiAccessPage()), sessionId)
      else
        Redirect(routes.CheckCdsAccessIsRequiredController.cdsAccessIsNotRequiredPage())
    }

    def handleInvalidForm(flow: SupportFlow)(formWithErrors: Form[CheckCdsAccessIsRequiredForm]): Result = {
      BadRequest(
        checkCdsAccessIsRequiredView(
          fullyloggedInDeveloper,
          formWithErrors,
          routes.ChooseAPrivateApiController.chooseAPrivateApiPage().url
        )
      )
    }

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).map { flow =>
      if (flow.entrySelection == SupportData.PrivateApiDocumentation.id && flow.privateApi == Some(SupportData.ChooseCDS.id)) {
        CheckCdsAccessIsRequiredForm.form.bindFromRequest().fold(handleInvalidForm(flow), handleValidForm(flow, sessionId))
      } else {
        Redirect(routes.ChooseAPrivateApiController.chooseAPrivateApiPage())
      }
    }
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
