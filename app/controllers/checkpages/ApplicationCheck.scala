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

import config.{ApplicationConfig, ErrorHandler}
import controllers.FormKeys._
import controllers._
import domain.{ApplicationRequest => _, _}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Call, Result}
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.{applicationcheck, editapplication}

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationCheck @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 val messagesApi: MessagesApi
                                 )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController()
    with ApplicationHelper
    with CanUseCheckActions
    with ConfirmNamePartialController
    with ContactDetailsPartialController
    with ApiSubscriptionsPartialController {

  def requestCheckPage(appId: String) = canUseChecksAction(appId) { implicit request =>
    val application = request.application

    Future.successful(Ok(applicationcheck.landingPage(application,
      ApplicationInformationForm.form.fill(CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))))))
  }

  def requestCheckAction(appId: String) = canUseChecksAction(appId) { implicit request =>

    def withFormErrors(app: Application)(form: Form[CheckInformationForm]): Future[Result] = {
      Future.successful(BadRequest(applicationcheck.landingPage(app, form)))
    }

    def withValidForm(app: Application)(form: CheckInformationForm): Future[Result] = {
      Future.successful(Redirect(routes.CheckYourAnswers.answersPage(appId)))
    }

    val app = request.application
    val requestForm = ApplicationInformationForm.form.fillAndValidate(CheckInformationForm.fromCheckInformation(app.checkInformation.getOrElse(CheckInformation())))

    requestForm.fold(withFormErrors(app), withValidForm(app))
  }

  def credentialsRequested(appId: String) = whenTeamMemberOnApp(appId) { implicit request =>
    Future.successful(Ok(editapplication.nameSubmitted(appId, request.application)))
  }

  def privacyPolicyPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = PrivacyPolicyForm(
          hasUrl(std.privacyPolicyUrl, app.checkInformation.map(_.providedPrivacyPolicyURL)),
          std.privacyPolicyUrl)
        Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form.fill(form), mode))
      case _ => Ok(applicationcheck.privacyPolicy(app, PrivacyPolicyForm.form, mode))
    })
  }

  def privacyPolicyAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = PrivacyPolicyForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[PrivacyPolicyForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.privacyPolicy(app, form, mode)))
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
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsAndConditionsPage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application

    Future.successful(app.access match {
      case std: Standard =>
        val form = TermsAndConditionsForm(
          hasUrl(std.termsAndConditionsUrl, app.checkInformation.map(_.providedTermsAndConditionsURL)),
          std.termsAndConditionsUrl)
        Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form.fill(form), mode))
      case _ => Ok(applicationcheck.termsAndConditions(app, TermsAndConditionsForm.form, mode))
    })
  }

  def termsAndConditionsAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val requestForm = TermsAndConditionsForm.form.bindFromRequest
    val app = request.application

    def withFormErrors(form: Form[TermsAndConditionsForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsAndConditions(app, form, mode)))
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
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def termsOfUsePage(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val checkInformation = app.checkInformation.getOrElse(CheckInformation())
    val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)

    Future.successful(Ok(applicationcheck.termsOfUse(app, TermsOfUseForm.form.fill(termsOfUseForm), mode)))
  }

  def termsOfUseAction(appId: String, mode: CheckYourAnswersPageMode) = canUseChecksAction(appId) { implicit request =>

    val version = appConfig.currentTermsOfUseVersion
    val app = request.application

    val requestForm = TermsOfUseForm.form.bindFromRequest

    def withFormErrors(form: Form[TermsOfUseForm]) = {
      Future.successful(BadRequest(views.html.applicationcheck.termsOfUse(app, form, mode)))
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
      } yield if (mode == CheckYourAnswersPageMode.CheckYourAnswers){
        Redirect(routes.CheckYourAnswers.answersPage(app.id))
      } else {
        Redirect(routes.ApplicationCheck.requestCheckPage(app.id))
      }
    }

    requestForm.fold(withFormErrors, withValidForm)
  }

  def team(appId: String) = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.team(request.application, request.role, request.user)))
  }

  def teamAction(appId: String) = canUseChecksAction(appId) { implicit request =>

    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(appId, information.copy(teamConfirmed = true))
    } yield Redirect(routes.ApplicationCheck.requestCheckPage(appId))
  }

  def teamAddMember(appId: String) = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.teamMemberAdd(request.application, AddTeamMemberForm.form, request.user)))
  }

  def teamMemberRemoveConfirmation(appId: String, teamMemberHash:  String) = canUseChecksAction(appId) { implicit request =>
    successful(request.application.findCollaboratorByHash(teamMemberHash)
      .map(collaborator => Ok(applicationcheck.team.teamMemberRemoveConfirmation(request.application, request.user, collaborator.emailAddress)))
      .getOrElse(Redirect(routes.ApplicationCheck.team(appId))))
  }

  def teamMemberRemoveAction(appId: String) = canUseChecksAction(appId) { implicit request => {

    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm) : Future[Result] = {
        applicationService
          .removeTeamMember(request.application, form.email, request.user.email)
          .map(_ => Redirect(routes.ApplicationCheck.team(appId)))
      }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]) : Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
    }
  }

  private def hasUrl(url: Option[String], hasCheckedUrl: Option[Boolean]) = {
    (url, hasCheckedUrl) match {
      case (Some(_), _) => Some("true")
      case (None, Some(true)) => Some("false")
      case _ => None
    }
  }

  protected def landingPageRoute(appId: String) = routes.ApplicationCheck.requestCheckPage(appId)
  protected def nameActionRoute(appId: String) = routes.ApplicationCheck.nameAction(appId)
  protected def contactActionRoute(appId: String) = routes.ApplicationCheck.contactAction(appId)
  protected def apiSubscriptionsActionRoute(appId: String): Call = routes.ApplicationCheck.apiSubscriptionsAction(appId)

}

