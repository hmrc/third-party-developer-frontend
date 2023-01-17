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

import views.html.checkpages.ContactDetailsView

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, CheckInformation, ContactDetails}

trait ContactDetailsPartialController {
  self: ApplicationController with CanUseCheckActions =>

  val contactDetailsView: ContactDetailsView

  def contactPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    val contactForm = for {
      approvalInfo   <- app.checkInformation
      contactDetails <- approvalInfo.contactDetails
    } yield ContactForm(contactDetails.fullname, contactDetails.email, contactDetails.telephoneNumber)

    Future.successful(contactForm match {
      case Some(form) =>
        val filledContactForm = ContactForm.form.fill(ContactForm(form.fullname, form.email, form.telephone))
        Ok(contactDetailsView(app, filledContactForm, contactActionRoute(app.id)))
      case _          =>
        Ok(contactDetailsView(app, ContactForm.form, contactActionRoute(app.id)))
    })
  }

  def contactAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val requestForm = ContactForm.form.bindFromRequest
    val app         = request.application

    def withFormErrors(form: Form[ContactForm]) = {
      Future.successful(BadRequest(contactDetailsView(app, form, contactActionRoute(app.id))))
    }

    def withValidForm(form: ContactForm) = {
      val information = app.checkInformation
        .getOrElse(CheckInformation())
        .copy(contactDetails = Some(ContactDetails(form.fullname, form.email, form.telephone)))
      applicationService.updateCheckInformation(app, information) map { _ => Redirect(landingPageRoute(app.id)) }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  protected def contactActionRoute(appId: ApplicationId): Call
  protected def landingPageRoute(appId: ApplicationId): Call

}
