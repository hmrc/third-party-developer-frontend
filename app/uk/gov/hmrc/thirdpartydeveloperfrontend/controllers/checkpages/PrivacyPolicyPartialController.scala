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

import scala.concurrent.Future

import views.html.checkpages.PrivacyPolicyView

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.HasUrl._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{CheckInformation, Standard, UpdateApplicationRequest}

trait PrivacyPolicyPartialController extends WithUnsafeDefaultFormBinding {
  self: ApplicationController with CanUseCheckActions =>

  val privacyPolicyView: PrivacyPolicyView

  def privacyPolicyPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = PrivacyPolicyForm(hasUrl(std.privacyPolicyUrl, app.checkInformation.map(_.providedPrivacyPolicyURL)), std.privacyPolicyUrl)
        Ok(privacyPolicyView(app, PrivacyPolicyForm.form.fill(form), privacyPolicyActionRoute(app.id)))
      case _             => Ok(privacyPolicyView(app, PrivacyPolicyForm.form, privacyPolicyActionRoute(app.id)))
    })
  }

  def privacyPolicyAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val requestForm = PrivacyPolicyForm.form.bindFromRequest
    val app         = request.application

    def withFormErrors(form: Form[PrivacyPolicyForm]) = {
      Future.successful(BadRequest(privacyPolicyView(app, form, privacyPolicyActionRoute(app.id))))
    }

    def updateUrl(form: PrivacyPolicyForm) = {
      val access = app.access match {
        case s: Standard => s.copy(privacyPolicyUrl = form.privacyPolicyURL, overrides = Set.empty)
        case other       => other
      }

      applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, app.name, app.description, access))
    }

    def withValidForm(form: PrivacyPolicyForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateUrl(form)
        _ <- applicationService.updateCheckInformation(app, information.copy(providedPrivacyPolicyURL = true))
      } yield Redirect(landingPageRoute(app.id))
    }
    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def landingPageRoute(appId: ApplicationId): Call
  protected def privacyPolicyActionRoute(appId: ApplicationId): Call

}
