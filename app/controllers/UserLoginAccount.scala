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
import model.MfaMandateDetails
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import service.AuditAction._
import service._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import views.html._
import views.html.protectaccount._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

trait Auditing {
  val auditService: AuditService

  def audit(auditAction: AuditAction, data: Map[String, String])(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, data)
  }

  def audit(auditAction: AuditAction, developer: DeveloperSession)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    auditService.audit(auditAction, Map("developerEmail" -> developer.email, "developerFullName" -> developer.displayedName))
  }
}

@Singleton
class UserLoginAccount @Inject()(val auditService: AuditService,
                                 val errorHandler: ErrorHandler,
                                 val sessionService: SessionService,
                                 val applicationService: ApplicationService,
                                 val messagesApi: MessagesApi,
                                 val mfaMandateService: MfaMandateService)
                                (implicit ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedOutController with LoginLogout with Auditing {

  import play.api.data._

  val loginForm: Form[LoginForm] = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form


  def login: Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(signIn("Sign in", loginForm)))
  }

  def accountLocked: Action[AnyContent] = Action.async { implicit request =>
    for {
      _ <- tokenAccessor.extract(request)
        .map(sessionService.destroy)
        .getOrElse(successful(()))
    } yield Locked(views.html.accountLocked())
  }

  private def routeToLoginOr2SV(login: LoginForm,
                                userAuthenticationResponse: UserAuthenticationResponse,
                                showAdminMfaMandateMessage: Boolean,
                                playSession: play.api.mvc.Session)(implicit request: Request[AnyContent]): Future[Result] = {
    def mfaMandateDetails = MfaMandateDetails(showAdminMfaMandateMessage, mfaMandateService.daysTillAdminMfaMandate.getOrElse(0))

    userAuthenticationResponse.session match {

      case Some(session) if session.loggedInState == LoggedInState.LOGGED_IN => audit(LoginSucceeded, DeveloperSession.apply(session))
        // Retain the Play session so that 'access_uri', if set, is used at the end of the 2SV reminder flow
        gotoLoginSucceeded(session.sessionId, successful(Ok(add2SV(mfaMandateDetails))
          .withSession(playSession)))

      case None => successful(Ok(logInAccessCode(ProtectAccountForm.form))
        .withSession(playSession + ("emailAddress" -> login.emailaddress) + ("nonce" -> userAuthenticationResponse.nonce.get)))

      case Some(session) if session.loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA =>
        gotoLoginSucceeded(session.sessionId, successful(Redirect(routes.ProtectAccount.getProtectAccount().url)
          .withSession(playSession)))
    }
  }

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    val requestForm = loginForm.bindFromRequest

    requestForm.fold(
      errors => successful(BadRequest(signIn("Sign in", errors))),
      login => {

        for {
          // TODO don't call TPA admin check twice
          userAuthenticationResponse <- sessionService.authenticate(login.emailaddress, login.password)
          showAdminMfaMandateMessage <- mfaMandateService.showAdminMfaMandatedMessage(login.emailaddress)
          response <- routeToLoginOr2SV(login, userAuthenticationResponse, showAdminMfaMandateMessage, request.session)
        } yield response
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

  def authenticateTotp: Action[AnyContent] = Action.async { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(
      errors => successful(BadRequest(logInAccessCode(errors))),
      validForm => {
        val email = request.session.get("emailAddress").get
        sessionService.authenticateTotp(email, validForm.accessCode, request.session.get("nonce").get) flatMap { session =>
          audit(LoginSucceeded, DeveloperSession.apply(session))
          gotoLoginSucceeded(session.sessionId)
        } recover {
          case _: InvalidCredentials =>
            audit(LoginFailedDueToInvalidAccessCode, Map("developerEmail" -> email))
            Unauthorized(logInAccessCode(ProtectAccountForm.form.fill(validForm).withError("accessCode", "You have entered an incorrect access code")))
        }
      }
    )
  }

  def get2SVHelpConfirmationPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCode()))
  }

  def get2SVHelpCompletionPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeComplete()))
  }

  def confirm2SVHelp(): Action[AnyContent] = loggedOutAction { implicit request =>
    applicationService.request2SVRemoval(request.session.get("emailAddress").getOrElse("")).map(_ => Ok(protectAccountNoAccessCodeComplete()))
  }

  def get2SVNotSetPage(): Action[AnyContent] = Action.async { implicit request =>
    successful(Ok(userDidNotAdd2SV()))
  }
}
