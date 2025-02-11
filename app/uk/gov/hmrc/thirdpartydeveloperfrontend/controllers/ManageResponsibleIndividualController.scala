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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.time.Instant
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import com.google.inject.{Inject, Singleton}
import views.html.manageResponsibleIndividual._

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel, formatDateTime}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsResponsibleIndividual
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

object ManageResponsibleIndividualController {
  case class ResponsibleIndividualHistoryItem(name: String, fromDate: String, toDate: String)

  case class ViewModel(
      environment: String,
      responsibleIndividualName: String,
      history: List[ResponsibleIndividualHistoryItem],
      allowChanges: Boolean,
      adminEmails: List[String],
      userIsResponsibleIndividual: Boolean
    )

  def formatDateTime(dateTime: Instant) = DateFormatter.formatDate(dateTime)
}

@Singleton
class ManageResponsibleIndividualController @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    responsibleIndividualDetailsView: ResponsibleIndividualDetailsView,
    responsibleIndividualChangeToSelfOrOtherView: ResponsibleIndividualChangeToSelfOrOtherView,
    responsibleIndividualChangeToSelfView: ResponsibleIndividualChangeToSelfView,
    responsibleIndividualChangeToSelfConfirmedView: ResponsibleIndividualChangeToSelfConfirmedView,
    responsibleIndividualChangeToOtherView: ResponsibleIndividualChangeToOtherView,
    responsibleIndividualChangeToOtherRequestedView: ResponsibleIndividualChangeToOtherRequestedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with ApplicationHelper {

  private val flashKeyNewRiName = "newRiName"

  private def canViewResponsibleIndividualDetailsAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsResponsibleIndividual, TeamMembersOnly)(applicationId)(fun)

  private def canUpdateResponsibleIndividualDetailsAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(SupportsResponsibleIndividual, AdministratorOnly)(applicationId)(fun)

  private def buildResponsibleIndividualHistoryItems(termsOfUseAcceptances: List[TermsOfUseAcceptance]): List[ResponsibleIndividualHistoryItem] = {
    termsOfUseAcceptances match {
      case Nil                       => List.empty
      case first :: Nil              => List(ResponsibleIndividualHistoryItem(first.responsibleIndividual.fullName.value, formatDateTime(first.dateTime), "Present"))
      case first :: second :: others =>
        List(ResponsibleIndividualHistoryItem(first.responsibleIndividual.fullName.value, formatDateTime(first.dateTime), formatDateTime(second.dateTime))) ++
          buildResponsibleIndividualHistoryItems(second :: others)
    }
  }

  def showResponsibleIndividualDetails(applicationId: ApplicationId): Action[AnyContent] = canViewResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    request.application.access match {
      case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, responsibleIndividual, _, _, _, termsOfUseAcceptances))) => {
        val environment                       = request.application.deployedTo.toString().toLowerCase().capitalize
        val responsibleIndividualHistoryItems = buildResponsibleIndividualHistoryItems(termsOfUseAcceptances).reverse
        val allowChanges                      = request.role.isAdministrator
        val adminEmails                       = request.application.collaborators.filter(_.isAdministrator).map(_.emailAddress.text).toList
        val userIsResponsibleIndividual       = request.userSession.developer.email == responsibleIndividual.emailAddress
        val viewModel                         = ViewModel(environment, responsibleIndividual.fullName.value, responsibleIndividualHistoryItems, allowChanges, adminEmails, userIsResponsibleIndividual)
        successful(Ok(responsibleIndividualDetailsView(request.application, viewModel)))
      }
      case _                                                                                                                          => successful(BadRequest("Only Standard apps have Responsible Individual details"))
    }
  }

  def showResponsibleIndividualChangeToSelfOrOther(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    successful(Ok(responsibleIndividualChangeToSelfOrOtherView(request.application, ResponsibleIndividualChangeToSelfOrOtherForm.form())))
  }

  def responsibleIndividualChangeToSelfOrOtherAction(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    def handleInvalidForm(formWithErrors: Form[ResponsibleIndividualChangeToSelfOrOtherForm]) = {
      successful(BadRequest(responsibleIndividualChangeToSelfOrOtherView(request.application, formWithErrors)))
    }

    def handleValidForm(form: ResponsibleIndividualChangeToSelfOrOtherForm): Future[Result] = {
      successful(Redirect(form.who match {
        case ResponsibleIndividualChangeToSelfOrOtherForm.self  => routes.ManageResponsibleIndividualController.showResponsibleIndividualChangeToSelf(applicationId)
        case ResponsibleIndividualChangeToSelfOrOtherForm.other => routes.ManageResponsibleIndividualController.showResponsibleIndividualChangeToOther(applicationId)
        case _                                                  => throw new AssertionError(s"Unexpected 'who' value in form '${form.who}', should have been caught by form validation")
      }))
    }

    ResponsibleIndividualChangeToSelfOrOtherForm.form().bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def showResponsibleIndividualChangeToSelf(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    successful(Ok(responsibleIndividualChangeToSelfView(request.application)))
  }

  def responsibleIndividualChangeToSelfAction(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    applicationService.updateResponsibleIndividual(request.application, request.userId, request.userSession.developer.displayedName, request.userSession.developer.email)
      .map(_ => Redirect(routes.ManageResponsibleIndividualController.showResponsibleIndividualChangeToSelfConfirmed(applicationId)))
  }

  def showResponsibleIndividualChangeToSelfConfirmed(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    successful(Ok(responsibleIndividualChangeToSelfConfirmedView(request.application)))
  }

  def showResponsibleIndividualChangeToOther(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    successful(Ok(responsibleIndividualChangeToOtherView(request.application, ResponsibleIndividualChangeToOtherForm.form())))
  }

  def responsibleIndividualChangeToOtherAction(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    def handleInvalidForm(formWithErrors: Form[ResponsibleIndividualChangeToOtherForm]) = {
      successful(BadRequest(responsibleIndividualChangeToOtherView(request.application, formWithErrors)))
    }

    def handleValidForm(form: ResponsibleIndividualChangeToOtherForm): Future[Result] = {
      request.application.access match {
        case Access.Standard(_, _, _, _, _, _, Some(ImportantSubmissionData(_, responsibleIndividual, _, _, _, _))) => {
          val isAlreadyResponsibleIndividual = form.name.equalsIgnoreCase(responsibleIndividual.fullName.value) &&
            form.email.equalsIgnoreCase(responsibleIndividual.emailAddress.text)
          if (isAlreadyResponsibleIndividual) {
            successful(BadRequest(responsibleIndividualChangeToOtherView(
              request.application,
              ResponsibleIndividualChangeToOtherForm.form().fill(form).withGlobalError("responsible_individual.error.nochange")
            )))
          } else {
            applicationService.verifyResponsibleIndividual(request.application, request.userId, request.userSession.developer.displayedName, form.name, form.email.toLaxEmail)
              .map(_ =>
                Redirect(routes.ManageResponsibleIndividualController.showResponsibleIndividualChangeToOtherRequested(applicationId)).flashing(flashKeyNewRiName -> form.name)
              )
          }
        }
      }
    }

    ResponsibleIndividualChangeToOtherForm.form().bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def showResponsibleIndividualChangeToOtherRequested(applicationId: ApplicationId) = canUpdateResponsibleIndividualDetailsAction(applicationId) { implicit request =>
    successful(Ok(responsibleIndividualChangeToOtherRequestedView(request.application, request.flash.get(flashKeyNewRiName))))
  }

}
