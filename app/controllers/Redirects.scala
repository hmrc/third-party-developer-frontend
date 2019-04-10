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
import domain.{Standard, UpdateApplicationRequest}
import javax.inject.{Inject, Singleton}
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import service.{ApplicationService, SessionService}

import scala.concurrent.Future.successful

@Singleton
class Redirects @Inject()(val applicationService: ApplicationService,
                          val sessionService: SessionService,
                          val errorHandler: ErrorHandler,
                          implicit val appConfig: ApplicationConfig)
  extends ApplicationController {

  def redirects(applicationId: String) = teamMemberOnStandardApp(applicationId) { implicit request =>
    val appAccess = request.application.access.asInstanceOf[Standard]
    successful(Ok(views.html.redirects(request.application, appAccess.redirectUris, request.role)))
  }

  def addRedirect(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    successful(Ok(views.html.addRedirect(request.application, AddRedirectForm.form)))
  }

  def addRedirectAction(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: AddRedirectForm) = {
      if (application.hasRedirectUri(form.redirectUri)) {
        successful(BadRequest(
          views.html.addRedirect(application, AddRedirectForm.form.fill(form).withError("redirectUri", "redirect.uri.duplicate"))))
      }
      else {
        applicationService.update(UpdateApplicationRequest.from(application, form)).map(_ => Redirect(routes.Redirects.redirects(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddRedirectForm]) = {
      successful(BadRequest(views.html.addRedirect(application, formWithErrors)))
    }

    AddRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirect(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: DeleteRedirectForm) = {
      successful(Ok(views.html.deleteRedirectConfirmation(application, DeleteRedirectConfirmationForm.form, form.redirectUri)))
    }

    def handleInvalidForm(formWithErrors: Form[DeleteRedirectForm]) = {
      successful(Redirect(routes.Redirects.redirects(applicationId)))
    }

    DeleteRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def deleteRedirectAction(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    val application = request.application

    def handleValidForm(form: DeleteRedirectConfirmationForm) = {
      form.deleteRedirectConfirm match {
        case Some("Yes") => applicationService.update(UpdateApplicationRequest.from(application, form))
          .map(_ => Redirect(routes.Redirects.redirects(application.id)))
        case _ => successful(Redirect(routes.Redirects.redirects(application.id)))
      }
    }

    def handleInvalidForm(form: Form[DeleteRedirectConfirmationForm]) = {
      successful(BadRequest(views.html.deleteRedirectConfirmation(application, form, form("redirectUri").value.getOrElse(""))))
    }

    DeleteRedirectConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def changeRedirect(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    successful(Ok(views.html.changeRedirect(request.application, ChangeRedirectForm.form.bindFromRequest())))
  }

  def changeRedirectAction(applicationId: String) = adminIfStandardProductionApp(applicationId) { implicit request =>
    def handleValidForm(form: ChangeRedirectForm) = {
      def updateUris() = {
        applicationService.update(UpdateApplicationRequest.from(request.application, form)).map(_ => Redirect(routes.Redirects.redirects(applicationId)))
      }

      if (form.originalRedirectUri == form.newRedirectUri) successful(Redirect(routes.Redirects.redirects(applicationId)))
      else {
        request.application.access match {
          case app: Standard => if (app.redirectUris.contains(form.newRedirectUri)) handleInvalidForm(ChangeRedirectForm.form.fill(form)
            .withError("newRedirectUri", "redirect.uri.duplicate"))
          else updateUris()
          case _ => successful(Redirect(routes.Details.details(applicationId)))
        }
      }
    }

    def handleInvalidForm(formWithErrors: Form[ChangeRedirectForm]) = {
      successful(BadRequest(views.html.changeRedirect(request.application, formWithErrors)))
    }

    ChangeRedirectForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
