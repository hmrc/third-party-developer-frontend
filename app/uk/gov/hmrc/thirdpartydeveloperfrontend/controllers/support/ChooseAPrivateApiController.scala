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

import views.html.support.ChooseAPrivateApiView

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

@Singleton
class ChooseAPrivateApiController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    val deskproService: DeskproService,
    supportService: SupportService,
    chooseAPrivateApiView: ChooseAPrivateApiView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends AbstractController(mcc) with SupportCookie {

  def chooseAPrivateApiPage: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    successful(Ok(
      chooseAPrivateApiView(
        fullyloggedInDeveloper,
        ChooseAPrivateApiForm.form,
        routes.HelpWithUsingAnApiController.helpWithUsingAnApiPage().url
      )
    ))
  }

  def submitChoiceOfPrivateApi: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderChooseAPrivateApiErrorView(form: Form[ChooseAPrivateApiForm]) = {
      successful(BadRequest(
        chooseAPrivateApiView(
          fullyloggedInDeveloper,
          form,
          routes.HelpWithUsingAnApiController.helpWithUsingAnApiPage().url
        )
      ))
    }

    def handleValidForm(form: ChooseAPrivateApiForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)

      form.chosenApiName match {
        case SupportData.ChooseBusinessRates.text =>
          Future.successful(withSupportCookie(Redirect(routes.ApplyForPrivateApiAccessController.applyForPrivateApiAccessPage()), sessionId))
        case SupportData.ChooseCDS.text           => Future.successful(withSupportCookie(Redirect(routes.ApplyForPrivateApiAccessController.applyForPrivateApiAccessPage()), sessionId))
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChooseAPrivateApiForm]): Future[Result] =
      renderChooseAPrivateApiErrorView(formWithErrors)

    ChooseAPrivateApiForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
