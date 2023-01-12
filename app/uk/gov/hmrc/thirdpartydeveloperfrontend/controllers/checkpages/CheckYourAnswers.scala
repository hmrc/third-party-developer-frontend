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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.applicationNameAlreadyExistsKey
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageSubscriptions.Field
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.{ApplicationAlreadyExists, DeskproTicketCreationFailed}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APISubscriptionStatus, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService, TermsOfUseVersionService}
import views.html.checkpages._
import views.html.checkpages.applicationcheck.LandingPageView
import views.html.checkpages.applicationcheck.team.{TeamMemberAddView, TeamMemberRemoveConfirmationView}
import views.html.checkpages.checkyouranswers.CheckYourAnswersView
import views.html.checkpages.checkyouranswers.team.TeamView

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessLevel

import java.time.Clock

@Singleton
class CheckYourAnswers @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val applicationCheck: ApplicationCheck,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
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
    val contactDetailsView: ContactDetailsView,
    val termsOfUseVersionService: TermsOfUseVersionService,
    val clock: Clock
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with ApplicationHelper
    with CanUseCheckActions
    with ConfirmNamePartialController
    with ContactDetailsPartialController
    with ApiSubscriptionsPartialController
    with PrivacyPolicyPartialController
    with TermsAndConditionsPartialController
    with TermsOfUsePartialController
    with CheckInformationFormHelper {

  def answersPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val accessLevel = DevhubAccessLevel.fromRole(request.role)

    val checkYourAnswersData = CheckYourAnswersData(accessLevel, request.application, request.subscriptions)
    Future.successful(
      Ok(
        checkYourAnswersView(checkYourAnswersData, CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy")))
      )
    )
  }

  def answersPageAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val application = request.application
    val accessLevel = DevhubAccessLevel.fromRole(request.role)

    (for {
      _ <- applicationService.requestUplift(appId, application.name, request.developerSession)
    } yield Redirect(routes.ApplicationCheck.credentialsRequested(appId)))
      .recover {
        case e: DeskproTicketCreationFailed =>
          val checkYourAnswersData = CheckYourAnswersData(accessLevel, request.application, request.subscriptions)
          val requestForm          = CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))
          InternalServerError(checkYourAnswersView(checkYourAnswersData, requestForm.withError("submitError", e.displayMessage)))
      }
      .recover {
        case _: ApplicationAlreadyExists =>
          val information = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)
          applicationService.updateCheckInformation(application, information)

          val requestForm = validateCheckFormForApplication(request)

          val applicationViewModel = ApplicationViewModel(application.copy(checkInformation = Some(information)), request.hasSubscriptionFields, hasPpnsFields(request))
          Conflict(landingPageView(applicationViewModel, requestForm.withError("confirmedName", applicationNameAlreadyExistsKey)))
      }
  }

  def team(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamView(request.application, request.role, request.developerSession)))
  }

  def teamAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(request.application, information.copy(teamConfirmed = true))
    } yield Redirect(routes.CheckYourAnswers.answersPage(appId))
  }

  def teamAddMember(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(teamMemberAddView(applicationViewModelFromApplicationRequest, AddTeamMemberForm.form, request.developerSession)))
  }

  def teamMemberRemoveConfirmation(appId: ApplicationId, teamMemberHash: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    successful(
      request.application
        .findCollaboratorByHash(teamMemberHash)
        .map(collaborator => Ok(teamMemberRemoveConfirmationView(request.application, request.developerSession, collaborator.emailAddress)))
        .getOrElse(Redirect(routes.CheckYourAnswers.team(appId)))
    )
  }

  def teamMemberRemoveAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm): Future[Result] = {
      applicationService
        .removeTeamMember(request.application, form.email, request.developerSession.email)
        .map(_ => Redirect(routes.CheckYourAnswers.team(appId)))
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]): Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  protected def landingPageRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.answersPage(appId)

  protected def nameActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.nameAction(appId)

  protected def contactActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.contactAction(appId)

  protected def apiSubscriptionsActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.apiSubscriptionsAction(appId)

  protected def privacyPolicyActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.privacyPolicyAction(appId)

  protected def termsAndConditionsActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.termsAndConditionsAction(appId)

  protected def termsOfUseActionRoute(appId: ApplicationId): Call = routes.CheckYourAnswers.termsOfUseAction(appId)

  protected def submitButtonLabel = "Continue"
}

case class CheckYourSubscriptionData(
    name: String,
    apiContext: ApiContext,
    apiVersion: ApiVersion,
    displayedStatus: String,
    fields: Seq[Field]
  )

case class CheckYourAnswersData(
    appId: ApplicationId,
    softwareName: String,
    fullName: Option[String],
    email: Option[String],
    telephoneNumber: Option[String],
    teamMembers: Set[String],
    privacyPolicyLocation: PrivacyPolicyLocation,
    termsAndConditionsLocation: TermsAndConditionsLocation,
    acceptedTermsOfUse: Boolean,
    subscriptions: Seq[CheckYourSubscriptionData]
  )

object CheckYourAnswersData {

  def apply(accessLevel: DevhubAccessLevel, application: Application, subs: Seq[APISubscriptionStatus]): CheckYourAnswersData = {
    val contactDetails: Option[ContactDetails] = application.checkInformation.flatMap(_.contactDetails)

    def asCheckYourSubscriptionData(accessLevel: DevhubAccessLevel)(in: APISubscriptionStatus): CheckYourSubscriptionData = {
      CheckYourSubscriptionData(
        name = in.name,
        apiContext = in.context,
        apiVersion = in.apiVersion.version,
        displayedStatus = in.apiVersion.displayedStatus,
        fields = in.fields.fields.map(ManageSubscriptions.toFieldValue(accessLevel))
      )
    }

    CheckYourAnswersData(
      appId = application.id,
      softwareName = application.name,
      fullName = contactDetails.map(_.fullname),
      email = contactDetails.map(_.email),
      telephoneNumber = contactDetails.map(_.telephoneNumber),
      teamMembers = application.collaborators.map(_.emailAddress),
      privacyPolicyLocation = application.privacyPolicyLocation,
      termsAndConditionsLocation = application.termsAndConditionsLocation,
      acceptedTermsOfUse = application.checkInformation.fold(false)(_.termsOfUseAgreements.nonEmpty),
      subscriptions = subs.filter(_.subscribed).map(asCheckYourSubscriptionData(accessLevel))
    )
  }
}

case class DummyCheckYourAnswersForm(dummy: String = "dummy")

object CheckYourAnswersForm {
  import play.api.data.Forms.{ignored, mapping}

  def form: Form[DummyCheckYourAnswersForm] = {
    Form(
      mapping(
        "dummy" -> ignored("dummy")
      )(DummyCheckYourAnswersForm.apply)(DummyCheckYourAnswersForm.unapply)
    )
  }
}
