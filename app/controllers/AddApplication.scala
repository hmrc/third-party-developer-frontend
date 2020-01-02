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
import play.api.mvc.Result
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

  def manageApps = loggedInAction { implicit request =>
    applicationService.fetchByTeamMemberEmail(loggedIn.email) flatMap { apps =>
      if (apps.isEmpty) {
        Future.successful(Ok(views.html.addApplicationSubordinateEmptyNest()))
      } else {
        Future.successful(Ok(views.html.manageApplications(apps.map(ApplicationSummary.from(_, loggedIn.email)))))
      }
    }
  }

  def addApplicationSubordinate = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.addApplicationStartSubordinate()))
  }

  def addApplicationSuccess(applicationId: String) = whenTeamMemberOnApp(applicationId) { implicit request =>
    applicationService.fetchByApplicationId(applicationId).map(_.deployedTo).flatMap{
      case SANDBOX =>
        Future.successful(Ok(views.html.addApplicationSubordinateSuccess(request.application.name, applicationId)))
      case PRODUCTION =>
        Future.successful(Ok(views.html.addApplicationPrincipalSuccess(request.application.name, applicationId)))
    }.recoverWith {
      case NonFatal(_) =>
        val x = Future.successful(NotFound(errorHandler.notFoundTemplate(request)))
        x
    }
  }

  def nameAddApplication(environment: String) = loggedInAction { implicit request =>
    Environment.from(environment) match {
      case Some(SANDBOX) =>
        Future.successful(Ok(views.html.addApplicationName(AddApplicationNameForm.form, Environment.from(environment))))
      case Some(PRODUCTION) =>
        Future.successful(Ok(views.html.addApplicationName(AddApplicationNameForm.form, Environment.from(environment))))
      case _ => Future.successful(NotFound(errorHandler.notFoundTemplate(request)))
    }
  }

    def nameApplicationAction(environment: String) = loggedInAction { implicit request =>
      val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

      def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: String) =
        Future.successful(Ok(views.html.addApplicationName(errors, Environment.from(environment))))

      def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) = {
        applicationService.isApplicationNameValid(formThatPassesSimpleValidation.applicationName,
          Environment.from(environment).getOrElse(SANDBOX), selfApplicationId = None).flatMap {
          case Valid =>
            applicationService
              .createForUser(CreateApplicationRequest.fromSandboxJourney(loggedIn, formThatPassesSimpleValidation,
                Environment.from(environment).getOrElse(SANDBOX)))
              .map(appCreated => {
                if (Environment.from(environment) == Some(PRODUCTION)) {
                  Redirect(routes.AddApplication.addApplicationSuccess(appCreated.id))
                } else Redirect(routes.Subscriptions.subscriptions(appCreated.id))
              })
          case invalid: Invalid => {
            def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            Future.successful(BadRequest(views.html.addApplicationName(invalidApplicationNameForm, Environment.from(environment))))
          }
        }
      }

      requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
    }
}