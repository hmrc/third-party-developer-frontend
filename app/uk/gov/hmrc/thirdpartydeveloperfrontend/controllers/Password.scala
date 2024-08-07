/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html._

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.{PasswordChangeRequest, PasswordResetRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditAction.PasswordChangeFailedDueToInvalidCredentials
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{AuditService, SessionService}

@Singleton
class Password @Inject() (
    val auditService: AuditService,
    val sessionService: SessionService,
    val connector: ThirdPartyDeveloperConnector,
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    forgotPasswordView: ForgotPasswordView,
    checkEmailView: CheckEmailView,
    resetView: ResetView,
    resetInvalidView: ResetInvalidView,
    resetErrorView: ResetErrorView,
    signInView: SignInView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends LoggedOutController(mcc) with PasswordChange with ApplicationLogger with WithUnsafeDefaultFormBinding {

  import ErrorFormBuilder.CommonGlobalErrorsSyntax

  def showForgotPassword() = loggedOutAction { implicit request =>
    Future.successful(Ok(forgotPasswordView(ForgotPasswordForm.form)))
  }

  def requestReset() = Action.async { implicit request =>
    val requestForm = ForgotPasswordForm.form.bindFromRequest()
    requestForm.fold(
      formWithErrors => {
        Future.successful(BadRequest(forgotPasswordView(formWithErrors.emailaddressGlobal())))
      },
      passwordForm =>
        connector.requestReset(passwordForm.emailaddress.toLaxEmail) map {
          _ => Ok(checkEmailView(passwordForm.emailaddress.toLaxEmail))
        } recover {
          case _: UnverifiedAccount                      => Forbidden(forgotPasswordView(ForgotPasswordForm.accountUnverified(requestForm, passwordForm.emailaddress)))
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Ok(checkEmailView(passwordForm.emailaddress.toLaxEmail))
        }
    )
  }

  def validateReset(code: String) = Action.async { implicit request =>
    connector.fetchEmailForResetCode(code) map {
      email => Redirect(routes.Password.resetPasswordChange()).addingToSession("email" -> email.text)
    } recover {
      case _: InvalidResetCode  => Redirect(routes.Password.resetPasswordError()).flashing(
          "error" -> "InvalidResetCode"
        )
      case _: UnverifiedAccount => Redirect(routes.Password.resetPasswordError()).flashing(
          "error" -> "UnverifiedAccount"
        )
    }
  }

  def resetPasswordChange = Action.async { implicit request =>
    request.session.get("email") match {
      case None    =>
        logger.warn("email not found in session")
        Future.successful(Redirect(routes.Password.resetPasswordError()).flashing("error" -> "Error"))
      case Some(_) => Future.successful(Ok(resetView(PasswordResetForm.form)))
    }
  }

  def resetPasswordError = Action(implicit request =>
    request.flash.get("error").getOrElse("error") match {
      case "UnverifiedAccount" =>
        val email = request.flash.get("email").getOrElse("").toString
        Forbidden(forgotPasswordView(ForgotPasswordForm.accountUnverified(ForgotPasswordForm.form, email))).withSession("email" -> email)
      case "InvalidResetCode"  =>
        BadRequest(resetInvalidView())
      case _                   =>
        BadRequest(resetErrorView())
    }
  )

  def resetPassword = Action.async { implicit request =>
    PasswordResetForm.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(resetView(formWithErrors.passwordGlobal().passwordNoMatchField()))),
      data => {
        val email = request.session.get("email").getOrElse(throw new RuntimeException("email not found in session"))
        connector.reset(PasswordResetRequest(email.toLaxEmail, data.password)) map {
          _ => Ok(signInView("You have reset your password", LoginForm.form, endOfJourney = true))
        } recover {
          case _: UnverifiedAccount => Forbidden(resetView(PasswordResetForm.accountUnverified(PasswordResetForm.form, email)))
              .withSession("email" -> email)
        }
      }
    )
  }
}

trait PasswordChange {
  self: FrontendBaseController =>

  import ErrorFormBuilder.CommonGlobalErrorsSyntax

  val connector: ThirdPartyDeveloperConnector
  val auditService: AuditService

  def processPasswordChange(
      email: LaxEmailAddress,
      success: Result,
      error: Form[ChangePasswordForm] => HtmlFormat.Appendable
    )(implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext
    ) = {
    ChangePasswordForm.form.bindFromRequest().fold(
      errors => Future.successful(BadRequest(error(errors.currentPasswordGlobal().passwordGlobal().passwordNoMatchField()))),
      data => {
        val payload = PasswordChangeRequest(email, data.currentPassword, data.password)
        connector.changePassword(payload) map {
          _ => success
        } recover {
          case _: UnverifiedAccount  => Forbidden(error(ChangePasswordForm.accountUnverified(ChangePasswordForm.form, email.text)))
              .withSession("email" -> email.text)
          case _: InvalidCredentials =>
            auditService.audit(PasswordChangeFailedDueToInvalidCredentials(email))
            Unauthorized(error(ChangePasswordForm.invalidCredentials(ChangePasswordForm.form)))
          case _: LockedAccount      => Redirect(routes.UserLoginAccount.accountLocked())
        }
      }
    )
  }
}
