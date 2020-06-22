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
import domain.Capabilities.SupportsTermsOfUse
import domain.Permissions.SandboxOrAdmin
import domain.{Application, CheckInformation, TermsOfUseAgreement, TermsOfUseStatus}
import javax.inject.{Inject, Singleton}
import model.ApplicationViewModel
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, Result}
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.time.DateTimeUtils
import views.html.partials

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TermsOfUse @Inject()(val errorHandler: ErrorHandler,
                           val sessionService: SessionService,
                           val applicationService: ApplicationService,
                           val messagesApi: MessagesApi,
                           val cookieSigner : CookieSigner
                           )
                          (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController() with ApplicationHelper {

  def canChangeTermsOfUseAction(applicationId: String)
                                (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    permissionThenCapabilityAction2(SandboxOrAdmin,SupportsTermsOfUse)(applicationId)(fun)


  def termsOfUsePartial() = Action {
    Ok(partials.termsOfUse())
  }

  def termsOfUse(id: String) = canChangeTermsOfUseAction(id) { implicit request =>
    if (request.application.termsOfUseStatus == TermsOfUseStatus.NOT_APPLICABLE) {
      Future.successful(BadRequest(errorHandler.badRequestTemplate))
    } else {
      Future.successful(Ok(views.html.termsOfUse(applicationViewModelFromApplicationRequest, TermsOfUseForm.form)))
    }
  }

  def agreeTermsOfUse(id: String) = canChangeTermsOfUseAction(id) { implicit request =>

    def handleValidForm(app: Application, form: TermsOfUseForm) = {
      if (app.termsOfUseStatus == TermsOfUseStatus.AGREEMENT_REQUIRED) {
        val information = app.checkInformation.getOrElse(CheckInformation())
        val updatedInformation = information.copy(
          termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(
            request.user.email, DateTimeUtils.now, appConfig.currentTermsOfUseVersion))

        applicationService.updateCheckInformation(app, updatedInformation)
          .map(_ => Redirect(routes.Details.details(app.id)))
      } else {
        Future.successful(BadRequest(errorHandler.badRequestTemplate))
      }
    }

    def handleInvalidForm(applicationViewModel: ApplicationViewModel, form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(views.html.termsOfUse(applicationViewModel, form)))
    }

    TermsOfUseForm.form.bindFromRequest.fold(
      invalidForm => handleInvalidForm(applicationViewModelFromApplicationRequest, invalidForm),
      validForm => handleValidForm(request.application, validForm))
  }
}
