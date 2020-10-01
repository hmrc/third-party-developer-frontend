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
import domain.models.applications.ApplicationId
import domain.models.applications.Capabilities.SupportsIpWhitelist
import domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import domain.models.flows.IpAllowlistFlow
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service._
import views.html.ipAllowlist.{AddIpAddressView, EditIpAllowlistView, IpAllowlistView}
import views.html.ipwhitelist.{ChangeIpWhitelistSuccessView, ChangeIpWhitelistView, ManageIpWhitelistView}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IpAllowlist @Inject()(
    deskproService: DeskproService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    manageIpWhitelistView: ManageIpWhitelistView,
    changeIpWhitelistView: ChangeIpWhitelistView,
    changeIpWhitelistSuccessView: ChangeIpWhitelistSuccessView,
    ipAllowlistService: IpAllowlistService,
    ipAllowlistView: IpAllowlistView,
    editIpAllowlistView: EditIpAllowlistView,
    addIpAddressView: AddIpAddressView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) {

  private def canChangeIpAllowlistAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsIpWhitelist, AdministratorOnly)(applicationId)(fun)

  private def canViewIpAllowlistAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsIpWhitelist, TeamMembersOnly)(applicationId)(fun)

  def viewIpAllowlist(applicationId: ApplicationId): Action[AnyContent] =
    canViewIpAllowlistAction(applicationId) { implicit request => successful(Ok(ipAllowlistView(request.application, request.role))) }

  def editIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canViewIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.getIpAllowlistFlow(request.application, request.user.session.sessionId) map { flow =>
      Ok(editIpAllowlistView(request.application, flow, AddAnotherIpAddressConfirmForm.form.fill(AddAnotherIpAddressConfirmForm())))
    }
  }

  def editIpAllowlistAction(applicationId: ApplicationId): Action[AnyContent] = canViewIpAllowlistAction(applicationId) { implicit request =>
    def handleValidForm(form: AddAnotherIpAddressConfirmForm): Future[Result] = {
      form.confirm match {
        case Some("Yes") => successful(Ok(addIpAddressView(request.application, AddIpAddressForm.form.fill(AddIpAddressForm("")))))
        case _ => successful(Ok(addIpAddressView(request.application, AddIpAddressForm.form.fill(AddIpAddressForm("")))))//TODO
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddAnotherIpAddressConfirmForm]): Future[Result] = {
      ipAllowlistService.getIpAllowlistFlow(request.application, request.user.session.sessionId) map { flow =>
        BadRequest(editIpAllowlistView(request.application, flow, formWithErrors))
      }
    }

    AddAnotherIpAddressConfirmForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }



  def manageIpWhitelist(applicationId: ApplicationId): Action[AnyContent] =
    canViewIpAllowlistAction(applicationId) { implicit request => successful(Ok(manageIpWhitelistView(request.application, request.role))) }

  def changeIpWhitelist(applicationId: ApplicationId): Action[AnyContent] =
    canChangeIpAllowlistAction(applicationId) { implicit request =>
      successful(Ok(changeIpWhitelistView(request.application, ChangeIpWhitelistForm.form.fill(ChangeIpWhitelistForm("")))))
    }

  def changeIpWhitelistAction(applicationId: ApplicationId): Action[AnyContent] =
    canChangeIpAllowlistAction(applicationId) { implicit request =>
      def handleValidForm(form: ChangeIpWhitelistForm): Future[Result] = {
        val developer = request.user.developer
        val supportForm = SupportEnquiryForm(s"${developer.firstName} ${developer.lastName}", developer.email, form.description)
        deskproService.submitSupportEnquiry(supportForm) map { _ => Ok(changeIpWhitelistSuccessView(request.application)) }
      }

      def handleInvalidForm(formWithErrors: Form[ChangeIpWhitelistForm]): Future[Result] = {
        successful(BadRequest(changeIpWhitelistView(request.application, formWithErrors)))
      }

      ChangeIpWhitelistForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }
}
