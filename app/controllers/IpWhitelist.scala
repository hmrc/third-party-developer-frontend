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
import domain.Capabilities.SupportsIpWhitelist
import domain.Permissions.{AdministratorOnly, TeamMembersOnly}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service._

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IpWhitelist @Inject()(deskproService: DeskproService,
                            val applicationService: ApplicationService,
                            val sessionService: SessionService,
                            val errorHandler: ErrorHandler,
                            val messagesApi: MessagesApi
                            )
                           (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController {

  private def canChangeIpWhitelistAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsIpWhitelist, AdministratorOnly)(applicationId)(fun)

  private def canViewManageIpWhitelistAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsIpWhitelist, TeamMembersOnly)(applicationId)(fun)

  def manageIpWhitelist(applicationId: String): Action[AnyContent] =
    canViewManageIpWhitelistAction(applicationId) { implicit request =>
      successful(Ok(views.html.ipwhitelist.manageIpWhitelist(request.application, request.role)))
    }

  def changeIpWhitelist(applicationId: String): Action[AnyContent] =
    canChangeIpWhitelistAction(applicationId) { implicit request =>
      successful(Ok(views.html.ipwhitelist.changeIpWhitelist(request.application, ChangeIpWhitelistForm.form.fill(ChangeIpWhitelistForm("")))))
    }

  def changeIpWhitelistAction(applicationId: String): Action[AnyContent] =
    canChangeIpWhitelistAction(applicationId) { implicit request =>
      def handleValidForm(form: ChangeIpWhitelistForm): Future[Result] = {
        val developer = request.user.developer
        val supportForm = SupportEnquiryForm(s"${developer.firstName} ${developer.lastName}", developer.email, form.description)
        deskproService.submitSupportEnquiry(supportForm) map { _ =>
          Ok(views.html.ipwhitelist.changeIpWhitelistSuccess(request.application))
        }
      }

      def handleInvalidForm(formWithErrors: Form[ChangeIpWhitelistForm]): Future[Result] = {
        successful(BadRequest(views.html.ipwhitelist.changeIpWhitelist(request.application, formWithErrors)))
      }

      ChangeIpWhitelistForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }
}
