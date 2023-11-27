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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages

import java.time.Clock
import scala.concurrent.Future

import views.html.checkpages.TermsOfUseView

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{CheckInformation, TermsOfUseAgreement}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationController, ApplicationRequest, TermsOfUseForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.TermsOfUseVersionService

trait TermsOfUsePartialController {
  self: ApplicationController with CanUseCheckActions with ClockNow =>
  val clock: Clock
  val termsOfUseView: TermsOfUseView
  val termsOfUseVersionService: TermsOfUseVersionService

  private def createTermsOfUse(applicationViewModel: ApplicationViewModel, form: Form[TermsOfUseForm])(implicit request: ApplicationRequest[AnyContent]) = {
    termsOfUseView(
      applicationViewModel,
      form,
      submitButtonLabel = submitButtonLabel,
      submitAction = termsOfUseActionRoute(applicationViewModel.application.id),
      landingPageRoute = landingPageRoute(applicationViewModel.application.id),
      termsOfUseVersionService.getForApplication(request.application)
    )
  }

  def termsOfUsePage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app              = request.application
    val checkInformation = app.checkInformation.getOrElse(CheckInformation())
    val termsOfUseForm   = TermsOfUseForm.fromCheckInformation(checkInformation)

    Future.successful(Ok(createTermsOfUse(applicationViewModelFromApplicationRequest(), TermsOfUseForm.form.fill(termsOfUseForm))))
  }

  def termsOfUseAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val version = termsOfUseVersionService.getLatest().toString
    val app     = request.application

    val requestForm = TermsOfUseForm.form.bindFromRequest()

    def withFormErrors(form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(createTermsOfUse(applicationViewModelFromApplicationRequest(), form)))
    }

    def withValidForm(form: TermsOfUseForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())

      val updatedInformation = if (information.termsOfUseAgreements.exists(terms => terms.version == version)) {
        information
      } else {
        information.copy(termsOfUseAgreements = information.termsOfUseAgreements :+ TermsOfUseAgreement(request.developerSession.email, now(), version))
      }

      for {
        _ <- applicationService.updateCheckInformation(app, updatedInformation)
      } yield Redirect(landingPageRoute(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def landingPageRoute(appId: ApplicationId): Call
  protected def termsOfUseActionRoute(appId: ApplicationId): Call
  protected def submitButtonLabel: String

}
