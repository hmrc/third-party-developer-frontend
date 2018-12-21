/*
 * Copyright 2018 HM Revenue & Customs
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
import domain._
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Action
import service.AuditAction.{LoginFailedDueToInvalidEmail, LoginFailedDueToInvalidPassword, LoginFailedDueToLockedAccount, LoginSucceeded}
import service.{AuditAction, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html._

import scala.concurrent.Future

trait Auditing {
  val auditService: AuditService

  def audit(auditAction: AuditAction, data: Map[String, String])(implicit hc: HeaderCarrier) = {
    auditService.audit(auditAction, data)
  }

  def audit(auditAction: AuditAction, developer: Developer)(implicit hc: HeaderCarrier) = {
    auditService.audit(auditAction, Map("developerEmail" -> developer.email, "developerFullName" -> developer.displayedName))
  }
}

@Singleton
class UserLoginAccount @Inject()(val auditService: AuditService,
                                 val errorHandler: ErrorHandler,
                                 val sessionService: SessionService,
                                 implicit val appConfig: ApplicationConfig)
  extends LoggedOutController with LoginLogout with Auditing {

  import play.api.data._

  val loginForm: Form[LoginForm] = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form

  def login = loggedOutAction { implicit request =>
    Future.successful(Ok(signIn("Sign in", loginForm)))
  }

  def accountLocked = Action.async { implicit request =>
    for {
      _ <- tokenAccessor.extract(request)
        .map(sessionService.destroy)
        .getOrElse(Future.successful(()))
    } yield Locked(views.html.accountLocked())
  }

  def authenticate = Action.async {
    implicit request =>
      val requestForm = loginForm.bindFromRequest
      requestForm.fold(
        errors => Future.successful(BadRequest(signIn("Sign in", errors))),
        login => sessionService.authenticate(login.emailaddress, login.password) flatMap { session => {
          audit(LoginSucceeded, session.developer)
          gotoLoginSucceeded(session.sessionId)
        }
        } recover {
          case e: InvalidEmail =>
            audit(LoginFailedDueToInvalidEmail, Map("developerEmail" -> login.emailaddress))
            Unauthorized(signIn("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
          case e: InvalidCredentials =>
            audit(LoginFailedDueToInvalidPassword, Map("developerEmail" -> login.emailaddress))
            Unauthorized(signIn("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
          case e: LockedAccount =>
            audit(LoginFailedDueToLockedAccount, Map("developerEmail" -> login.emailaddress))
            Locked(signIn("Sign in", LoginForm.accountLocked(requestForm)))
          case e: UnverifiedAccount => Forbidden(signIn("Sign in", LoginForm.accountUnverified(requestForm, login.emailaddress)))
            .withSession("email" -> login.emailaddress)
        }
      )
  }
}
