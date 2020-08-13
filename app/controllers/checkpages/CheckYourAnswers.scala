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
import controllers.FormKeys.applicationNameAlreadyExistsKey
import controllers.ManageSubscriptions.FieldValue
import controllers._
import domain.{ApplicationAlreadyExists, DeskproTicketCreationFailed}
import domain.models.apidefinitions._
import domain.models.controllers._
import domain.models.subscriptions._
import domain.models.applications._
import domain.models.apidefinitions.APISubscriptionStatus
import javax.inject.{Inject, Singleton}
import model.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import service.{ApplicationService, SessionService}
import views.html.checkpages.{ApiSubscriptionsView, ConfirmNameView, ContactDetailsView, PrivacyPolicyView, TermsAndConditionsView, TermsOfUseView}
import views.html.checkpages.applicationcheck.LandingPageView
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView}
import views.html.checkpages.checkyouranswers.CheckYourAnswersView
import views.html.checkpages.checkyouranswers.team.TeamView

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourAnswers @Inject()(val applicationService: ApplicationService,
                                 val applicationCheck: ApplicationCheck,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 mcc: MessagesControllerComponents,
                                 val cookieSigner : CookieSigner,
                                 checkYourAnswersView: CheckYourAnswersView,
                                 landingPageView: LandingPageView,
                                 teamView: TeamView,
                                 teamMemberAddView: TeamMemberAddView,
                                 teamMemberRemoveConfirmationView: TeamMemberRemoveConfirmationView,
                                 val termsOfUseView: TermsOfUseView,
                                 val confirmNameView: ConfirmNameView,
                                 val termsAndConditionsView: TermsAndConditionsView,
                                 val privacyPolicyView: PrivacyPolicyView,
                                 val apiSubscriptionsViewTemplate: ApiSubscriptionsView,
                                 val contactDetailsView: ContactDetailsView
                                )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
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

  def answersPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val checkYourAnswersData = CheckYourAnswersData(request.application, request.subscriptions)
    Future.successful(
      Ok(
        checkYourAnswersView(checkYourAnswersData, CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy")))
      )
    )
  }

  def answersPageAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val application = request.application

    (for {
      _ <- applicationService.requestUplift(appId, application.name, request.user)
    } yield Redirect(routes.ApplicationCheck.credentialsRequested(appId)))
      .recover {
        case e: DeskproTicketCreationFailed =>
          val checkYourAnswersData = CheckYourAnswersData(request.application, request.subscriptions)
          val requestForm = CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))
          InternalServerError(checkYourAnswersView(checkYourAnswersData, requestForm.withError("submitError", e.displayMessage)))
      }
      .recover {
        case _: ApplicationAlreadyExists =>
          val information = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)
          applicationService.updateCheckInformation(application, information)

          val requestForm = validateCheckFormForApplication(request)

          val applicationViewModel = ApplicationViewModel(application.copy(checkInformation = Some(information)), hasSubscriptionFields(request))
          Conflict(landingPageView(applicationViewModel, requestForm.withError("confirmedName", applicationNameAlreadyExistsKey)))
      }
  }

  def team(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamView(request.application, request.role, request.user)))
  }

  def teamAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(request.application, information.copy(teamConfirmed = true))
    } yield Redirect(routes.CheckYourAnswers.answersPage(appId))
  }

  def teamAddMember(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamMemberAddView(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.user)))
  }

  def teamMemberRemoveConfirmation(appId: String, teamMemberHash: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    successful(request.application.findCollaboratorByHash(teamMemberHash)
      .map(collaborator => Ok(teamMemberRemoveConfirmationView(request.application, request.user, collaborator.emailAddress)))
      .getOrElse(Redirect(routes.CheckYourAnswers.team(appId))))
  }

  def teamMemberRemoveAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm): Future[Result] = {
      applicationService
        .removeTeamMember(request.application, form.email, request.user.email)
        .map(_ => Redirect(routes.CheckYourAnswers.team(appId)))
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]): Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  protected def landingPageRoute(appId: String): Call = routes.CheckYourAnswers.answersPage(appId)

  protected def nameActionRoute(appId: String): Call = routes.CheckYourAnswers.nameAction(appId)

  protected def contactActionRoute(appId: String): Call = routes.CheckYourAnswers.contactAction(appId)

  protected def apiSubscriptionsActionRoute(appId: String): Call = routes.CheckYourAnswers.apiSubscriptionsAction(appId)

  protected def privacyPolicyActionRoute(appId: String): Call = routes.CheckYourAnswers.privacyPolicyAction(appId)

  protected def termsAndConditionsActionRoute(appId: String): Call = routes.CheckYourAnswers.termsAndConditionsAction(appId)

  protected def termsOfUseActionRoute(appId: String): Call = routes.CheckYourAnswers.termsOfUseAction(appId)

  protected def submitButtonLabel = "Continue"
}

case class CheckYourSubscriptionData(
  name: String,
  apiContext: ApiContext,
  apiVersion: String,
  displayedStatus:String,
  fields: Seq[FieldValue]
)

case class CheckYourAnswersData(
                                 appId: String,
                                 softwareName: String,
                                 fullName: Option[String],
                                 email: Option[String],
                                 telephoneNumber: Option[String],
                                 teamMembers: Set[String],
                                 privacyPolicyUrl: Option[String],
                                 termsAndConditionsUrl: Option[String],
                                 acceptedTermsOfUse: Boolean,
                                 subscriptions: Seq[CheckYourSubscriptionData]
                               )

object CheckYourAnswersData {
  def apply(application: Application, subs: Seq[APISubscriptionStatus]): CheckYourAnswersData = {
    val contactDetails: Option[ContactDetails] = application.checkInformation.flatMap(_.contactDetails)

    def asCheckYourSubscriptionData(in: APISubscriptionStatus): CheckYourSubscriptionData = {
      CheckYourSubscriptionData(
        name = in.name,
        apiContext = in.context,
        apiVersion = in.apiVersion.version,
        displayedStatus = in.apiVersion.displayedStatus,
        fields = in.fields.fields.map(ManageSubscriptions.toFieldValue)
      )
    }

    CheckYourAnswersData(
      appId = application.id,
      softwareName = application.name,

      fullName = contactDetails.map(_.fullname),
      email = contactDetails.map(_.email),
      telephoneNumber = contactDetails.map(_.telephoneNumber),

      teamMembers = application.collaborators.map(_.emailAddress),

      privacyPolicyUrl = application.privacyPolicyUrl,
      termsAndConditionsUrl = application.termsAndConditionsUrl,
      acceptedTermsOfUse = application.checkInformation.fold(false)(_.termsOfUseAgreements.nonEmpty),
      subscriptions = subs.filter(_.subscribed).map(asCheckYourSubscriptionData)
    )
  }
}

case class DummyCheckYourAnswersForm(dummy: String = "dummy")

object CheckYourAnswersForm {
  import play.api.data.Forms.{ignored, mapping}

  def form: Form[DummyCheckYourAnswersForm] = {
    Form(mapping(
      "dummy" -> ignored("dummy")
    )
    (DummyCheckYourAnswersForm.apply)
    (DummyCheckYourAnswersForm.unapply)
    )
  }
}
