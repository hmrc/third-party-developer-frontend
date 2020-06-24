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
import connectors.ThirdPartyDeveloperConnector
import domain.AddTeamMemberPageMode.{ApplicationCheck, CheckYourAnswers, ManageTeamMembers}
import domain.Capabilities.SupportsTeamMembers
import domain.Permissions.{AdministratorOnly, TeamMembersOnly}
import domain._
import javax.inject.{Inject, Singleton}
import model.ApplicationViewModel
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import play.api.libs.crypto.CookieSigner
import play.twirl.api.Html
import service._

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageTeam @Inject()(val sessionService: SessionService,
                           val auditService: AuditService,
                           developerConnector: ThirdPartyDeveloperConnector,
                           val applicationService: ApplicationService,
                           val errorHandler: ErrorHandler,
                           val messagesApi: MessagesApi,
                           val cookieSigner : CookieSigner
                           )
                          (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController {

  private def whenAppSupportsTeamMembers(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsActionForApprovedApps(SupportsTeamMembers, TeamMembersOnly)(applicationId)(fun)

  private def canEditTeamMembers(applicationId: String, alsoAllowTestingState : Boolean = false)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    if (alsoAllowTestingState)
      capabilityThenPermissionsActionForApprovedOrTestingApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)
    else
      capabilityThenPermissionsActionForApprovedApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)

  def manageTeam(applicationId: String, error: Option[String] = None) = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    val view = views.html.manageTeamViews.manageTeam(applicationViewModelFromApplicationRequest, request.role, AddTeamMemberForm.form)
    Future.successful(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def addTeamMember(applicationId: String) = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    Future.successful(Ok(views.html.manageTeamViews.addTeamMember(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.user)))
  }

  def addTeamMemberAction(applicationId: String, addTeamMemberPageMode: AddTeamMemberPageMode) =
    canEditTeamMembers(applicationId, alsoAllowTestingState = true) { implicit request =>

    val successRedirect = addTeamMemberPageMode match {
      case ManageTeamMembers => controllers.routes.ManageTeam.manageTeam(applicationId, None)
      case ApplicationCheck => controllers.checkpages.routes.ApplicationCheck.team(applicationId)
      case CheckYourAnswers => controllers.checkpages.routes.CheckYourAnswers.team(applicationId)
    }

    def createBadRequestResult(formWithErrors: Form[AddTeamMemberForm]) : Result = {
      val viewFunction: (ApplicationViewModel, Form[AddTeamMemberForm], DeveloperSession) => Html = addTeamMemberPageMode match {
        case ManageTeamMembers => views.html.manageTeamViews.addTeamMember.apply
        case ApplicationCheck => views.html.checkpages.applicationcheck.team.teamMemberAdd.apply
        case CheckYourAnswers => views.html.checkpages.checkyouranswers.team.teamMemberAdd.apply

      }

      BadRequest(viewFunction(
        applicationViewModelFromApplicationRequest,
        formWithErrors,
        request.user
      ))
    }

    def handleValidForm(form: AddTeamMemberForm) = {
      applicationService.addTeamMember(request.application, request.user.email, Collaborator(form.email, Role.from(form.role).getOrElse(Role.DEVELOPER)))
        .map(_ => Redirect(successRedirect)) recover {
        case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
        case _: TeamMemberAlreadyExists => createBadRequestResult(AddTeamMemberForm.form.fill(form).withError("email", "team.member.error.emailAddress.already.exists.field"))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddTeamMemberForm]) = {
      successful( createBadRequestResult(formWithErrors))
    }

    AddTeamMemberForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def removeTeamMember(applicationId: String, teamMemberHash: String) = whenAppSupportsTeamMembers(applicationId) {
    implicit request =>
      val application = request.application

      application.findCollaboratorByHash(teamMemberHash) match {
        case Some(collaborator) =>
          successful(Ok(views.html.manageTeamViews.removeTeamMember(applicationViewModelFromApplicationRequest, RemoveTeamMemberConfirmationForm.form, collaborator.emailAddress)))
        case None => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
      }
  }

  def removeTeamMemberAction(applicationId: String) = canEditTeamMembers(applicationId) { implicit request =>
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
      successful(BadRequest(views.html.manageTeamViews.removeTeamMember(applicationViewModelFromApplicationRequest, form, form("email").value.getOrElse(""))))

    RemoveTeamMemberConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
