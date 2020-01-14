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

import java.io

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.FormKeys.{appNameField, applicationNameInvalidKey, emailaddressAlreadyInUseGlobalKey, emailaddressField, emailalreadyInUseKey}
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import service._
import views.html._

import scala.concurrent.{ExecutionContext, Future}

// TODO: This is from the old journey. Needs removing?
@Singleton
class OldManageApplications @Inject()(val applicationService: ApplicationService,
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

  def addApplication() = loggedInAction { implicit request =>
    Future.successful(Ok(views.html.addApplication(AddApplicationForm.form)))
  }

  def addApplicationAction() = loggedInAction { implicit request =>
    val requestForm: Form[AddApplicationForm] = AddApplicationForm.form.bindFromRequest

    def addApplicationWithFormErrors(errors: Form[AddApplicationForm]) =
      Future.successful(BadRequest(views.html.addApplication(errors)))

    def addApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationForm) = {

      val environment = formThatPassesSimpleValidation.environment.flatMap(Environment.from).getOrElse(Environment.SANDBOX)

      applicationService.isApplicationNameValid(formThatPassesSimpleValidation.applicationName, environment, selfApplicationId = None).flatMap {
        case Valid => {
          applicationService
            .createForUser(CreateApplicationRequest.from(loggedIn, formThatPassesSimpleValidation))
            .map(appCreated => {
              Created(addApplicationSuccess(
                formThatPassesSimpleValidation.applicationName,
                appCreated.id,
                environment
              ))
            })
        }
        case invalid : Invalid => {
          def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

          Future.successful(BadRequest(views.html.addApplication(invalidApplicationNameForm)))
        }
      }
    }

    requestForm.fold(addApplicationWithFormErrors, addApplicationWithValidForm)
  }

  def editApplication(applicationId: String, error: Option[String] = None) = whenTeamMemberOnApp(applicationId) { implicit request =>
    Future.successful(Redirect(routes.Details.details(applicationId)))
  }
}
