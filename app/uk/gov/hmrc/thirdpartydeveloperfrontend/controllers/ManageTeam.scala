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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import views.html.manageTeamViews.{AddTeamMemberView, ManageTeamView, RemoveTeamMemberView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result => PlayResult}
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.services.CollaboratorService
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{CommandFailures, CommandHandlerTypes, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsTeamMembers
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class ManageTeam @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val collaboratorService: CollaboratorService,
    val applicationActionService: ApplicationActionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    manageTeamView: ManageTeamView,
    addTeamMemberView: AddTeamMemberView,
    removeTeamMemberView: RemoveTeamMemberView,
    val fraudPreventionConfig: FraudPreventionConfig
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with CommandHandlerTypes[DispatchSuccessResult]
    with FraudPreventionNavLinkHelper
    with WithUnsafeDefaultFormBinding {

  private def whenAppSupportsTeamMembers(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[PlayResult]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsTeamMembers, TeamMembersOnly)(applicationId)(fun)

  private def canEditTeamMembers(applicationId: ApplicationId, alsoAllowTestingState: Boolean = false)(fun: ApplicationRequest[AnyContent] => Future[PlayResult]): Action[AnyContent] =
    if (alsoAllowTestingState) {
      checkActionForApprovedOrTestingApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)
    } else {
      checkActionForApprovedApps(SupportsTeamMembers, AdministratorOnly)(applicationId)(fun)
    }

  def manageTeam(applicationId: ApplicationId, error: Option[String] = None): Action[AnyContent] = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    val view = manageTeamView(applicationViewModelFromApplicationRequest(), request.role, AddTeamMemberForm.form, createFraudNavModel(fraudPreventionConfig))
    Future.successful(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def addTeamMember(applicationId: ApplicationId): Action[AnyContent] = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    Future.successful(Ok(addTeamMemberView(applicationViewModelFromApplicationRequest(), AddTeamMemberForm.form, request.developerSession, createFraudNavModel(fraudPreventionConfig))))
  }

  def addTeamMemberAction(applicationId: ApplicationId): Action[AnyContent] =
    canEditTeamMembers(applicationId, alsoAllowTestingState = true) { implicit request =>
      val successRedirect = routes.ManageTeam.manageTeam(applicationId, None)

      def handleAddTeamMemberView(a: ApplicationViewModel, f: Form[AddTeamMemberForm], ds: DeveloperSession) = {
        addTeamMemberView.apply(a, f, ds, createFraudNavModel(fraudPreventionConfig))
      }

      def createBadRequestResult(formWithErrors: Form[AddTeamMemberForm]): PlayResult = {
        val viewFunction: (ApplicationViewModel, Form[AddTeamMemberForm], DeveloperSession) => Html = handleAddTeamMemberView

        BadRequest(
          viewFunction(
            applicationViewModelFromApplicationRequest(),
            formWithErrors,
            request.developerSession
          )
        )
      }

      def handleValidForm(form: AddTeamMemberForm) = {
        val role = form.role.flatMap(Collaborator.Role(_)).getOrElse(Collaborator.Roles.DEVELOPER)

        val handleFailure: Failures => PlayResult = (fails) =>
          fails.head match {
            case CommandFailures.CollaboratorAlreadyExistsOnApp =>
              createBadRequestResult(AddTeamMemberForm.form.fill(form).withError("email", "team.member.error.emailAddress.already.exists.field"))
            case CommandFailures.ApplicationNotFound            => NotFound(errorHandler.notFoundTemplate)
            case _                                              => InternalServerError("Action failed")
          }

        collaboratorService
          .addTeamMember(request.application, form.email.toLaxEmail, role, request.developerSession.email)
          .map {
            _.fold(handleFailure, _ => Redirect(successRedirect))
          }
      }

      def handleInvalidForm(formWithErrors: Form[AddTeamMemberForm]) = {
        successful(createBadRequestResult(formWithErrors))
      }

      AddTeamMemberForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
    }

  def removeTeamMember(applicationId: ApplicationId, teamMemberHash: String): Action[AnyContent] = whenAppSupportsTeamMembers(applicationId) { implicit request =>
    val application = request.application

    application.findCollaboratorByHash(teamMemberHash) match {
      case Some(collaborator) =>
        successful(Ok(removeTeamMemberView(applicationViewModelFromApplicationRequest(), RemoveTeamMemberConfirmationForm.form, collaborator.emailAddress.text)))
      case None               => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
    }
  }

  def removeTeamMemberAction(applicationId: ApplicationId): Action[AnyContent] = canEditTeamMembers(applicationId) { implicit request =>
    def handleValidForm(form: RemoveTeamMemberConfirmationForm) = {
      form.confirm match {
        case Some("Yes") =>
          collaboratorService
            .removeTeamMember(request.application, form.email.toLaxEmail, request.developerSession.email)
            .map(_ => Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
        case _           => successful(Redirect(routes.ManageTeam.manageTeam(applicationId, None)))
      }
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberConfirmationForm]) =
      successful(BadRequest(removeTeamMemberView(applicationViewModelFromApplicationRequest(), form, form("email").value.getOrElse(""))))

    RemoveTeamMemberConfirmationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
