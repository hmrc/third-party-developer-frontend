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

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.CheckInformation
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.HasUrl._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateResult

trait PrivacyPolicyPartialController extends WithUnsafeDefaultFormBinding {
  self: ApplicationController with CanUseCheckActions =>

  val privacyPolicyView: PrivacyPolicyView

  def privacyPolicyPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Access.Standard =>
        val form = PrivacyPolicyForm(hasUrl(std.privacyPolicyUrl, app.checkInformation.map(_.providedPrivacyPolicyURL)), std.privacyPolicyUrl)
        Ok(privacyPolicyView(app, PrivacyPolicyForm.form.fill(form), privacyPolicyActionRoute(app.id)))
      case _                    => Ok(privacyPolicyView(app, PrivacyPolicyForm.form, privacyPolicyActionRoute(app.id)))
    })
  }

  def privacyPolicyAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val requestForm = PrivacyPolicyForm.form.bindFromRequest()
    val app         = request.application

    def withFormErrors(form: Form[PrivacyPolicyForm]) = {
      Future.successful(BadRequest(privacyPolicyView(app, form, privacyPolicyActionRoute(app.id))))
    }

    def updateUrl(form: PrivacyPolicyForm) = {
      val existingPrivacyPolicyUrl = app.access match {
        case s: Access.Standard => Some(s.privacyPolicyUrl)
        case other              => None
      }

      lazy val actor = Actors.AppCollaborator(request.userRequest.developerSession.email)

      val cmd = (form.privacyPolicyURL, existingPrivacyPolicyUrl) match {
        case (None, None) => None
        case (Some(a), Some(b)) if(a == b) => None
        case (None, _) => Some(ApplicationCommands.RemoveSandboxApplicationPrivacyPolicyUrl(actor, instant()))
        case (Some(newValue), _) => Some(ApplicationCommands.ChangeSandboxApplicationPrivacyPolicyUrl(actor, instant(), newValue))
      }

      cmd.fold[Future[ApplicationUpdateResult]](Future.successful(ApplicationUpdateSuccessful))(c => applicationService.dispatchWithThrow(app.id, c))
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
