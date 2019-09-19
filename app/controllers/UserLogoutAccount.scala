/*
 * Copyright 2019 HM Revenue & Customs
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
import jp.t2v.lab.play2.auth.LoginLogout
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request}
import service.{ApplicationService, DeskproService, SessionService}
import views.html.signoutSurvey

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserLogoutAccount @Inject()(val deskproService: DeskproService,
                                  val sessionService: SessionService,
                                  val applicationService: ApplicationService,
                                  val errorHandler: ErrorHandler,
                                  val messagesApi: MessagesApi)
                                 (implicit ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController with LoginLogout {

  def logoutSurvey = loggedInAction { implicit request =>
    val page = signoutSurvey("Are you sure you want to sign out?", SignOutSurveyForm.form)

    Future.successful(Ok(page))
  }

  def logoutSurveyAction = loggedInAction { implicit request: RequestWithAttributes[AnyContent] =>

    SignOutSurveyForm.form.bindFromRequest.value match {
      case Some(form) => {
        val res: Future[TicketId] = deskproService.submitSurvey(form)
        res.onFailure {
          case _ => Logger.error(s"Failed to create deskpro ticket")
//            applicationService.userLogoutSurveyCompleted(form.email, form.name, form.rating.toString, form.improvementSuggestions)
        }
        applicationService.userLogoutSurveyCompleted(form.email, form.name, form.rating.getOrElse("").toString, form.improvementSuggestions).flatMap(_ => {
          Future.successful(Redirect(controllers.routes.UserLogoutAccount.logout()))
        })
      }
      case None => {
        Logger.error(s"Survey form invalid.")
        Future.successful(Redirect(controllers.routes.UserLogoutAccount.logout()))
      }
    }
  }

  def logout = Action.async { implicit request: Request[AnyContent] =>
    gotoLogoutSucceeded {
      for {
        _ <- tokenAccessor.extract(request)
          .map(sessionService.destroy)
          .getOrElse(Future.successful(()))
      } yield Ok(views.html.logoutConfirmation()).withNewSession
    }
  }
}
