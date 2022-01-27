/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsTermsOfUse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOrAdmin
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, SessionService, ApplicationActionService}
import uk.gov.hmrc.time.DateTimeUtils
import views.html.{TermsOfUseView, partials}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TermsOfUse @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    termsOfUseView: TermsOfUseView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc)
    with ApplicationHelper {

  def canChangeTermsOfUseAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsTermsOfUse, SandboxOrAdmin)(applicationId)(fun)

  def termsOfUsePartial() = Action {
    Ok(partials.termsOfUse())
  }

  def termsOfUse(id: ApplicationId) = canChangeTermsOfUseAction(id) { implicit request =>
    if (request.application.termsOfUseStatus == TermsOfUseStatus.NOT_APPLICABLE) {
      Future.successful(BadRequest(errorHandler.badRequestTemplate))
    } else {
      Future.successful(Ok(termsOfUseView(applicationViewModelFromApplicationRequest, TermsOfUseForm.form)))
    }
  }

  def agreeTermsOfUse(id: ApplicationId) = canChangeTermsOfUseAction(id) { implicit request =>
    def handleValidForm(app: Application, form: TermsOfUseForm) = {
      if (app.termsOfUseStatus == TermsOfUseStatus.AGREEMENT_REQUIRED) {
        val information = app.checkInformation.getOrElse(CheckInformation())
        val updatedInformation = information.copy(
          termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(request.developerSession.email, DateTimeUtils.now, appConfig.currentTermsOfUseVersion)
        )

        applicationService
          .updateCheckInformation(app, updatedInformation)
          .map(_ => Redirect(routes.Details.details(app.id)))
      } else {
        Future.successful(BadRequest(errorHandler.badRequestTemplate))
      }
    }

    def handleInvalidForm(applicationViewModel: ApplicationViewModel, form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(termsOfUseView(applicationViewModel, form)))
    }

    TermsOfUseForm.form.bindFromRequest
      .fold(invalidForm => handleInvalidForm(applicationViewModelFromApplicationRequest, invalidForm), validForm => handleValidForm(request.application, validForm))
  }
}