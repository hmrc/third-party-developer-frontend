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
import controllers.FormKeys.appNameField
import domain.Environment.{PRODUCTION, SANDBOX}
import domain.{Environment, _}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import service._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class AddApplication @Inject()(val applicationService: ApplicationService,
                               val sessionService: SessionService,
                               val auditService: AuditService,
                               val errorHandler: ErrorHandler,
                               val messagesApi: MessagesApi,
                               implicit val appConfig: ApplicationConfig)
                              (implicit ec: ExecutionContext) extends ApplicationController {

  def manageApps: Action[AnyContent] = loggedInAction { implicit request =>
    applicationService.fetchByTeamMemberEmail(loggedIn.email) flatMap { apps =>
      if (apps.isEmpty) {
        Future.successful(Ok(views.html.addApplicationSubordinateEmptyNest()))
      } else {
        Future.successful(Ok(views.html.manageApplications(apps.map(ApplicationSummary.from(_, loggedIn.email)))))
      }
    }
  }

  def addApplicationSubordinate(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.addApplicationStartSubordinate()))
  }

  def addApplicationSubordinatePost(): Action[AnyContent] = loggedInAction { implicit request =>

    val createApplicationRequest: CreateApplicationRequest = CreateApplicationRequest(
      AddApplication.newAppName,
      Environment.SANDBOX,
      None,
      Seq(Collaborator(loggedIn.email, Role.ADMINISTRATOR)))

    applicationService.createForUser(createApplicationRequest).map(
      createApplicationResponse => {
        Redirect(routes.AddApplication.editApplicationName(createApplicationResponse.id, createApplicationRequest.environment))
      }
    )
  }

  def addApplicationSuccess(applicationId: String, notUsedEnvironment: Environment): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>

      applicationService.fetchByApplicationId(applicationId).map(_.deployedTo).map {
        case SANDBOX => Ok(views.html.addApplicationSubordinateSuccess(request.application.name, applicationId))
        case PRODUCTION => Ok(views.html.addApplicationPrincipalSuccess(request.application.name, applicationId))
      }.recoverWith {
        case NonFatal(_) =>
          Future.successful(NotFound(errorHandler.notFoundTemplate(request)))
      }
    }

  def editApplicationName(applicationId: String, environment: Environment): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    def hideNewAppName(name: String) = {
      if (name == AddApplication.newAppName) ""
      else name
    }

    environment match {
      case env =>
        val form = AddApplicationNameForm.form.fill(AddApplicationNameForm(hideNewAppName(request.application.name)))

        Future.successful(Ok(views.html.addApplicationName(form, applicationId, Some(env))))
    }
  }

  def editApplicationNameAction(applicationId: String, environment: Environment): Action[AnyContent] = whenTeamMemberOnApp(applicationId) {
    implicit request =>
      val application = request.application

      val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

      def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: Environment) =
        Future.successful(Ok(views.html.addApplicationName(errors, applicationId, Some(environment))))

      def updateNameIfChanged(form: AddApplicationNameForm) = {
        if (application.name == form.applicationName) {
          Future.successful(())
        } else {
          applicationService.update(UpdateApplicationRequest(
            application.id,
            application.deployedTo,
            form.applicationName))
        }
      }

      def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) =
        applicationService.isApplicationNameValid(
          formThatPassesSimpleValidation.applicationName,
          environment,
          Some(application.id))
          .flatMap {
            case Valid =>
              updateNameIfChanged(formThatPassesSimpleValidation).map(
                _ => application.deployedTo match {
                  case PRODUCTION => Redirect(routes.AddApplication.addApplicationSuccess(application.id, application.deployedTo))
                  case SANDBOX => Redirect(routes.Subscriptions.subscriptions2(application.id, application.deployedTo))
                }
              )

            case invalid: Invalid =>
              def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

              Future.successful(BadRequest(views.html.addApplicationName(invalidApplicationNameForm, applicationId, Some(environment))))
          }

      requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
  }
}

object AddApplication {
  val newAppName = "new application"
}