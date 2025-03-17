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
import play.api.mvc.{AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsDeletion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class DeleteApplication @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    deleteApplicationView: DeleteApplicationView,
    requestDeleteApplicationConfirmView: RequestDeleteApplicationConfirmView,
    requestDeleteApplicationCompleteView: RequestDeleteApplicationCompleteView,
    deleteSubordinateApplicationConfirmView: DeleteSubordinateApplicationConfirmView,
    deleteSubordinateApplicationCompleteView: DeleteSubordinateApplicationCompleteView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with WithUnsafeDefaultFormBinding {

  def deleteApplication(applicationId: ApplicationId, error: Option[String] = None) =
    canViewDeleteApplicationAction(applicationId) { implicit request =>
      val view = deleteApplicationView(request.application, request.role)
      Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
    }

  def requestDeleteApplicationConfirm(applicationId: ApplicationId, error: Option[String] = None) =
    canDeleteApplicationAction(applicationId) { implicit request =>
      val view = requestDeleteApplicationConfirmView(request.application, DeleteApplicationForm.form.fill(DeleteApplicationForm(None)))
      Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
    }

  def requestDeleteApplicationAction(applicationId: ApplicationId) = canDeleteApplicationAction(applicationId) { implicit request =>
    val application = request.application

    def handleInvalidForm(formWithErrors: Form[DeleteApplicationForm]) =
      Future(BadRequest(requestDeleteApplicationConfirmView(application, formWithErrors)))

    def handleValidForm(validForm: DeleteApplicationForm) = {
      validForm.deleteConfirm match {
        case Some("Yes") =>
          applicationService
            .requestApplicationDeletion(request.userSession, application)
            .map(_ => Ok(requestDeleteApplicationCompleteView(application)))
        case _           => Future(Redirect(routes.Details.details(applicationId)))
      }
    }

    DeleteApplicationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteSubordinateApplicationConfirm(applicationId: ApplicationId) = canDeleteApplicationAction(applicationId) { implicit request =>
    Future(Ok(deleteSubordinateApplicationConfirmView(request.application)))
  }

  def deleteSubordinateApplicationAction(applicationId: ApplicationId) = canDeleteApplicationAction(applicationId) { implicit request =>
    val application = request.application

    applicationService
      .deleteSubordinateApplication(request.userSession, application)
      .map(_ => Ok(deleteSubordinateApplicationCompleteView(application)))

  }

  private def canDeleteApplicationAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForApprovedApps(SupportsDeletion, AdministratorOnly)(applicationId)(fun)

  private def canViewDeleteApplicationAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForApprovedApps(SupportsDeletion, TeamMembersOnly)(applicationId)(fun)
}
