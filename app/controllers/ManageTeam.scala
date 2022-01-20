/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import controllers.fraudprevention.FraudPreventionNavLinkHelper
import domain._
import domain.models.applications.{ApplicationId, AddCollaborator, CollaboratorRole}
import domain.models.applications.Capabilities.SupportsTeamMembers
import domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import domain.models.controllers.AddTeamMemberPageMode
import domain.models.controllers.AddTeamMemberPageMode._
import domain.models.developers.DeveloperSession
import javax.inject.{Inject, Singleton}
import domain.models.controllers.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.Html
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.html.checkpages.applicationcheck.team.TeamMemberAddView
import views.html.manageTeamViews.{AddTeamMemberView, ManageTeamView, RemoveTeamMemberView}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

@Singleton
class ManageTeam @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    manageTeamView: ManageTeamView,
    addTeamMemberView: AddTeamMemberView,
    teamMemberAddView: TeamMemberAddView,
    removeTeamMemberView: RemoveTeamMemberView,
    val fraudPreventionConfig: FraudPreventionConfig
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) with FraudPreventionNavLinkHelper {

  private def whenAppSupportsTeamMembers(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsTeamMembers, TeamMembersOnly)(applicationId)(fun)

  private def canEditTeamMembers(applicationId: ApplicationId, alsoAllowTestingState: Boolean = false)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    if (alsoAllowTestingState)
      checkActionForApprovedOrTestingApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)
    else
      checkActionForApprovedApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)


  def manageTeam(applicationId: ApplicationId, error: Option[String] = None) = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    val view = manageTeamView(applicationViewModelFromApplicationRequest, request.role, AddTeamMemberForm.form, createFraudNavModel(fraudPreventionConfig))
    Future.successful(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def addTeamMember(applicationId: ApplicationId) = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    Future.successful(Ok(addTeamMemberView(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.developerSession, createFraudNavModel(fraudPreventionConfig))))
  }

  def addTeamMemberAction(applicationId: ApplicationId, addTeamMemberPageMode: AddTeamMemberPageMode) =
    canEditTeamMembers(applicationId, alsoAllowTestingState = true) { implicit request =>
      val successRedirect = addTeamMemberPageMode match {
        case ManageTeamMembers => routes.ManageTeam.manageTeam(applicationId, None)
        case ApplicationCheck  => controllers.checkpages.routes.ApplicationCheck.team(applicationId)
        case CheckYourAnswers  => controllers.checkpages.routes.CheckYourAnswers.team(applicationId)
      }

      def handleAddTeamMemberView(a: ApplicationViewModel, f: Form[AddTeamMemberForm], ds:DeveloperSession)={
        addTeamMemberView.apply(a,f,ds, createFraudNavModel(fraudPreventionConfig))
      }
      
      def createBadRequestResult(formWithErrors: Form[AddTeamMemberForm]): Result = {
        val viewFunction: (ApplicationViewModel, Form[AddTeamMemberForm], DeveloperSession) => Html = addTeamMemberPageMode match {
          case ManageTeamMembers => handleAddTeamMemberView
          case ApplicationCheck  => teamMemberAddView.apply
          case CheckYourAnswers  => teamMemberAddView.apply

        }

        BadRequest(
          viewFunction(
            applicationViewModelFromApplicationRequest,
            formWithErrors,
            request.developerSession
          )
        )
      }

      def handleValidForm(form: AddTeamMemberForm) = {
        applicationService
          .addTeamMember(request.application, request.developerSession.email, AddCollaborator(form.email, CollaboratorRole.from(form.role).getOrElse(CollaboratorRole.DEVELOPER)))
          .map(_ => Redirect(successRedirect)) recover {
          case _: ApplicationNotFound     => NotFound(errorHandler.notFoundTemplate)
          case _: TeamMemberAlreadyExists => createBadRequestResult(AddTeamMemberForm.form.fill(form).withError("email", "team.member.error.emailAddress.already.exists.field"))
        }
      }

      def handleInvalidForm(formWithErrors: Form[AddTeamMemberForm]) = {
        successful(createBadRequestResult(formWithErrors))
      }

      AddTeamMemberForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }

  def removeTeamMember(applicationId: ApplicationId, teamMemberHash: String) = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    val application = request.application

    application.findCollaboratorByHash(teamMemberHash) match {
      case Some(collaborator) =>
        successful(Ok(removeTeamMemberView(applicationViewModelFromApplicationRequest, RemoveTeamMemberConfirmationForm.form, collaborator.emailAddress)))
      case None => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
    }
  }

  def removeTeamMemberAction(applicationId: ApplicationId) = canEditTeamMembers(applicationId) { implicit request =>
    def handleValidForm(form: RemoveTeamMemberConfirmationForm) = {
      form.confirm match {
        case Some("Yes") =>
          applicationService
            .removeTeamMember(request.application, form.email, request.developerSession.email)
            .map(_ => Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
        case _ => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
      }
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberConfirmationForm]) =
      successful(BadRequest(removeTeamMemberView(applicationViewModelFromApplicationRequest, form, form("email").value.getOrElse(""))))

    RemoveTeamMemberConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
