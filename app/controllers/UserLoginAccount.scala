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
import play.api.mvc.Action
import service.AuditAction._
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.html._
import views.html.protectaccount._

import scala.concurrent.ExecutionContext
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
                                 val messagesApi: MessagesApi,
                                 val mfaMandateService: MfaMandateService,
                                 implicit val appConfig: ApplicationConfig)
                                (implicit ec: ExecutionContext)
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
      login => sessionService.authenticate(login.emailaddress, login.password) flatMap {
        userAuthenticationResponse => {
          // TODO - A bit ugly
          mfaMandateService.showAdminMfaMandatedMessage(login.emailaddress).flatMap(showAdminMfaMandateMessage => {
            def mfaMandateDetails = MfaMandateDetails(showAdminMfaMandateMessage, mfaMandateService.daysTillAdminMfaMandate.getOrElse(0))

            userAuthenticationResponse.session match {
              case Some(session) => audit(LoginSucceeded, session.developer)
                // Retain the Play session so that 'access_uri', if set, is used at the end of the 2SV reminder flow

                gotoLoginSucceeded(session.sessionId, successful(Ok(add2SV(mfaMandateDetails)).withSession(request.session)))
              case None => successful(Ok(logInAccessCode(ProtectAccountForm.form))
                .withSession(request.session + ("emailAddress" -> login.emailaddress) + ("nonce" -> userAuthenticationResponse.nonce.get)))
            }
          })
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
    ProtectAccountForm.form.bindFromRequest.fold(
      errors => successful(BadRequest(logInAccessCode(errors))),
      validForm => {
        val email = request.session.get("emailAddress").get
        sessionService.authenticateTotp(email, validForm.accessCode, request.session.get("nonce").get) flatMap { session =>
          audit(LoginSucceeded, session.developer)
          gotoLoginSucceeded(session.sessionId)
        } recover {
          case _: InvalidCredentials =>
            audit(LoginFailedDueToInvalidAccessCode, Map("developerEmail" -> email))
            Unauthorized(logInAccessCode(ProtectAccountForm.form.fill(validForm).withError("accessCode", "You have entered an incorrect access code")))
        }
      }
    )
  }

  def get2SVHelpConfirmationPage() = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCode()))
  }

  def get2SVHelpCompletionPage() = loggedOutAction { implicit request =>
    successful(Ok(protectAccountNoAccessCodeComplete()))
  }

  def confirm2SVHelp() = loggedOutAction { implicit request =>
    applicationService.request2SVRemoval(request.session.get("emailAddress").getOrElse("")).map(_ => Ok(protectAccountNoAccessCodeComplete()))
  }

  def get2SVNotSetPage() = Action.async { implicit request =>
    successful(Ok(userDidNotAdd2SV()))
  }
}
