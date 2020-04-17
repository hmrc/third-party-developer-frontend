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
                               val messagesApi: MessagesApi
                               )
                              (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends ApplicationController {

  def manageApps: Action[AnyContent] = loggedInAction2 { implicit request =>
    applicationService.fetchByTeamMemberEmail(loggedIn2.email) flatMap { apps =>
      if (apps.isEmpty) {
        Future.successful(Ok(views.html.addApplicationSubordinateEmptyNest()))
      } else {
        Future.successful(Ok(views.html.manageApplications(apps.map(ApplicationSummary.from(_, loggedIn2.email)))))
      }
    }
  }

  def tenDaysWarning(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(views.html.tenDaysWarning()))
  }

  def addApplicationSubordinate(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(views.html.addApplicationStartSubordinate()))
  }

  def addApplicationPrincipal(): Action[AnyContent] = loggedInAction2 { implicit request =>
    Future.successful(Ok(views.html.addApplicationStartPrincipal()))
  }

  def addApplicationSuccess(applicationId: String, notUsedEnvironment: Environment): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>

      applicationService.fetchByApplicationId(applicationId).map(_.deployedTo).map {
        case SANDBOX => Ok(views.html.addApplicationSubordinateSuccess(request.application.name, applicationId))
      }.recoverWith {
        case NonFatal(_) =>
          Future.successful(NotFound(errorHandler.notFoundTemplate(request)))
      }
    }

  def addApplicationName(environment: Environment): Action[AnyContent] = loggedInAction2 { implicit request =>
    val form = AddApplicationNameForm.form.fill(AddApplicationNameForm(""))
    Future.successful(Ok(views.html.addApplicationName(form, environment)))
  }

  def editApplicationNameAction(environment: Environment): Action[AnyContent] = loggedInAction2 {
    implicit request =>

      val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

      def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: Environment) =
        Future.successful(Ok(views.html.addApplicationName(errors, environment)))

      def addApplication(form: AddApplicationNameForm) = {
        applicationService
          .createForUser(CreateApplicationRequest.fromAddApplicationJourney(loggedIn2, form, environment))
      }

      def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) =
        applicationService.isApplicationNameValid(
          formThatPassesSimpleValidation.applicationName,
          environment,
          None)
          .flatMap {
            case Valid =>
              addApplication(formThatPassesSimpleValidation).map(
                applicationCreatedResponse => environment match {
                  case PRODUCTION => Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(applicationCreatedResponse.id))
                  case SANDBOX => Redirect(routes.Subscriptions.addAppSubscriptions(applicationCreatedResponse.id, environment))
                }
              )

            case invalid: Invalid =>
              def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

              Future.successful(BadRequest(views.html.addApplicationName(invalidApplicationNameForm, environment)))
          }

      requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
  }
}
