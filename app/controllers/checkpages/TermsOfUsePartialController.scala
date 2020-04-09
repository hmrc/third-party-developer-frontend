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

package controllers.checkpages

import controllers.{ApplicationController, TermsOfUseForm}
import domain._
import model.ApplicationViewModel
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}
import uk.gov.hmrc.time.DateTimeUtils
import views.html.checkpages.termsOfUse

import scala.concurrent.Future

trait TermsOfUsePartialController {
  self: ApplicationController with CanUseCheckActions =>

  private def createTermsOfUse(applicationViewModel: ApplicationViewModel, form: Form[TermsOfUseForm], appId: String)(implicit request: controllers.ApplicationRequest[AnyContent]) = {
    termsOfUse(
      applicationViewModel,
      form,
      submitButtonLabel = submitButtonLabel,
      submitAction = termsOfUseActionRoute(applicationViewModel.application.id),
      landingPageRoute = landingPageRoute(applicationViewModel.application.id)
    )
  }

  def termsOfUsePage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val checkInformation = app.checkInformation.getOrElse(CheckInformation())
    val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)

    Future.successful(Ok(createTermsOfUse(applicationViewModelFromApplicationRequest, TermsOfUseForm.form.fill(termsOfUseForm),appId)))
  }

  def termsOfUseAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    val version = appConfig.currentTermsOfUseVersion
    val app = request.application

    val requestForm = TermsOfUseForm.form.bindFromRequest

    def withFormErrors(form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(createTermsOfUse(applicationViewModelFromApplicationRequest, form, app.id)))
    }

    def withValidForm(form: TermsOfUseForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())

      val updatedInformation = if (information.termsOfUseAgreements.exists(terms => terms.version == version)) {
        information
      }
      else {
        information.copy(termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(request.user.email, DateTimeUtils.now, version))
      }

      for {
        _ <- applicationService.updateCheckInformation(app.id, updatedInformation)
      } yield Redirect(landingPageRoute(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def landingPageRoute(appId: String): Call
  protected def termsOfUseActionRoute(appId: String): Call
  protected def submitButtonLabel: String

}
