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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.CheckAnswersController.ProdCredsRequestReceivedViewModel
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.SubmissionActionBuilders.SubmissionStatusFilter
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.models.AnswersViewModel._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.{RequestProductionCredentials, SubmissionService}
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

object CheckAnswersController {
  case class ProdCredsRequestReceivedViewModel(appId: ApplicationId, requesterIsResponsibleIndividual: Boolean, isNewTermsOfUseUplift: Boolean, isGranted: Boolean)
}

@Singleton
class CheckAnswersController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val submissionService: SubmissionService,
    requestProductionCredentials: RequestProductionCredentials,
    checkAnswersView: CheckAnswersView,
    prodCredsRequestReceivedView: ProductionCredentialsRequestReceivedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with EitherTHelper[String]
    with SubmissionActionBuilders {

  import SubmissionActionBuilders.{ApplicationStateFilter, RoleFilter}

  val redirectToGetProdCreds = (applicationId: ApplicationId) => Redirect(routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))

  /*, Read/Write and State details */
  def checkAnswersPage(productionAppId: ApplicationId) = withApplicationAndSubmissionInSpecifiedState(
    ApplicationStateFilter.inTestingOrProduction,
    RoleFilter.isAdminRole,
    SubmissionStatusFilter.answeredCompletely
  )(redirectToGetProdCreds(productionAppId))(productionAppId) { implicit request =>
    submissionService.fetchLatestExtendedSubmission(productionAppId).map(_ match {
      case Some(extSubmission) => {
        val viewModel                   = convertSubmissionToViewModel(extSubmission)(request.application.id, request.application.name)
        val maybePreviousInstance       = extSubmission.submission.instances.tail.headOption // previous instance, if there was one
        val previousInstanceWasDeclined = maybePreviousInstance.exists(_.isDeclined)
        Ok(checkAnswersView(viewModel, previousInstanceWasDeclined, request.application.state.name.isProduction, request.msgRequest.flash.get("error")))
      }
      case None                => BadRequestWithErrorMessage("No submission and/or application found")
    })
  }

  def checkAnswersAction(productionAppId: ApplicationId) = withApplicationAndSubmissionInSpecifiedState(
    ApplicationStateFilter.inTestingOrProduction,
    RoleFilter.isAdminRole,
    SubmissionStatusFilter.answeredCompletely
  )(redirectToGetProdCreds(productionAppId))(productionAppId) { implicit request =>
    val requesterIsResponsibleIndividual = isRequesterResponsibleIndividual(request.submission)
    val isNewTouUplift                   = request.submission.context.getOrElse(AskWhen.Context.Keys.NEW_TERMS_OF_USE_UPLIFT, "No") == "Yes"
    requestProductionCredentials
      .requestProductionCredentials(request.application, request.userSession, requesterIsResponsibleIndividual, isNewTouUplift)
      .map(_ match {
        case Right(app)                 => {
          Redirect(routes.CheckAnswersController.requestReceivedPage(productionAppId))
        }
        case Left(ErrorDetails(_, msg)) => Redirect(routes.CheckAnswersController.checkAnswersPage(productionAppId)).flashing("error" -> msg)
      })
  }

  private def isRequesterResponsibleIndividual(submission: Submission) = {
    val responsibleIndividualIsRequesterId = submission.questionIdsOfInterest.responsibleIndividualIsRequesterId
    submission.latestInstance.answersToQuestions.get(responsibleIndividualIsRequesterId) match {
      case Some(ActualAnswer.SingleChoiceAnswer(answer)) => answer == "Yes"
      case _                                             => false
    }
  }

  def requestReceivedPage(
      productionAppId: ApplicationId
    ) = withApplicationSubmission(ApplicationStateFilter.pendingApprovalOrProduction, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    val requesterIsResponsibleIndividual = isRequesterResponsibleIndividual(request.submission)
    val isNewTouUplift                   = request.submission.context.getOrElse(AskWhen.Context.Keys.NEW_TERMS_OF_USE_UPLIFT, "No") == "Yes"
    val isGranted                        = request.submission.status.isGranted
    val viewModel                        = ProdCredsRequestReceivedViewModel(productionAppId, requesterIsResponsibleIndividual, isNewTouUplift, isGranted)
    successful(Ok(prodCredsRequestReceivedView(viewModel)))
  }
}
