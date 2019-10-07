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
import connectors.ThirdPartyDeveloperConnector
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import service._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeleteApplication @Inject()(developerConnector: ThirdPartyDeveloperConnector,
                                  auditService: AuditService,
                                  val applicationService: ApplicationService,
                                  val sessionService: SessionService,
                                  val errorHandler: ErrorHandler,
                                  val messagesApi: MessagesApi,
                                  implicit val appConfig: ApplicationConfig)
                                 (implicit ec: ExecutionContext)
  extends ApplicationController {

  def deleteApplication(applicationId: String, error: Option[String] = None) = teamMemberOnStandardApp(applicationId) { implicit request =>
    val view = views.html.deleteApplication(request.application, request.role)
    Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def deletePrincipalApplicationConfirm(applicationId: String, error: Option[String] = None) = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>
    val view = views.html.deletePrincipalApplicationConfirm(request.application, DeletePrincipalApplicationForm.form.fill(DeletePrincipalApplicationForm(None)))
    Future(error.map(_ => BadRequest(view)).getOrElse(Ok(view)))
  }

  def deletePrincipalApplicationAction(applicationId: String) = sandboxOrAdminIfProductionForStandardApp(applicationId) { implicit request =>
    val application = request.application

    def handleInvalidForm(formWithErrors: Form[DeletePrincipalApplicationForm]) =
      Future(BadRequest(views.html.deletePrincipalApplicationConfirm(application, formWithErrors)))

    def handleValidForm(validForm: DeletePrincipalApplicationForm) = {
      validForm.deleteConfirm match {
        case Some("Yes") => applicationService.requestPrincipalApplicationDeletion(request.user, application)
          .map(_ => Ok(views.html.deletePrincipalApplicationComplete(application)))
        case _ => Future(Redirect(routes.Details.details(applicationId)))
      }
    }

    DeletePrincipalApplicationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
