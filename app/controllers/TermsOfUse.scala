/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ApplicationGlobal
import domain.{Application, CheckInformation, TermsOfUseAgreement, TermsOfUseStatus}
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.mvc.Action
import service.{ApplicationServiceImpl, SessionService}
import uk.gov.hmrc.time.DateTimeUtils
import views.html.partials

import scala.concurrent.Future

trait TermsOfUse extends ApplicationController with ApplicationHelper {

  def termsOfUsePartial() = Action {
    Ok(partials.termsOfUse())
  }

  def termsOfUse(id: String) = adminIfStandardProductionApp(id) { implicit request =>
    if (request.application.termsOfUseStatus == TermsOfUseStatus.NOT_APPLICABLE) {
      Future.successful(BadRequest(ApplicationGlobal.badRequestTemplate))
    } else {
      Future.successful(Ok(views.html.termsOfUse(request.application, TermsOfUseForm.form)))
    }
  }

  def agreeTermsOfUse(id: String) = adminIfStandardProductionApp(id) { implicit request =>

    def handleValidForm(app: Application, form: TermsOfUseForm) = {
      if (app.termsOfUseStatus == TermsOfUseStatus.AGREEMENT_REQUIRED) {
        val information = app.checkInformation.getOrElse(CheckInformation())
        val updatedInformation = information.copy(termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(request.user.email, DateTimeUtils.now, appConfig.currentTermsOfUseVersion))

        applicationService.updateCheckInformation(app.id, updatedInformation)
          .map(_ => Redirect(routes.Details.details(app.id)))
      } else {
        Future.successful(BadRequest(ApplicationGlobal.badRequestTemplate))
      }
    }

    def handleInvalidForm(app: Application, form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(views.html.termsOfUse(app, form)))
    }

    TermsOfUseForm.form.bindFromRequest.fold(
      invalidForm => handleInvalidForm(request.application, invalidForm),
      validForm => handleValidForm(request.application, validForm))
  }
}

object TermsOfUse extends TermsOfUse with WithAppConfig {
  override val sessionService = SessionService
  override val applicationService = ApplicationServiceImpl
}
