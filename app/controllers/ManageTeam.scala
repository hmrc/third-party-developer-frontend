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

import config.ApplicationGlobal
import connectors.ThirdPartyDeveloperConnector
import domain._
import helpers.string._
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import service._

import scala.concurrent.Future
import scala.concurrent.Future.successful

trait ManageTeam extends ApplicationController {

  val applicationService: ApplicationService
  val developerConnector: ThirdPartyDeveloperConnector
  val auditService: AuditService

  def manageTeam(applicationId: String, error: Option[String] = None) = teamMemberOnStandardApp(applicationId) { implicit request =>
    val application = request.application
    val view = views.html.manageTeam(application, request.role, AddTeamMemberForm.form, request.user)
    Future.successful(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def addTeamMember(applicationId: String) = teamMemberOnStandardApp(applicationId) { implicit request =>
    Future.successful(Ok(views.html.addTeamMember(request.application, AddTeamMemberForm.form, request.user)))
  }

  def addTeamMemberAction(applicationId: String) = adminOnStandardApp(applicationId) { implicit request =>
    def handleValidForm(form: AddTeamMemberForm) = {
      applicationService.addTeamMember(request.application, request.user.email, Collaborator(form.email, Role.from(form.role).getOrElse(Role.DEVELOPER)))
        .map(_ => Redirect(controllers.routes.ManageTeam.manageTeam(applicationId, None))) recover {
        case _: ApplicationNotFound => NotFound(ApplicationGlobal.notFoundTemplate)
        case _: TeamMemberAlreadyExists =>
          BadRequest(views.html.addTeamMember(
            request.application,
            AddTeamMemberForm.form.fill(form).withError("email", "team.member.error.emailAddress.already.exists.field"),
            request.user))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddTeamMemberForm]) = {
      Future.successful(BadRequest(views.html.addTeamMember(request.application, formWithErrors, request.user)))
    }

    AddTeamMemberForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def removeTeamMember(applicationId: String, teamMemberHash: String) = teamMemberOnStandardApp(applicationId) {
    implicit request =>
      val application = request.application

      application.collaborators.find(c => c.emailAddress.toSha256 == teamMemberHash) match {
        case Some(collaborator) =>
          successful(Ok(views.html.removeTeamMember(application, RemoveTeamMemberConfirmationForm.form, request.user, collaborator.emailAddress)))
        case None => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
      }
  }

  def removeTeamMemberAction(applicationId: String) = adminOnStandardApp(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: RemoveTeamMemberConfirmationForm) = {
      form.confirm match {
        case Some("Yes") => applicationService
          .removeTeamMember(request.application, form.email, request.user.email)
          .map(_ => Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
        case _ => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
      }
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberConfirmationForm]) =
      successful(BadRequest(views.html.removeTeamMember(application, form, request.user, form("email").value.getOrElse(""))))

    RemoveTeamMemberConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}

object ManageTeam extends ManageTeam with WithAppConfig {
  override val sessionService = SessionService
  override val applicationService = ApplicationServiceImpl
  override val developerConnector = ThirdPartyDeveloperConnector
  override val auditService = AuditService
}
