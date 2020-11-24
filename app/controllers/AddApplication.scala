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
import domain.models.applications._
import domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import domain.ApplicationCreatedResponse
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.emailpreferences.EmailPreferences
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service._
import views.helper.EnvironmentNameService
import views.html._

import scala.collection.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

@Singleton
class AddApplication @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    val auditService: AuditService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    addApplicationSubordinateEmptyNestView: AddApplicationSubordinateEmptyNestView,
    manageApplicationsView: ManageApplicationsView,
    accessTokenSwitchView: AccessTokenSwitchView,
    usingPrivilegedApplicationCredentialsView: UsingPrivilegedApplicationCredentialsView,
    tenDaysWarningView: TenDaysWarningView,
    addApplicationStartSubordinateView: AddApplicationStartSubordinateView,
    addApplicationStartPrincipalView: AddApplicationStartPrincipalView,
    addApplicationSubordinateSuccessView: AddApplicationSubordinateSuccessView,
    addApplicationNameView: AddApplicationNameView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig, val environmentNameService: EnvironmentNameService)
    extends ApplicationController(mcc) {

  def manageApps: Action[AnyContent] = loggedInAction { implicit request =>
    applicationService.fetchByTeamMemberEmail(loggedIn.email) flatMap { apps =>
      if (apps.isEmpty) {
        successful(Ok(addApplicationSubordinateEmptyNestView()))
      } else {
        successful(Ok(manageApplicationsView(apps.map(ApplicationSummary.from(_, loggedIn.email)))))
      }
    }
  }

  def accessTokenSwitchPage(): Action[AnyContent] = loggedInAction { implicit request => successful(Ok(accessTokenSwitchView())) }

  def usingPrivilegedApplicationCredentialsPage(): Action[AnyContent] =
    loggedInAction { implicit request => successful(Ok(usingPrivilegedApplicationCredentialsView())) }

  def tenDaysWarning(): Action[AnyContent] = loggedInAction { implicit request => successful(Ok(tenDaysWarningView())) }

  def addApplicationSubordinate(): Action[AnyContent] = loggedInAction { implicit request => successful(Ok(addApplicationStartSubordinateView())) }

  def addApplicationPrincipal(): Action[AnyContent] = loggedInAction { implicit request => successful(Ok(addApplicationStartPrincipalView())) }

  def addApplicationSuccess(applicationId: ApplicationId): Action[AnyContent] = {

    def subscriptionsNotInUserEmailPreferences(applicationSubscriptions: Seq[APISubscriptionStatus],
                                               userEmailPreferences: EmailPreferences): Set[String] = {
      applicationSubscriptions
        .map(_.serviceName)
        .diff(userEmailPreferences.interests.flatMap(_.services))
        .toSet
    }

    whenTeamMemberOnApp(applicationId) { implicit appRequest =>
      import appRequest._

      successful(
        deployedTo match {
          case SANDBOX    => {
            val missingSubscriptions = subscriptionsNotInUserEmailPreferences(subscriptions.filter(_.subscribed), user.developer.emailPreferences)
            if(missingSubscriptions.isEmpty) {
              Ok(addApplicationSubordinateSuccessView(application.name, applicationId))
            } else {
              Redirect(controllers.profile.routes.EmailPreferences.selectApisFromSubscriptionsPage())
                .flashing("missingSubscriptions" -> missingSubscriptions.mkString(","))
            }
          }
          case PRODUCTION => NotFound(errorHandler.notFoundTemplate(request))
        }
      )
    }
  }

  def addApplicationName(environment: Environment): Action[AnyContent] = loggedInAction { implicit request =>
    val form = AddApplicationNameForm.form.fill(AddApplicationNameForm(""))
    successful(Ok(addApplicationNameView(form, environment)))
  }

  def editApplicationNameAction(environment: Environment): Action[AnyContent] = loggedInAction { implicit request =>
    val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

    def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: Environment) =
      successful(Ok(addApplicationNameView(errors, environment)))

    def addApplication(form: AddApplicationNameForm): Future[ApplicationCreatedResponse] = {
      applicationService
        .createForUser(CreateApplicationRequest.fromAddApplicationJourney(loggedIn, form, environment))
    }

    def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) =
      applicationService
        .isApplicationNameValid(formThatPassesSimpleValidation.applicationName, environment, None)
        .flatMap {
          case Valid =>
            addApplication(formThatPassesSimpleValidation).map(applicationCreatedResponse =>
              environment match {
                case PRODUCTION => Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(applicationCreatedResponse.id))
                case SANDBOX    => Redirect(routes.Subscriptions.addAppSubscriptions(applicationCreatedResponse.id))
              }
            )

          case invalid: Invalid =>
            def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            successful(BadRequest(addApplicationNameView(invalidApplicationNameForm, environment)))
        }

    requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
  }
}