object ApplicationInformationForm {
  def form: Form[CheckInformationForm] = Form(
    mapping(
      "confirmedNameCompleted" -> boolean.verifying("confirm.name.required.field", cn => cn),
      "apiSubscriptionsCompleted" -> boolean.verifying("api.subscriptions.required.field", subsConfirmed => subsConfirmed),
      "contactDetailsCompleted" -> boolean.verifying("contact.details.required.field", cd => cd),
      "providedPolicyURLCompleted" -> boolean.verifying("privacy.links.required.field", provided => provided),
      "providedTermsAndConditionsURLCompleted" -> boolean.verifying("tnc.links.required.field", provided => provided),
      "teamConfirmedCompleted" -> boolean.verifying("team.required.field", provided => provided),
      "termsOfUseAgreementsCompleted" -> boolean.verifying("agree.terms.of.use.required.field", terms => terms)
    )(CheckInformationForm.apply)(CheckInformationForm.unapply)
  )
}

case class TermsAndConditionsForm(urlPresent: Option[String], termsAndConditionsURL: Option[String])

object TermsAndConditionsForm {
  import controllers._

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
  import controllers._

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
  import controllers._

  def form: Form[NameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(NameForm.apply)(NameForm.unapply)
  )
}

case class DetailsForm(applicationDetails: String)

object DetailsForm {
  import controllers._

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
  import controllers._
  def form: Form[ContactForm] = Form(
    mapping(
      "fullname" -> fullnameValidator,
      "email" -> emailValidator(),
      "telephone" -> telephoneValidator
    )(ContactForm.apply)(ContactForm.unapply)
  )
}

case class DummySubscriptionsForm(hasNonExampleSubscription: Boolean)

object DummySubscriptionsForm {
  def form: Form[DummySubscriptionsForm] = Form(
    mapping(
      "hasNonExampleSubscription" -> boolean
    )(DummySubscriptionsForm.apply)(DummySubscriptionsForm.unapply)
      .verifying("error.must.subscribe", x => x.hasNonExampleSubscription)
  )
}
