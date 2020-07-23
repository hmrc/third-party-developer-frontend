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
import domain.Capabilities.SupportsDeletion
import domain.Permissions.{AdministratorOnly, TeamMembersOnly}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, MessagesControllerComponents, Result}
import service._
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteApplication @Inject()(val applicationService: ApplicationService,
                                  val sessionService: SessionService,
                                  val errorHandler: ErrorHandler,
                                  mcc: MessagesControllerComponents,
                                  val cookieSigner : CookieSigner,
                                  deleteApplicationView: DeleteApplicationView,
                                  deletePrincipalApplicationConfirmView: DeletePrincipalApplicationConfirmView,
                                  deletePrincipalApplicationCompleteView: DeletePrincipalApplicationCompleteView,
                                  deleteSubordinateApplicationConfirmView: DeleteSubordinateApplicationConfirmView,
                                  deleteSubordinateApplicationCompleteView: DeleteSubordinateApplicationCompleteView)
                                 (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc) {

  private def canDeleteApplicationAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForApprovedApps(SupportsDeletion,AdministratorOnly)(applicationId)(fun)

  private def canViewDeleteApplicationAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForApprovedApps(SupportsDeletion,TeamMembersOnly)(applicationId)(fun)


  def deleteApplication(applicationId: String, error: Option[String] = None) =
    canViewDeleteApplicationAction(applicationId) { implicit request =>
      val view = deleteApplicationView(request.application, request.role)
      Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
    }

  def deletePrincipalApplicationConfirm(applicationId: String, error: Option[String] = None) =
    canDeleteApplicationAction(applicationId) { implicit request =>
      val view = deletePrincipalApplicationConfirmView(request.application, DeletePrincipalApplicationForm.form.fill(DeletePrincipalApplicationForm(None)))
      Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
    }

  def deletePrincipalApplicationAction(applicationId: String) = canDeleteApplicationAction(applicationId) { implicit request =>
    val application = request.application

    def handleInvalidForm(formWithErrors: Form[DeletePrincipalApplicationForm]) =
      Future(BadRequest(deletePrincipalApplicationConfirmView(application, formWithErrors)))

    def handleValidForm(validForm: DeletePrincipalApplicationForm) = {
      validForm.deleteConfirm match {
        case Some("Yes") => applicationService.requestPrincipalApplicationDeletion(request.user, application)
          .map(_ => Ok(deletePrincipalApplicationCompleteView(application)))
        case _ => Future(Redirect(routes.Details.details(applicationId)))
      }
    }

    DeletePrincipalApplicationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def deleteSubordinateApplicationConfirm(applicationId: String) = canDeleteApplicationAction(applicationId) { implicit request =>
    Future(Ok(deleteSubordinateApplicationConfirmView(request.application)))
  }

  def deleteSubordinateApplicationAction(applicationId: String) = canDeleteApplicationAction(applicationId) { implicit request =>
    val application = request.application

    applicationService.deleteSubordinateApplication(request.user, application)
      .map(_ => Ok(deleteSubordinateApplicationCompleteView(application)))

  }


}
