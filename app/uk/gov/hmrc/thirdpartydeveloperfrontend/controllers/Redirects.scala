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
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.RedirectUri
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsRedirects
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, RedirectsService, SessionService}

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
    redirectsService: RedirectsService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc) with FraudPreventionNavLinkHelper with WithUnsafeDefaultFormBinding {

  def canChangeRedirectInformationAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsRedirects, SandboxOrAdmin)(applicationId)(fun)

  def redirects(applicationId: ApplicationId) = checkActionForApprovedApps(SupportsRedirects, TeamMembersOnly)(applicationId) { implicit request =>
    val appAccess = request.application.access.asInstanceOf[Access.Standard]
    successful(Ok(redirectsView(
      applicationViewModelFromApplicationRequest(),
      appAccess.redirectUris,
      createOptionalFraudPreventionNavLinkViewModel(request.application, request.subscriptions, fraudPreventionConfig)
    )))
  }

  def addRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(addRedirectView(applicationViewModelFromApplicationRequest(), AddRedirectForm.form)))
  }

  def addRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.developerSession.email)

    def handleValidForm(form: AddRedirectForm) = {
      if (application.hasRedirectUri(RedirectUri.unsafeApply(form.redirectUri))) {
        successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest(), AddRedirectForm.form.fill(form).withError("redirectUri", "redirect.uri.duplicate"))))
      } else {
        redirectsService.addRedirect(actor, application, RedirectUri.unsafeApply(form.redirectUri))
          .map(_ => Redirect(routes.Redirects.redirects(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddRedirectForm]) = {
      successful(BadRequest(addRedirectView(applicationViewModelFromApplicationRequest(), formWithErrors)))
    }

    AddRedirectForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    def handleValidForm(form: DeleteRedirectForm) = {
      successful(Ok(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest(), DeleteRedirectConfirmationForm.form, form.redirectUri)))
    }

    def handleInvalidForm(formWithErrors: Form[DeleteRedirectForm]) = {
      successful(Redirect(routes.Redirects.redirects(applicationId)))
    }

    DeleteRedirectForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.developerSession.email)

    def handleValidForm(form: DeleteRedirectConfirmationForm) = {
      form.deleteRedirectConfirm match {
        case Some("Yes") =>
          redirectsService.deleteRedirect(actor, application, RedirectUri.unsafeApply(form.redirectUri))
            .map(_ => Redirect(routes.Redirects.redirects(applicationId)))
        case _           => successful(Redirect(routes.Redirects.redirects(application.id)))
      }
    }

    def handleInvalidForm(form: Form[DeleteRedirectConfirmationForm]) = {
      successful(BadRequest(deleteRedirectConfirmationView(applicationViewModelFromApplicationRequest(), form, form("redirectUri").value.getOrElse(""))))
    }

    DeleteRedirectConfirmationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def changeRedirect(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    successful(Ok(changeRedirectView(applicationViewModelFromApplicationRequest(), ChangeRedirectForm.form.bindFromRequest())))
  }

  def changeRedirectAction(applicationId: ApplicationId) = canChangeRedirectInformationAction(applicationId) { implicit request =>
    val application = request.application
    val actor       = Actors.AppCollaborator(request.developerSession.email)

    def handleValidForm(form: ChangeRedirectForm) = {

      if (form.originalRedirectUri == form.newRedirectUri)
        successful(Redirect(routes.Redirects.redirects(applicationId)))
      else {
        application.access match {
          case app: Access.Standard =>
            if (app.redirectUris.contains(RedirectUri.unsafeApply(form.newRedirectUri))) {
              handleInvalidForm(
                ChangeRedirectForm.form
                  .fill(form)
                  .withError("newRedirectUri", "redirect.uri.duplicate")
              )
            } else
              redirectsService.changeRedirect(actor, application, new RedirectUri(form.originalRedirectUri), RedirectUri.unsafeApply(form.newRedirectUri))
                .map(_ => Redirect(routes.Redirects.redirects(applicationId)))
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
