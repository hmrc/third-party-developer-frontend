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
import connectors.ThirdPartyDeveloperConnector
import controllers.FormKeys.clientSecretLimitExceeded
import domain._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Result}
import service.AuditAction.{LoginFailedDueToInvalidPassword, LoginFailedDueToLockedAccount}
import service._
import uk.gov.hmrc.http.{ForbiddenException, HeaderCarrier}
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Credentials @Inject()(val applicationService: ApplicationService,
                            val developerConnector: ThirdPartyDeveloperConnector,
                            val auditService: AuditService,
                            val sessionService: SessionService,
                            val errorHandler: ErrorHandler,
                            val messagesApi: MessagesApi,
                            implicit val appConfig: ApplicationConfig)
                           (implicit ec: ExecutionContext)
  extends ApplicationController {


  def credentials(applicationId: String, error: Option[String] = None) = teamMemberOnApp(applicationId) { implicit request =>
    applicationService.fetchCredentials(applicationId).map { tokens =>
      val view = views.html.credentials(request.application, tokens, VerifyPasswordForm.form.fill(VerifyPasswordForm("")))
      error.map(_ => BadRequest(view)).getOrElse(Ok(view))
    } recover {
      case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
    }
  }

  def addClientSecret(applicationId: String) = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>

    def result(err: Option[String] = None): Result = Redirect(controllers.routes.Credentials.credentials(applicationId, err))

    applicationService.addClientSecret(applicationId).map { _ =>
      result()
    } recover {
        case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
        case _: ForbiddenException => Forbidden(errorHandler.badRequestTemplate)
        case _: ClientSecretLimitExceeded => result(Some(clientSecretLimitExceeded))
    }
  }

  def getProductionClientSecret(applicationId: String, index: Integer) =
    sandboxOrAdminIfProductionForAnyApp(applicationId, Seq(appInStateProductionFilter)) { implicit request =>

    def fetchClientSecret(password: String) = {
      val future = for {
        _ <- developerConnector.checkPassword(PasswordCheckRequest(request.user.email, password))
        result <- applicationService.fetchCredentials(applicationId)
      } yield result.production.clientSecrets.zipWithIndex.find(_._2 == index).map(_._1)

      future map {
        case Some(clientSecret) => Ok(Json.toJson(ClientSecretResponse(clientSecret.secret)))
        case None => BadRequest(Json.toJson(BadRequestError))
      } recover {
        case _: InvalidCredentials => Unauthorized(Json.toJson(InvalidPasswordError))
        case _: LockedAccount => Locked(Json.toJson(LockedAccountError))
        case e: Throwable =>
          Logger.error(s"Could not fetch client secret for application $applicationId", e)
          BadRequest(Json.toJson(BadRequestError))
      }
    }

    if (request.application.state.name != State.PRODUCTION) {
      Logger.warn(s"Application $applicationId is not in production")
      Future.successful(BadRequest(Json.toJson(BadRequestError)))
    } else {
      request.headers.get("password").map(_.trim) match {
        case None | Some("") => Future(BadRequest(Json.toJson(PasswordRequiredError)))
        case Some(pwd) => fetchClientSecret(pwd)
      }
    }
  }

  def selectClientSecretsToDelete(applicationId: String): Action[AnyContent] = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>

    val application = request.application

    def showCredentials(form: Form[VerifyPasswordForm]) = {
      applicationService.fetchCredentials(applicationId).map { tokens =>
        val view = views.html.credentials(application, tokens, form)
        if (form.hasErrors) BadRequest(view) else Ok(view)
      } recover {
        case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
      }
    }

    def showClientSecretsToDelete = {
      applicationService.fetchCredentials(applicationId).map { tokens =>
        val clientSecrets = tokens.production.clientSecrets.map(_.secret)
        Ok(editapplication.selectClientSecretsToDelete(application, clientSecrets, SelectClientSecretsToDeleteForm.form))
      } recover {
        case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
      }
    }

    def handleValidForm(form: VerifyPasswordForm): Future[Result] = {
      developerConnector.checkPassword(PasswordCheckRequest(request.user.email, form.password)).flatMap { _ =>
        showClientSecretsToDelete
      } recoverWith {
        case _: InvalidCredentials =>
          audit(LoginFailedDueToInvalidPassword, request.user)
          showCredentials(VerifyPasswordForm.form.fill(form).withError(FormKeys.passwordField, FormKeys.verifyPasswordInvalidKey))

        case _: LockedAccount =>
          audit(LoginFailedDueToLockedAccount, request.user)
          Future(Redirect(controllers.routes.UserLoginAccount.accountLocked()))
      }
    }

    def handleInvalidForm(form: Form[VerifyPasswordForm]): Future[Result] = showCredentials(form)

    if (application.deployedTo.isSandbox) showClientSecretsToDelete
    else VerifyPasswordForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def selectClientSecretsToDeleteAction(applicationId: String, error: Option[String] = None)
    = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>

    val application = request.application

    def handleInvalidForm(formWithErrors: Form[SelectClientSecretsToDeleteForm]) = {
      Future(BadRequest(editapplication.selectClientSecretsToDelete(application, Seq.empty,  formWithErrors)))
    }

    def handleValidForm(validForm: SelectClientSecretsToDeleteForm): Future[Result] = {
      applicationService.fetchCredentials(applicationId).map { credentials =>
        val clientSecrets = credentials.production.clientSecrets.map(_.secret)
        validForm.clientSecretsToDelete match {
          case Nil =>
            val errorForm = SelectClientSecretsToDeleteForm.form.fill(validForm).withError(FormKeys.deleteSelectField, FormKeys.selectAClientSecretKey)
            BadRequest(editapplication.selectClientSecretsToDelete(application, clientSecrets, errorForm))
          case toDelete if toDelete.length == clientSecrets.length =>
            val errorForm = SelectClientSecretsToDeleteForm.form.fill(validForm).withError(FormKeys.deleteSelectField, FormKeys.selectFewerClientSecretsKey)
              BadRequest(editapplication.selectClientSecretsToDelete(application, clientSecrets, errorForm))
          case secrets =>
            Ok(editapplication.deleteClientSecretConfirm(application, DeleteClientSecretsConfirmForm.form.fill(DeleteClientSecretsConfirmForm(None, secrets.mkString(",")))))
        }
      }
    }

    SelectClientSecretsToDeleteForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteClientSecretsAction(applicationId: String): Action[AnyContent] = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>

    val application = request.application

    def handleInvalidForm(formWithErrors: Form[DeleteClientSecretsConfirmForm]) =
      Future(BadRequest(editapplication.deleteClientSecretConfirm(application, formWithErrors)))

    def handleValidForm(validForm: DeleteClientSecretsConfirmForm) = {
      validForm.deleteConfirm match {
        case Some("Yes") =>
          applicationService.deleteClientSecrets(applicationId, validForm.clientSecretsToDelete.split(",").toSeq)
            .map(_ => Ok(editapplication.deleteClientSecretComplete(application)))

        case _ => Future(Redirect(routes.Credentials.credentials(applicationId, None)))
      }
    }

    DeleteClientSecretsConfirmForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  private def audit(auditAction: AuditAction, developer: DeveloperSession)(implicit hc: HeaderCarrier) = {
    auditService.audit(auditAction, Map("developerEmail" -> developer.email, "developerFullName" -> developer.displayedName))
  }
}
