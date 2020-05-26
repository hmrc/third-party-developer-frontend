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
import play.api.data.{Form, Forms, Mapping}
import play.api.data.Forms.{boolean, ignored, mapping, optional, text}
import play.api.i18n.MessagesApi
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, Call, Result}
import service.{ApplicationService, SessionService}
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.editapplication
import views.html.checkpages.applicationcheck

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import model.ApplicationViewModel

@Singleton
class ApplicationCheck @Inject()(val applicationService: ApplicationService,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 val messagesApi: MessagesApi,
                                 val cookieSigner : CookieSigner
                                 )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController
    with ApplicationHelper
    with CanUseCheckActions
    with ConfirmNamePartialController
    with ContactDetailsPartialController
    with ApiSubscriptionsPartialController
    with PrivacyPolicyPartialController
    with TermsAndConditionsPartialController
    with TermsOfUsePartialController
    with CheckInformationFormHelper
    {

  def requestCheckPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val form = createCheckFormForApplication(request)
    Future.successful(Ok(applicationcheck.landingPage(applicationViewModelFromApplicationRequest(),form)))
  }

  def unauthorisedAppDetails(appId: String): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    val application = request.application

    if(request.role.isAdministrator) {
      Future.successful(Redirect(routes.ApplicationCheck.requestCheckPage(appId)))
    } else {
      Future.successful(Ok(applicationcheck.unauthorisedAppDetails(application.name, application.adminEmails)))
    }
  }

  def requestCheckAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request: ApplicationRequest[AnyContent] =>
    def withFormErrors(form: Form[CheckInformationForm]): Future[Result] = {
      Future.successful(BadRequest(applicationcheck.landingPage(applicationViewModelFromApplicationRequest(), form)))
    }

    def withValidForm(form: CheckInformationForm): Future[Result] = {
      Future.successful(Redirect(routes.CheckYourAnswers.answersPage(appId)))
    }

    val requestForm = validateCheckFormForApplication(request)

    requestForm.fold(withFormErrors, withValidForm)
  }

  def credentialsRequested(appId: String): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    Future.successful(Ok(editapplication.nameSubmitted(appId, request.application)))
  }

  def team(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.team(request.application, request.role, request.user)))
  }

  def teamAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(appId, information.copy(teamConfirmed = true))
    } yield Redirect(routes.ApplicationCheck.requestCheckPage(appId))
  }

  def teamAddMember(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(applicationcheck.team.teamMemberAdd(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.user)))
  }

  def teamMemberRemoveConfirmation(appId: String, teamMemberHash:  String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    successful(request.application.findCollaboratorByHash(teamMemberHash)
      .map(collaborator => Ok(applicationcheck.team.teamMemberRemoveConfirmation(request.application, request.user, collaborator.emailAddress)))
      .getOrElse(Redirect(routes.ApplicationCheck.team(appId))))
  }

  def teamMemberRemoveAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

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

  protected def landingPageRoute(appId: String): Call = routes.ApplicationCheck.requestCheckPage(appId)
  protected def nameActionRoute(appId: String): Call = routes.ApplicationCheck.nameAction(appId)
  protected def contactActionRoute(appId: String): Call = routes.ApplicationCheck.contactAction(appId)
  protected def apiSubscriptionsActionRoute(appId: String): Call = routes.ApplicationCheck.apiSubscriptionsAction(appId)
  protected def privacyPolicyActionRoute(appId: String): Call = routes.ApplicationCheck.privacyPolicyAction(appId)
  protected def termsAndConditionsActionRoute(appId: String): Call = routes.ApplicationCheck.termsAndConditionsAction(appId)
  protected def termsOfUseActionRoute(appId: String): Call = routes.ApplicationCheck.termsOfUseAction(appId)
  protected def submitButtonLabel = "Save and return"
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
