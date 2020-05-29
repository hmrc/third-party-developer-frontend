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

import controllers.ApplicationController
import controllers.checkpages.HasUrl._
import domain._
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}
import views.html.checkpages.termsAndConditions

import scala.concurrent.Future

trait TermsAndConditionsPartialController {
  self: ApplicationController with CanUseCheckActions =>

  def termsAndConditionsPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = TermsAndConditionsForm(
          hasUrl(std.termsAndConditionsUrl, app.checkInformation.map(_.providedTermsAndConditionsURL)),
          std.termsAndConditionsUrl)
        Ok(termsAndConditions(app, TermsAndConditionsForm.form.fill(form), termsAndConditionsActionRoute(app.id)))
      case _ => Ok(termsAndConditions(app, TermsAndConditionsForm.form, termsAndConditionsActionRoute(app.id)))
    })
  }

  def termsAndConditionsAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val requestForm = TermsAndConditionsForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[TermsAndConditionsForm]) = {
      Future.successful(BadRequest(termsAndConditions(app, form, termsAndConditionsActionRoute(app.id))))
    }

    def updateUrl(form: TermsAndConditionsForm) = {
      val access = app.access match {
        case s: Standard => s.copy(termsAndConditionsUrl = form.termsAndConditionsURL, overrides = Set.empty)
        case other => other
      }

      applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, app.name, app.description, access))
    }

    def withValidForm(form: TermsAndConditionsForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateUrl(form)
        _ <- applicationService.updateCheckInformation(app, information.copy(providedTermsAndConditionsURL = true))
      } yield Redirect(landingPageRoute(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def landingPageRoute(appId: String): Call
  protected def termsAndConditionsActionRoute(appId: String): Call

}
