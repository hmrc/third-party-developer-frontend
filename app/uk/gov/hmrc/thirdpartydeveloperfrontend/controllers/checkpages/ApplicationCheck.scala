/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, CheckInformation}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.CheckInformationForm
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, SessionService}
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.checkpages._
import views.html.checkpages.applicationcheck.{LandingPageView, UnauthorisedAppDetailsView}
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView, TeamView}
import views.html.editapplication.NameSubmittedView

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationActionService

@Singleton
class ApplicationCheck @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    landingPageView: LandingPageView,
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView,
    nameSubmittedView: NameSubmittedView,
    teamView: TeamView,
    teamMemberAddView: TeamMemberAddView,
    teamMemberRemoveConfirmationView: TeamMemberRemoveConfirmationView,
    val termsOfUseView: TermsOfUseView,
    val confirmNameView: ConfirmNameView,
    val contactDetailsView: ContactDetailsView,
    val apiSubscriptionsViewTemplate: ApiSubscriptionsView,
    val privacyPolicyView: PrivacyPolicyView,
    val termsAndConditionsView: TermsAndConditionsView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc)
    with ApplicationHelper
    with CanUseCheckActions
    with ConfirmNamePartialController
    with ContactDetailsPartialController
    with ApiSubscriptionsPartialController
    with PrivacyPolicyPartialController
    with TermsAndConditionsPartialController
    with TermsOfUsePartialController
    with CheckInformationFormHelper {

  def requestCheckPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val form = createCheckFormForApplication(request)
    Future.successful(Ok(landingPageView(applicationViewModelFromApplicationRequest(), form)))
  }

  def unauthorisedAppDetails(appId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    val application = request.application

    if (request.role.isAdministrator) {
      Future.successful(Redirect(routes.ApplicationCheck.requestCheckPage(appId)))
    } else {
      Future.successful(Ok(unauthorisedAppDetailsView(application.name, application.adminEmails)))
    }
  }

  def requestCheckAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request: ApplicationRequest[AnyContent] =>
    def withFormErrors(form: Form[CheckInformationForm]): Future[Result] = {
      Future.successful(BadRequest(landingPageView(applicationViewModelFromApplicationRequest(), form)))
    }

    def withValidForm(form: CheckInformationForm): Future[Result] = {
      Future.successful(Redirect(routes.CheckYourAnswers.answersPage(appId)))
    }

    val requestForm = validateCheckFormForApplication(request)

    requestForm.fold(withFormErrors, withValidForm)
  }

  def credentialsRequested(appId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    Future.successful(Ok(nameSubmittedView(appId, request.application)))
  }

  def team(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamView(request.application, request.role, request.developerSession)))
  }

  def teamAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(request.application, information.copy(teamConfirmed = true))
    } yield Redirect(routes.ApplicationCheck.requestCheckPage(appId))
  }

  def teamAddMember(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamMemberAddView(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.developerSession)))
  }

  def teamMemberRemoveConfirmation(appId: ApplicationId, teamMemberHash: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    successful(
      request.application
        .findCollaboratorByHash(teamMemberHash)
        .map(collaborator => Ok(teamMemberRemoveConfirmationView(request.application, request.developerSession, collaborator.emailAddress)))
        .getOrElse(Redirect(routes.ApplicationCheck.team(appId)))
    )
  }

  def teamMemberRemoveAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm): Future[Result] = {
      applicationService
        .removeTeamMember(request.application, form.email, request.developerSession.email)
        .map(_ => Redirect(routes.ApplicationCheck.team(appId)))
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]): Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  protected def landingPageRoute(appId: ApplicationId): Call = routes.ApplicationCheck.requestCheckPage(appId)
  protected def nameActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.nameAction(appId)
  protected def contactActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.contactAction(appId)
  protected def apiSubscriptionsActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.apiSubscriptionsAction(appId)
  protected def privacyPolicyActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.privacyPolicyAction(appId)
  protected def termsAndConditionsActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.termsAndConditionsAction(appId)
  protected def termsOfUseActionRoute(appId: ApplicationId): Call = routes.ApplicationCheck.termsOfUseAction(appId)
  protected def submitButtonLabel = "Save and return"
}

case class TermsAndConditionsForm(urlPresent: Option[String], termsAndConditionsURL: Option[String])

object TermsAndConditionsForm {
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

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
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

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
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

  def form: Form[NameForm] = Form(
    mapping(
      "applicationName" -> applicationNameValidator
    )(NameForm.apply)(NameForm.unapply)
  )
}

case class DetailsForm(applicationDetails: String)

object DetailsForm {
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._

  def form: Form[DetailsForm] = Form(
    mapping(
      "applicationDetails" -> textValidator(detailsRequiredKey, detailsMaxLengthKey, 3000)
    )(DetailsForm.apply)(DetailsForm.unapply)
  )
}

case class ContactForm(fullname: String, email: String, telephone: String)

object ContactForm {
  import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
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

  def form2: Form[DummySubscriptionsForm] = Form(
    mapping(
      "hasNonExampleSubscription" -> boolean
    )(DummySubscriptionsForm.apply)(DummySubscriptionsForm.unapply)
      .verifying("error.turnoffapis.requires.at.least.one", x => x.hasNonExampleSubscription)
  )
}
