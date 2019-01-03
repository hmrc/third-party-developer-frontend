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
import domain._
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.LoginLogout
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{Action, Call}
import service.AuditAction.{LoginFailedDueToInvalidEmail, LoginFailedDueToInvalidPassword, LoginFailedDueToLockedAccount, LoginSucceeded}
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.html._

import scala.concurrent.Future
import scala.concurrent.Future.successful

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
                                 val applicationService: ApplicationService,
                                 implicit val appConfig: ApplicationConfig)
  extends LoggedOutController with LoginLogout with Auditing {

  import play.api.data._

  val loginForm: Form[LoginForm] = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form

  def login = loggedOutAction { implicit request =>
    successful(Ok(signIn("Sign in", loginForm)))
  }

  def accountLocked = Action.async { implicit request =>
    for {
      _ <- tokenAccessor.extract(request)
        .map(sessionService.destroy)
        .getOrElse(successful(()))
    } yield Locked(views.html.accountLocked())
  }

  def authenticate = Action.async { implicit request =>
    val requestForm = loginForm.bindFromRequest
    requestForm.fold(
      errors => successful(BadRequest(signIn("Sign in", errors))),
      login => sessionService.authenticate(login.emailaddress, login.password) flatMap { userAuthenticationResponse =>
        userAuthenticationResponse.session match {
          case Some(session) => audit(LoginSucceeded, session.developer)
                                gotoLoginSucceeded(session.sessionId)
          case None => successful(Ok(logInAccessCode(
            LoginTotpForm.form, login.emailaddress, userAuthenticationResponse.nonce.get)).withSession(request.session + ("emailAddress" -> login.emailaddress)))
        }
      } recover {
        case _: InvalidEmail =>
          audit(LoginFailedDueToInvalidEmail, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signIn("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: InvalidCredentials =>
          audit(LoginFailedDueToInvalidPassword, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signIn("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: LockedAccount =>
          audit(LoginFailedDueToLockedAccount, Map("developerEmail" -> login.emailaddress))
          Locked(signIn("Sign in", LoginForm.accountLocked(requestForm)))
        case _: UnverifiedAccount => Forbidden(signIn("Sign in", LoginForm.accountUnverified(requestForm, login.emailaddress)))
          .withSession("email" -> login.emailaddress)
      }
    )
  }

  def authenticateTotp = Action.async { implicit request =>
    LoginTotpForm.form.bindFromRequest.fold(
      errors => successful(BadRequest(logInAccessCode(errors, errors.data("email"), errors.data("nonce")))),
      validForm => sessionService.authenticateTotp(validForm.email, validForm.accessCode, validForm.nonce) flatMap { session =>
        audit(LoginSucceeded, session.developer)
        gotoLoginSucceeded(session.sessionId)
      } recover {
        case _: InvalidCredentials =>
          Unauthorized(logInAccessCode(
            LoginTotpForm.form.fill(validForm).withError("accessCode", "You have entered an incorrect access code"),
            validForm.email, validForm.nonce))
      }
    )
  }

  def get2SVHelpConfirmationPage() = loggedOutAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountNoAccessCode(Help2SVConfirmForm.form)))
  }

  def get2SVHelpCompletionPage() = loggedOutAction { implicit request =>
    Future.successful(Ok(views.html.protectAccountNoAccessCodeComplete()))
  }

  def confirm2SVHelp() = loggedOutAction { implicit request =>
    Help2SVConfirmForm.form.bindFromRequest.fold(form => {
      Future.successful(BadRequest(views.html.protectAccountNoAccessCode(form)))
    },
      form => {
        form.helpRemoveConfirm match {
          case Some("Yes") => applicationService.request2SVRemoval(request.session.get("emailAddress").getOrElse("")).
            map(_ => Ok(protectAccountNoAccessCodeComplete()))
          case _ => Future.successful(Ok(signIn("Sign in", loginForm)))
        }
      })
  }
}

final case class LoginTotpForm(accessCode: String, email: String, nonce: String)

object LoginTotpForm {
  def form: Form[LoginTotpForm] = Form(
    mapping(
      "accessCode" -> text.verifying(FormKeys.accessCodeInvalidKey, s => s.matches("^[0-9]{6}$")),
      "email" -> text,
      "nonce" -> text
    )(LoginTotpForm.apply)(LoginTotpForm.unapply)
  )
}