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

import views.html.checkpages.ConfirmNameView

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call, Result}

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.appNameField
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

trait ConfirmNamePartialController {
  self: ApplicationController with CanUseCheckActions =>

  val confirmNameView: ConfirmNameView

  def namePage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(Ok(confirmNameView(app, NameForm.form.fill(NameForm(app.name)), nameActionRoute(appId))))
  }

  def nameAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val requestForm = NameForm.form.bindFromRequest
    val app         = request.application

    def withFormErrors(form: Form[NameForm]) = {
      Future.successful(BadRequest(confirmNameView(app, form, nameActionRoute(appId))))
    }

    def updateNameIfChanged(form: NameForm) = {
      if (app.name != form.applicationName) {
        applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, form.applicationName, app.description, app.access))
      } else {
        Future.successful(())
      }
    }

    def withValidForm(form: NameForm): Future[Result] = {
      applicationService
        .isApplicationNameValid(form.applicationName, app.deployedTo, Some(app.id))
        .flatMap({
          case Valid            =>
            val information = app.checkInformation.getOrElse(CheckInformation())
            for {
              _ <- updateNameIfChanged(form)
              _ <- applicationService.updateCheckInformation(app, information.copy(confirmedName = true))
            } yield Redirect(landingPageRoute(app.id))
          case invalid: Invalid =>
            def invalidNameCheckForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            Future.successful(BadRequest(confirmNameView(request.application, invalidNameCheckForm, nameActionRoute(appId))))
        })
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def nameActionRoute(appId: ApplicationId): Call
  protected def landingPageRoute(appId: ApplicationId): Call

}
