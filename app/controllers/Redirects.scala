/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import controllers.fraudprevention.FraudPreventionNavLinkHelper
import domain.models.applications.{ApplicationId, Standard, UpdateApplicationRequest}
import domain.models.applications.Capabilities.SupportsRedirects
import domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service.{ApplicationService, SessionService, ApplicationActionService}
import views.html.{AddRedirectView, ChangeRedirectView, DeleteRedirectConfirmationView, RedirectsView}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

@Singleton
class Redirects @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    redirectsView: RedirectsView,
    addRedirectView: AddRedirectView,
    deleteRedirectConfirmationView: DeleteRedirectConfirmationView,
    changeRedirectView: ChangeRedirectView,
    val fraudPreventionConfig: FraudPreventionConfig
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) with FraudPreventionNavLinkHelper{

  def canChangeRedirectInformationAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsRedirects, SandboxOrAdmin)(applicationId)(fun)

  def redirects(applicationId: ApplicationId) = checkActionForApprovedApps(SupportsRedirects, TeamMembersOnly)(applicationId) { implicit request =>
    val appAccess = request.application.access.asInstanceOf[Standard]
    successful(Ok(redirectsView(applicationViewModelFromApplicationRequest, appAccess.redirectUris, createOptionalFraudPreventionNavLinkViewModel(request.application, request.subscriptions, fraudPreventionConfig))))
  }

  def addRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(addRedirectView(applicationViewModelFromApplicationRequest, AddRedirectForm.form)))
  }

  def addRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: AddRedirectForm) = {
      if (application.hasRedirectUri(form.redirectUri)) {
        successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest, AddRedirectForm.form.fill(form).withError("redirectUri", "redirect.uri.duplicate"))))
      } else {
        applicationService.update(UpdateApplicationRequest.from(application, form)).map(_ => Redirect(routes.Redirects.redirects(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddRedirectForm]) = {
      successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest, formWithErrors)))
    }

    AddRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    def handleValidForm(form: DeleteRedirectForm) = {
      successful(Ok(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest, DeleteRedirectConfirmationForm.form, form.redirectUri)))
    }

    def handleInvalidForm(formWithErrors: Form[DeleteRedirectForm]) = {
      successful(Redirect(routes.Redirects.redirects(applicationId)))
    }

    DeleteRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: DeleteRedirectConfirmationForm) = {
      form.deleteRedirectConfirm match {
        case Some("Yes") =>
          applicationService
            .update(UpdateApplicationRequest.from(application, form))
            .map(_ => Redirect(routes.Redirects.redirects(application.id)))
        case _ => successful(Redirect(routes.Redirects.redirects(application.id)))
      }
    }

    def handleInvalidForm(form: Form[DeleteRedirectConfirmationForm]) = {
      successful(BadRequest(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest, form, form("redirectUri").value.getOrElse(""))))
    }

    DeleteRedirectConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def changeRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(changeRedirectView(applicationViewModelFromApplicationRequest, ChangeRedirectForm.form.bindFromRequest())))
  }

  def changeRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    def handleValidForm(form: ChangeRedirectForm) = {
      def updateUris() = {
        applicationService.update(UpdateApplicationRequest.from(request.application, form)).map(_ => Redirect(routes.Redirects.redirects(applicationId)))
      }

      if (form.originalRedirectUri == form.newRedirectUri) successful(Redirect(routes.Redirects.redirects(applicationId)))
      else {
        request.application.access match {
          case app: Standard =>
            if (app.redirectUris.contains(form.newRedirectUri))
              handleInvalidForm(
                ChangeRedirectForm.form
                  .fill(form)
                  .withError("newRedirectUri", "redirect.uri.duplicate")
              )
            else updateUris()
          case _ => successful(Redirect(routes.Details.details(applicationId)))
        }
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeRedirectForm]) = {
      successful(BadRequest(changeRedirectView(applicationViewModelFromApplicationRequest, formWithErrors)))
    }

    ChangeRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
