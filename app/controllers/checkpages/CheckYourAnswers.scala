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
import controllers.{
  AddTeamMemberForm,
  ApiSubscriptionsHelper,
  ApplicationController,
  ApplicationHelper,
  ApplicationRequest,
  RemoveTeamMemberCheckPageConfirmationForm
}
import controllers.FormKeys.applicationNameAlreadyExistsKey
import domain.{Application, ApplicationAlreadyExists, CheckInformation, CheckInformationForm, ContactDetails, DeskproTicketCreationFailed}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Call, Result}
import service.{ApplicationService, SessionService}
import views.html.checkpages.checkyouranswers
import views.html.checkpages.applicationcheck

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourAnswers @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val applicationCheck: ApplicationCheck,
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
    with ApiSubscriptionsPartialController
    with PrivacyPolicyPartialController
    with TermsAndConditionsPartialController
    with TermsOfUsePartialController
    {

  private def populateCheckYourAnswersData(application: Application, subs: Seq[String]): CheckYourAnswersData = {
    val contactDetails: Option[ContactDetails] = application.checkInformation.flatMap(_.contactDetails)

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
      subscriptions = subs
    )
  }

  private def populateCheckYourAnswersData(application: Application)(implicit request: ApplicationRequest[AnyContent]): Future[CheckYourAnswersData] = {
    applicationService.fetchAllSubscriptions(application).map(_.map(_.name)).map(subscriptions => {
      populateCheckYourAnswersData(application, subscriptions)
    })
  }

  def answersPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    for {
      application <- fetchApp(appId)
      checkYourAnswersData <- populateCheckYourAnswersData(application)
    } yield Ok(checkyouranswers.checkYourAnswers(checkYourAnswersData, CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))))
  }

  def answersPageAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val application = request.application

    (for {
      _ <- applicationService.requestUplift(appId, application.name, request.user)
    } yield Redirect(routes.ApplicationCheck.credentialsRequested(appId)))
    .recoverWith {
      case e: DeskproTicketCreationFailed =>
        for {
          checkYourAnswersData <- populateCheckYourAnswersData(application)
          requestForm = CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))
        } yield InternalServerError(checkyouranswers.checkYourAnswers(checkYourAnswersData, requestForm.withError("submitError", e.displayMessage)))
    }
    .recover {
      case _: ApplicationAlreadyExists =>
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)
        applicationService.updateCheckInformation(application.id, information)
        val requestForm = ApplicationInformationForm.form.fillAndValidate(CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation())))
        Conflict(applicationcheck.landingPage(application.copy(checkInformation = Some(information)), requestForm.withError("confirmedName", applicationNameAlreadyExistsKey))) // TODO where
    }
  }

  def team(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(checkyouranswers.team.team(request.application, request.role, request.user)))
  }

  def teamAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    val information = request.application.checkInformation.getOrElse(CheckInformation())
    for {
      _ <- applicationService.updateCheckInformation(appId, information.copy(teamConfirmed = true))
    } yield Redirect(routes.CheckYourAnswers.answersPage(appId))
  }

  def teamAddMember(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    Future.successful(Ok(checkyouranswers.team.teamMemberAdd(request.application, AddTeamMemberForm.form, request.user)))
  }

  def teamMemberRemoveConfirmation(appId: String, teamMemberHash:  String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    successful(request.application.findCollaboratorByHash(teamMemberHash)
      .map(collaborator => Ok(checkyouranswers.team.teamMemberRemoveConfirmation(request.application, request.user, collaborator.emailAddress)))
      .getOrElse(Redirect(routes.CheckYourAnswers.team(appId))))
  }

  def teamMemberRemoveAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>

    def handleValidForm(form: RemoveTeamMemberCheckPageConfirmationForm) : Future[Result] = {
      applicationService
        .removeTeamMember(request.application, form.email, request.user.email)
        .map(_ => Redirect(routes.CheckYourAnswers.team(appId)))
    }

    def handleInvalidForm(form: Form[RemoveTeamMemberCheckPageConfirmationForm]) : Future[Result] = {
      successful(BadRequest)
    }

    RemoveTeamMemberCheckPageConfirmationForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  protected def nameActionRoute(appId: String): Call =routes.CheckYourAnswers.nameAction(appId)
  protected def contactActionRoute(appId: String): Call =routes.CheckYourAnswers.contactAction(appId)
  protected def landingPageRoute(appId: String): Call = routes.CheckYourAnswers.answersPage(appId)
  protected def apiSubscriptionsActionRoute(appId: String): Call = routes.CheckYourAnswers.apiSubscriptionsAction(appId)
  protected def privacyPolicyActionRoute(appId: String): Call = routes.CheckYourAnswers.privacyPolicyAction(appId)
  protected def termsAndConditionsActionRoute(appId: String): Call = routes.CheckYourAnswers.termsAndConditionsAction(appId)
  protected def termsOfUseActionRoute(appId: String): Call = routes.CheckYourAnswers.termsOfUseAction(appId)
}

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
                                 subscriptions: Seq[String]
                               )

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
