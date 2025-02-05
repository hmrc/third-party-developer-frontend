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

import views.html.{AddRedirectView, ChangeRedirectView, DeleteRedirectConfirmationView, RedirectsView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.LoginRedirectUri
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationSyntaxes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsRedirects
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, LoginRedirectsService, SessionService}

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
    val fraudPreventionConfig: FraudPreventionConfig,
    loginRedirectsService: LoginRedirectsService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc) with FraudPreventionNavLinkHelper with WithUnsafeDefaultFormBinding with ApplicationSyntaxes {

  def canChangeRedirectInformationAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsRedirects, SandboxOrAdmin)(applicationId)(fun)

  def loginRedirects(applicationId: ApplicationId) = checkActionForApprovedApps(SupportsRedirects, TeamMembersOnly)(applicationId) { implicit request =>
    val appAccess = request.application.access.asInstanceOf[Access.Standard]
    successful(Ok(redirectsView(
      applicationViewModelFromApplicationRequest(),
      appAccess.redirectUris,
      createOptionalFraudPreventionNavLinkViewModel(request.application, request.subscriptions, fraudPreventionConfig)
    )))
  }

  def addLoginRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(addRedirectView(applicationViewModelFromApplicationRequest(), AddRedirectForm.form)))
  }

  def addLoginRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.userSession.developer.email)

    def handleValidForm(form: AddRedirectForm) = {
      if (application.hasRedirectUri(LoginRedirectUri.unsafeApply(form.redirectUri))) {
        successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest(), AddRedirectForm.form.fill(form).withError("redirectUri", "redirect.uri.duplicate"))))
      } else {
        loginRedirectsService.addLoginRedirect(actor, application, LoginRedirectUri.unsafeApply(form.redirectUri))
          .map(_ => Redirect(routes.Redirects.loginRedirects(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddRedirectForm]) = {
      successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest(), formWithErrors)))
    }

    AddRedirectForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteLoginRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    def handleValidForm(form: DeleteRedirectForm) = {
      successful(Ok(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest(), DeleteRedirectConfirmationForm.form, form.redirectUri)))
    }

    def handleInvalidForm(formWithErrors: Form[DeleteRedirectForm]) = {
      successful(Redirect(routes.Redirects.loginRedirects(applicationId)))
    }

    DeleteRedirectForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteLoginRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.userSession.developer.email)

    def handleValidForm(form: DeleteRedirectConfirmationForm) = {
      form.deleteRedirectConfirm match {
        case Some("Yes") =>
          loginRedirectsService.deleteLoginRedirect(actor, application, LoginRedirectUri.unsafeApply(form.redirectUri))
            .map(_ => Redirect(routes.Redirects.loginRedirects(applicationId)))
        case _           => successful(Redirect(routes.Redirects.loginRedirects(application.id)))
      }
    }

    def handleInvalidForm(form: Form[DeleteRedirectConfirmationForm]) = {
      successful(BadRequest(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest(), form, form("redirectUri").value.getOrElse(""))))
    }

    DeleteRedirectConfirmationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def changeLoginRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(changeRedirectView(applicationViewModelFromApplicationRequest(), ChangeRedirectForm.form.bindFromRequest())))
  }

  def changeLoginRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.userSession.developer.email)

    def handleValidForm(form: ChangeRedirectForm) = {

      if (form.originalRedirectUri == form.newRedirectUri)
        successful(Redirect(routes.Redirects.loginRedirects(applicationId)))
      else {
        application.access match {
          case app: Access.Standard =>
            if (app.redirectUris.contains(LoginRedirectUri.unsafeApply(form.newRedirectUri))) {
              handleInvalidForm(
                ChangeRedirectForm.form
                  .fill(form)
                  .withError("newRedirectUri", "redirect.uri.duplicate")
              )
            } else
              loginRedirectsService.changeLoginRedirect(actor, application, new LoginRedirectUri(form.originalRedirectUri), LoginRedirectUri.unsafeApply(form.newRedirectUri))
                .map(_ => Redirect(routes.Redirects.loginRedirects(applicationId)))
          case _                    => successful(Redirect(routes.Details.details(applicationId)))
        }
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeRedirectForm]) = {
      successful(BadRequest(changeRedirectView(applicationViewModelFromApplicationRequest(), formWithErrors)))
    }

    ChangeRedirectForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
