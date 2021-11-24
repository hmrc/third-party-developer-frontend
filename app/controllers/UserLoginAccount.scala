/*
 * Copyright 2021 HM Revenue & Customs
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
import domain.models.connectors.UserAuthenticationResponse
import domain.models.developers.DeveloperSession
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result, Session => PlaySession}
import service._
import service.AuditAction._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import views.html._
import views.html.protectaccount._
import controllers.profile.ProtectAccountForm
import domain.models.controllers.MfaMandateDetails

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
                                 val applicationService: ApplicationService,
                                 val subscriptionFieldsService: SubscriptionFieldsService,
                                 val sessionService: SessionService,
                                 mcc: MessagesControllerComponents,
                                 val mfaMandateService: MfaMandateService,
                                 val cookieSigner : CookieSigner,
                                 signInView: SignInView,
                                 accountLockedView: AccountLockedView,
                                 logInAccessCodeView: LogInAccessCodeView,
                                 protectAccountNoAccessCodeView: ProtectAccountNoAccessCodeView,
                                 protectAccountNoAccessCodeCompleteView: ProtectAccountNoAccessCodeCompleteView,
                                 userDidNotAdd2SVView: UserDidNotAdd2SVView,
                                 add2SVView: Add2SVView
                                )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends LoggedOutController(mcc) with Auditing {

  import play.api.data._

  val loginForm: Form[LoginForm] = LoginForm.form
  val changePasswordForm: Form[ChangePasswordForm] = ChangePasswordForm.form

  def login: Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(signInView("Sign in", loginForm)))
  }

  def accountLocked: Action[AnyContent] = Action.async { implicit request =>
    for {
      _ <- extractSessionIdFromCookie(request)
        .map(sessionService.destroy)
        .getOrElse(successful(()))
    } yield Locked(accountLockedView())
  }

  
  def get2SVNotSetPage(): Action[AnyContent] = loggedInAction { implicit request =>
    successful(Ok(userDidNotAdd2SVView()))
  }

  def get2svRecommendationPage(): Action[AnyContent] = loggedInAction {
    implicit request => {
      for {
        showAdminMfaMandateMessage <- mfaMandateService.showAdminMfaMandatedMessage(request.userId)
        mfaMandateDetails = MfaMandateDetails(showAdminMfaMandateMessage, mfaMandateService.daysTillAdminMfaMandate.getOrElse(0))
      }  yield (Ok(add2SVView(mfaMandateDetails)))
    }
  }

  private def routeToLoginOr2SV(login: LoginForm,
                                userAuthenticationResponse: UserAuthenticationResponse,
                                playSession: PlaySession)(implicit request: Request[AnyContent]): Future[Result] = {

    // In each case retain the Play session so that 'access_uri' query param, if set, is used at the end of the 2SV reminder flow
    userAuthenticationResponse.session match {
      case Some(session) if session.loggedInState.isLoggedIn => audit(LoginSucceeded, DeveloperSession.apply(session))
        successful(
          withSessionCookie(
            Redirect(routes.UserLoginAccount.get2svRecommendationPage(), SEE_OTHER).withSession(playSession),
            session.sessionId
          )
        )

      case None => {
        successful(
          Redirect(
            routes.UserLoginAccount.enterTotp(), SEE_OTHER
          ).withSession(
            playSession + ("emailAddress" -> login.emailaddress) + ("nonce" -> userAuthenticationResponse.nonce.get)
          )
        )
      }

      case Some(session) if session.loggedInState.isPartLoggedInEnablingMFA => {
        successful(
          withSessionCookie(
            Redirect(controllers.profile.routes.ProtectAccount.getProtectAccount().url).withSession(playSession),
            session.sessionId
          )
        )
      }
    }
  }

  def authenticate: Action[AnyContent] = Action.async { implicit request =>
    val requestForm = loginForm.bindFromRequest

    requestForm.fold(
      errors => successful(BadRequest(signInView("Sign in", errors))),
      login => {

        for {
          userAuthenticationResponse <- sessionService.authenticate(login.emailaddress, login.password)
          response <- routeToLoginOr2SV(login, userAuthenticationResponse, request.session)
        } yield response
      } recover {
        case _: InvalidEmail =>
          audit(LoginFailedDueToInvalidEmail, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: InvalidCredentials =>
          audit(LoginFailedDueToInvalidPassword, Map("developerEmail" -> login.emailaddress))
          Unauthorized(signInView("Sign in", LoginForm.invalidCredentials(requestForm, login.emailaddress)))
        case _: LockedAccount =>
          audit(LoginFailedDueToLockedAccount, Map("developerEmail" -> login.emailaddress))
          Locked(accountLockedView())
        case _: UnverifiedAccount =>
          Forbidden(signInView("Sign in", LoginForm.accountUnverified(requestForm, login.emailaddress)))
            .withSession("email" -> login.emailaddress)
      }
    )
  }

  def enterTotp: Action[AnyContent] = Action.async { implicit request =>
      Future.successful(Ok(logInAccessCodeView(ProtectAccountForm.form)))
  }

  def authenticateTotp: Action[AnyContent] = Action.async { implicit request =>
    ProtectAccountForm.form.bindFromRequest.fold(
      errors => successful(BadRequest(logInAccessCodeView(errors))),
      validForm => {
        val email = request.session.get("emailAddress").get
        sessionService.authenticateTotp(email, validForm.accessCode, request.session.get("nonce").get) flatMap { session =>
          audit(LoginSucceeded, DeveloperSession.apply(session))
          loginSucceeded(request).map(r => withSessionCookie(r, session.sessionId))
        } recover {
          case _: InvalidCredentials =>
            audit(LoginFailedDueToInvalidAccessCode, Map("developerEmail" -> email))
            Unauthorized(logInAccessCodeView(ProtectAccountForm.form.fill(validForm).withError("accessCode", "You have entered an incorrect access code")))
        }
      }
    )
  }

  def get2SVHelpConfirmationPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeView()))
  }

  def get2SVHelpCompletionPage(): Action[AnyContent] = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeCompleteView()))
  }

  def confirm2SVHelp(): Action[AnyContent] = loggedOutAction { implicit request =>
    applicationService.request2SVRemoval(request.session.get("emailAddress").getOrElse("")).map(_ => Ok(protectAccountNoAccessCodeCompleteView()))
  }
}
