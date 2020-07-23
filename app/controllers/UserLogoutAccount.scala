/*
 * Copyright 2020 HM Revenue & Customs
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
import domain.TicketId
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents, MessagesRequest}
import security.ExtendedDevHubAuthorization
import service.{ApplicationService, DeskproService, SessionService}
import views.html.{LogoutConfirmationView, SignoutSurveyView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserLogoutAccount @Inject()(val deskproService: DeskproService,
                                  val sessionService: SessionService,
                                  val applicationService: ApplicationService,
                                  val errorHandler: ErrorHandler,
                                  mcc: MessagesControllerComponents,
                                  val cookieSigner: CookieSigner,
                                  signoutSurveyView: SignoutSurveyView,
                                  logoutConfirmationView: LogoutConfirmationView
                                 )
                                 (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedInController(mcc) with ExtendedDevHubAuthorization {

  def logoutSurvey = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    val page = signoutSurveyView("Are you sure you want to sign out?", SignOutSurveyForm.form)

    Future.successful(Ok(page))
  }

  def logoutSurveyAction = atLeastPartLoggedInEnablingMfaAction { implicit request =>
    SignOutSurveyForm.form.bindFromRequest.value match {
      case Some(form) =>
        val res: Future[TicketId] = deskproService.submitSurvey(form)
        res.onFailure {
          case _ => Logger.error("Failed to create deskpro ticket")
        }

        applicationService.userLogoutSurveyCompleted(form.email, form.name, form.rating.getOrElse("").toString, form.improvementSuggestions).flatMap(_ => {
          Future.successful(Redirect(controllers.routes.UserLogoutAccount.logout()))
        })
      case None =>
        Logger.error("Survey form invalid.")
        Future.successful(Redirect(controllers.routes.UserLogoutAccount.logout()))
    }
  }

  def logout = Action.async { implicit request: MessagesRequest[AnyContent] =>
    destroySession(request)
      .getOrElse(Future.successful(()))
      .map(_ => Ok(logoutConfirmationView()).withNewSession)
      .map(removeCookieFromResult)
  }
}
