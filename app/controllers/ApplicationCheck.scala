/*
 * Copyright 2019 HM Revenue & Customs
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
import controllers.FormKeys._
import domain._
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.i18n.Messages.Implicits._
import play.api.mvc.{AnyContent, Result}
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.{applicationcheck, editapplication}

import scala.concurrent.Future

@Singleton
class ApplicationCheck @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 implicit val appConfig: ApplicationConfig)
  extends ApplicationController() with ApplicationHelper {

  def withAppInTestingState(appId: String)(f: Application => Future[Result])(implicit request: RequestWithAttributes[AnyContent]) = {
    applicationForRequest(appId) flatMap { app =>
      if (app.state.name == ApplicationState.testing.name) {
        f(app)
      } else {
        Future.successful(Redirect(controllers.routes.ManageApplications.editApplication(appId, None)))
      }
    }
  }

  def requestCheckPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val application = request.application

    Future.successful(Ok(applicationcheck.landingPage(application,
      ApplicationInformationForm.form.fill(CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))))))
  }

  def requestCheckAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application
    val requestForm = ApplicationInformationForm.form.fillAndValidate(CheckInformationForm.fromCheckInformation(app.checkInformation.getOrElse(CheckInformation())))

    def withFormErrors(form: Form[CheckInformationForm]): Future[Result] = {
      Future.successful(BadRequest(applicationcheck.landingPage(app, form)))
    }

    def withValidForm(form: CheckInformationForm): Future[Result] = {
      val future = for {
        _ <- applicationService.requestUplift(appId, app.name, request.user)
      } yield Ok(editapplication.nameSubmitted(appId, app))

      future recover {
        case e: DeskproTicketCreationFailed => InternalServerError(applicationcheck.landingPage(app, requestForm.withError("submitError", e.displayMessage)))
        case _: ApplicationAlreadyExists =>
          val information = app.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)

          applicationService.updateCheckInformation(app.id, information)
          Conflict(applicationcheck.landingPage(app.copy(checkInformation = Some(information)), requestForm.withError("confirmedName", applicationNameAlreadyExistsKey)))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def contactPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application

    val contactForm = for {
      approvalInfo <- app.checkInformation
      contactDetails <- approvalInfo.contactDetails
    } yield ContactForm(contactDetails.fullname, contactDetails.email, contactDetails.telephoneNumber)


    Future.successful(contactForm match {
      case Some(form) => Ok(applicationcheck.contactDetails(app, ContactForm.form.fill(ContactForm(form.fullname, form.email, form.telephone))))
      case _ => Ok(applicationcheck.contactDetails(app, ContactForm.form))
    })
  }

  def contactAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val requestForm = ContactForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[ContactForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.contactDetails(app, form)))
    }

    def withValidForm(form: ContactForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      applicationService.updateCheckInformation(app.id, information.copy(contactDetails = Some(ContactDetails(form.fullname, form.email, form.telephone)))) map { _ =>
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def namePage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.confirmName(request.application, NameForm.form.fill(NameForm(request.application.name)))))
  }

  def nameAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val requestForm = NameForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[NameForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.confirmName(app, form)))
    }

    def updateNameIfChanged(form: NameForm) = {
      if (app.name != form.applicationName) {
        applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, form.applicationName, app.description, app.access))
      } else {
        Future.successful(())
      }
    }

    def withValidForm(form: NameForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateNameIfChanged(form)
        _ <- applicationService.updateCheckInformation(app.id, information.copy(confirmedName = true))
      } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def detailsPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application

    val detailsForm = for {
      approvalInfo <- app.checkInformation
      applicationDetails <- approvalInfo.applicationDetails
    } yield DetailsForm.form.fill(DetailsForm(applicationDetails))

    Future.successful(detailsForm match {
      case Some(form) => Ok(applicationcheck.applicationDetails(app, form))
      case _ => Ok(applicationcheck.applicationDetails(app, DetailsForm.form))
    })
  }

  def detailsAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val requestForm = DetailsForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[DetailsForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.applicationDetails(app, form)))
    }

    def withValidForm(form: DetailsForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- applicationService.updateCheckInformation(app.id, information.copy(applicationDetails = Some(form.applicationDetails)))
      } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def apiSubscriptionsPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application

    apiSubscriptionsHelper.fetchAllSubscriptions(app, request.user)(hc).flatMap {
      case Some(subsData) => Future.successful(Ok(applicationcheck.apiSubscriptions(app, subsData.role, subsData.subscriptions, appId, subsData.hasSubscriptions)))
      case None => Future.successful(NotFound(errorHandler.notFoundTemplate))
    }
  }

  def apiSubscriptionsAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application
    val information = app.checkInformation.getOrElse(CheckInformation())

    for {
      _ <- applicationService.updateCheckInformation(app.id, information.copy(apiSubscriptionsConfirmed = true))
    } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
  }

  def privacyPolicyPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = PrivacyPolicyForm(
          hasUrl(std.privacyPolicyUrl, app.checkInformation.map(_.providedPrivacyPolicyURL)),
          std.privacyPolicyUrl)
        Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form.fill(form)))
      case _ => Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form))
    })
  }

  def privacyPolicyAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val requestForm = PrivacyPolicyForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[PrivacyPolicyForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.privacyPolicy(app, form)))
    }

    def updateUrl(form: PrivacyPolicyForm) = {
      val access = app.access match {
        case s: Standard => s.copy(privacyPolicyUrl = form.privacyPolicyURL, overrides = Set.empty)
        case other => other
      }

      applicationService.update(UpdateApplicationRequest(app.id, app.deployedTo, app.name, app.description, access))
    }

    def withValidForm(form: PrivacyPolicyForm) = {
      val information = app.checkInformation.getOrElse(CheckInformation())
      for {
        _ <- updateUrl(form)
        _ <- applicationService.updateCheckInformation(app.id, information.copy(providedPrivacyPolicyURL = true))
      } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsAndConditionsPage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = TermsAndConditionsForm(
          hasUrl(std.termsAndConditionsUrl, app.checkInformation.map(_.providedTermsAndConditionsURL)),
          std.termsAndConditionsUrl)
        Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form.fill(form)))
      case _ => Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form))
    })
  }

  def termsAndConditionsAction(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val requestForm = TermsAndConditionsForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[TermsAndConditionsForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsAndConditions(app, form)))
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
        _ <- applicationService.updateCheckInformation(app.id, information.copy(providedTermsAndConditionsURL = true))
      } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsOfUsePage(appId: String) = adminOnTestingApp(appId) { implicit request =>
    val app = request.application
    val checkInformation = app.checkInformation.getOrElse(CheckInformation())
    val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)

    Future.successful(Ok(applicationcheck.termsOfUse(app, TermsOfUseForm.form.fill(termsOfUseForm))))
  }

  def termsOfUseAction(appId: String) = adminOnTestingApp(appId) { implicit request =>

    val version = appConfig.currentTermsOfUseVersion
    val app = request.application

    val requestForm = TermsOfUseForm.form.bindFromRequest

    def withFormErrors(form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsOfUse(app, form)))
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
      } yield Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  private def hasUrl(url: Option[String], hasCheckedUrl: Option[Boolean]) = {
    (url, hasCheckedUrl) match {
      case (Some(_), _) => Some("true")
      case (None, Some(true)) => Some("false")
      case _ => None
    }
  }
}

object ApplicationInformationForm {
  def form: Form[CheckInformationForm] = Form(
    mapping(
      "confirmedNameCompleted" -> boolean.verifying("confirm.name.required.field", cn => cn),
      "applicationDetailsCompleted" -> boolean.verifying("application.details.required.field", ad => ad),
      "apiSubscriptionsCompleted" -> boolean.verifying("api.subscriptions.required.field", subsConfirmed => subsConfirmed),
      "contactDetailsCompleted" -> boolean.verifying("contact.details.required.field", cd => cd),
      "providedPolicyURLCompleted" -> boolean.verifying("privacy.links.required.field", provided => provided),
      "providedTermsAndConditionsURLCompleted" -> boolean.verifying("tnc.links.required.field", provided => provided),
      "termsOfUseAgreementsCompleted" -> boolean.verifying("agree.terms.of.use.required.field", terms => terms)
    )(CheckInformationForm.apply)(CheckInformationForm.unapply)
  )
}

case class TermsAndConditionsForm(urlPresent: Option[String], termsAndConditionsURL: Option[String])

object TermsAndConditionsForm {
  def form: Form[TermsAndConditionsForm] = Form(
    mapping(
      "hasUrl" -> optional(text).verifying(tNcUrlNoChoiceKey, s => s.isDefined),
      "termsAndConditionsURL" -> mandatoryIfTrue(
        "hasUrl",
        text.verifying(tNcUrlInvalidKey, s => s.isEmpty || isValidUrl(s)).verifying(tNcUrlRequiredKey, _.nonEmpty)
      )
    )(TermsAndConditionsForm.apply)(TermsAndConditionsForm.unapply)
  )
}

case class PrivacyPolicyForm(urlPresent: Option[String], privacyPolicyURL: Option[String])

object PrivacyPolicyForm {
  def form: Form[PrivacyPolicyForm] = Form(
    mapping(
      "hasUrl" -> optional(text).verifying(privacyPolicyUrlNoChoiceKey, s => s.isDefined),
      "privacyPolicyURL" -> mandatoryIfTrue(
        "hasUrl",
        text.verifying(privacyPolicyUrlInvalidKey, s => s.isEmpty || isValidUrl(s)).verifying(privacyPolicyUrlRequiredKey, _.nonEmpty)
      )
    )(PrivacyPolicyForm.apply)(PrivacyPolicyForm.unapply)
  )
}

case class NameForm(applicationName: String)

object NameForm {
  def form: Form[NameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(NameForm.apply)(NameForm.unapply)
  )
}

case class DetailsForm(applicationDetails: String)

object DetailsForm {
  def form: Form[DetailsForm] = Form(
    mapping(
      "applicationDetails" -> textValidator(detailsRequiredKey, detailsMaxLengthKey, 3000)
    )(DetailsForm.apply)(DetailsForm.unapply)
  )
}

case class TermsOfUseForm(termsOfUseAgreed: Boolean)

object TermsOfUseForm {
  def form: Form[TermsOfUseForm] = Form(
    mapping(
      "termsOfUseAgreed" -> boolean.verifying(termsOfUseAgreeKey, b => b)
    )(TermsOfUseForm.apply)(TermsOfUseForm.unapply)
  )

  def fromCheckInformation(checkInformation: CheckInformation) = {
    TermsOfUseForm(checkInformation.termsOfUseAgreements.nonEmpty)
  }
}


case class ContactForm(fullname: String, email: String, telephone: String)

object ContactForm {
  def form: Form[ContactForm] = Form(
    mapping(
      "fullname" -> fullnameValidator,
      "email" -> emailValidator(),
      "telephone" -> telephoneValidator
    )(ContactForm.apply)(ContactForm.unapply)
  )
}
