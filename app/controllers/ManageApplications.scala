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

import java.io

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.FormKeys.{appNameField, applicationNameInvalid2Key, emailaddressAlreadyInUseGlobalKey, emailaddressField, emailalreadyInUseKey}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import service._
import views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageApplications @Inject()(val applicationService: ApplicationService,
                                   val developerConnector: ThirdPartyDeveloperConnector,
                                   val sessionService: SessionService,
                                   val auditService: AuditService,
                                   val errorHandler: ErrorHandler,
                                   val messagesApi: MessagesApi,
                                   implicit val appConfig: ApplicationConfig)
                                  (implicit ec: ExecutionContext) extends ApplicationController {

  val detailsTab = "details"
  val credentialsTab = "credentials"
  val subscriptionsTab = "subscriptions"

  val rolesTab = "roles"

  def manageApps = loggedInAction { implicit request =>
    applicationService.fetchByTeamMemberEmail(loggedIn.email) map { apps =>
      Ok(views.html.manageApplications(apps.map(ApplicationSummary.from(_, loggedIn.email))))
    }
  }

  def addApplication() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.addApplication(AddApplicationForm.form)))
  }

  def addApplicationAction() = loggedInAction { implicit request =>
    val requestForm: Form[AddApplicationForm] = AddApplicationForm.form.bindFromRequest

    def addApplicationWithFormErrors(errors: Form[AddApplicationForm]) =
      Future.successful(BadRequest(views.html.addApplication(errors)))

    def addApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationForm) = {

      val environment = formThatPassesSimpleValidation.environment.flatMap(Environment.from).getOrElse(Environment.SANDBOX)

      // TODO: Use correct environment
      applicationService.isApplicationNameValid(formThatPassesSimpleValidation.applicationName, environment)
        .flatMap {
          case Valid => {
            applicationService
              .createForUser(CreateApplicationRequest.from(loggedIn, formThatPassesSimpleValidation))
              .map(appCreated => {
                Created(addApplicationSuccess(
                  formThatPassesSimpleValidation.applicationName,
                  appCreated.id,
                  environment.toString // TODO: Does this need to be a string?
                  ))
              })
          }
          case Invalid(invalidName, duplicateName) => {
            def invalidApplicationNameForm = requestForm.
              withError("submissionError", "true").
              withError(appNameField, applicationNameInvalid2Key, controllers.routes.ManageApplications.addApplicationAction()).
              withGlobalError(applicationNameInvalid2Key)

            Future.successful(BadRequest(views.html.addApplication(invalidApplicationNameForm)))
          }
        }
    }

    requestForm.fold(addApplicationWithFormErrors, addApplicationWithValidForm)
  }

  // TODO - Delete
  //  def addApplicationAction2() = loggedInAction { implicit request =>
  //    val requestForm = AddApplicationForm.form.bindFromRequest
  //
  //    def addApplicationWithFormErrors(errors: Form[AddApplicationForm]) =
  //      Future.successful(BadRequest(views.html.addApplication(errors)))
  //
  //    def addApplicationWithValidForm(validForm: AddApplicationForm) = {
  //
  //      val y: Future[Result] = applicationService.createForUser(CreateApplicationRequest.from(loggedIn, validForm))
  //        .map(appCreated => {
  //          val x: Result = Created(addApplicationSuccess(validForm.applicationName, appCreated.id, validForm.environment.getOrElse(Environment.SANDBOX.toString)))
  //          x
  //        })
  //
  //      y
  //    }
  //
  //    requestForm.fold(addApplicationWithFormErrors, addApplicationWithValidForm)
  //  }

  def editApplication(applicationId: String, error: Option[String] = None) = teamMemberOnApp(applicationId) { implicit request =>
    Future.successful(Redirect(routes.Details.details(applicationId)))
  }
}
