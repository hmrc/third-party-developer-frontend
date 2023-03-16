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

import views.html.ipAllowlist._

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.ForbiddenException
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsIpAllowlist
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class IpAllowListController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    ipAllowlistService: IpAllowlistService,
    ipAllowlistView: IpAllowlistView,
    editIpAllowlistView: EditIpAllowlistView,
    addCidrBlockView: AddCidrBlockView,
    reviewIpAllowlistView: ReviewIpAllowlistView,
    changeIpAllowlistSuccessView: ChangeIpAllowlistSuccessView,
    startIpAllowlistView: StartIpAllowlistView,
    allowedIpsView: AllowedIpsView,
    settingUpAllowlistView: SettingUpAllowlistView,
    removeIpAllowlistView: RemoveIpAllowlistView,
    removeIpAllowlistSuccessView: RemoveIpAllowlistSuccessView,
    removeCidrBlockView: RemoveCidrBlockView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc) with WithUnsafeDefaultFormBinding {

  private def canViewIpAllowlistAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsIpAllowlist, TeamMembersOnly)(applicationId)(fun)

  private def canEditIpAllowlistAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsIpAllowlist, SandboxOrAdmin)(applicationId)(fun)

  def viewIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canViewIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.discardIpAllowlistFlow(request.sessionId) map { _ =>
      if (request.application.ipAllowlist.allowlist.isEmpty) {
        Ok(startIpAllowlistView(request.application, request.role))
      } else {
        Ok(ipAllowlistView(request.application, request.role))
      }
    }
  }

  def allowedIps(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    successful(Ok(allowedIpsView(request.application)))
  }

  def settingUpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    successful(Ok(settingUpAllowlistView(request.application)))
  }

  def editIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) map { flow =>
      Ok(editIpAllowlistView(request.application, flow, AddAnotherCidrBlockConfirmForm.form.fill(AddAnotherCidrBlockConfirmForm())))
    }
  }

  def editIpAllowlistAction(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    def handleValidForm(form: AddAnotherCidrBlockConfirmForm): Future[Result] = {
      form.confirm match {
        case Some("Yes") => successful(Redirect(routes.IpAllowListController.addCidrBlock(applicationId)))
        case _           => successful(Redirect(routes.IpAllowListController.reviewIpAllowlist(applicationId)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddAnotherCidrBlockConfirmForm]): Future[Result] = {
      ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) map { flow =>
        BadRequest(editIpAllowlistView(request.application, flow, formWithErrors))
      }
    }

    AddAnotherCidrBlockConfirmForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def addCidrBlock(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) map { flow =>
      Ok(addCidrBlockView(request.application, flow, AddCidrBlockForm.form.fill(AddCidrBlockForm(""))))
    }
  }

  def addCidrBlockAction(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    def handleValidForm(form: AddCidrBlockForm): Future[Result] = {
      ipAllowlistService.addCidrBlock(form.ipAddress, request.application, request.sessionId) map { _ =>
        Redirect(routes.IpAllowListController.editIpAllowlist(applicationId))
      }
    }

    def handleInvalidForm(formWithErrors: Form[AddCidrBlockForm]): Future[Result] = {
      ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) map { flow =>
        BadRequest(addCidrBlockView(request.application, flow, formWithErrors))
      }
    }

    AddCidrBlockForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def removeCidrBlock(applicationId: ApplicationId, cidrBlock: String): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    successful(Ok(removeCidrBlockView(request.application, cidrBlock)))
  }

  def removeCidrBlockAction(applicationId: ApplicationId, cidrBlock: String): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.removeCidrBlock(cidrBlock, request.sessionId) map { updatedFlow =>
      if (updatedFlow.allowlist.isEmpty) {
        Redirect(routes.IpAllowListController.settingUpAllowlist(applicationId))
      } else {
        Redirect(routes.IpAllowListController.editIpAllowlist(applicationId))
      }
    }
  }

  def reviewIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) map { flow =>
      Ok(reviewIpAllowlistView(request.application, flow))
    }
  }

  def activateIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.getIpAllowlistFlow(request.application, request.sessionId) flatMap { flow =>
      ipAllowlistService.activateIpAllowlist(request.application, request.sessionId) map { _ =>
        Ok(changeIpAllowlistSuccessView(request.application, flow))
      }
    } recover {
      case _: ForbiddenException => Forbidden(errorHandler.forbiddenTemplate)
    }
  }

  def removeIpAllowlist(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    successful(Ok(removeIpAllowlistView(request.application)))
  }

  def removeIpAllowlistAction(applicationId: ApplicationId): Action[AnyContent] = canEditIpAllowlistAction(applicationId) { implicit request =>
    ipAllowlistService.deactivateIpAllowlist(request.application, request.sessionId) map { _ =>
      Ok(removeIpAllowlistSuccessView(request.application))
    } recover {
      case _: ForbiddenException => Forbidden(errorHandler.forbiddenTemplate)
    }
  }
}
