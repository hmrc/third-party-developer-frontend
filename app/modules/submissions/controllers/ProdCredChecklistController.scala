/*
 * Copyright 2021 HM Revenue & Customs
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

package modules.submissions.controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ApmConnector
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service.{ApplicationActionService, ApplicationService, SessionService}
import modules.submissions.views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import controllers.ApplicationController
import controllers.checkpages.CanUseCheckActions
import cats.data.NonEmptyList
import modules.submissions.domain.models._
import modules.submissions.services.SubmissionService
import helpers.EitherTHelper
import domain.models.controllers.BadRequestWithErrorMessage

object ProdCredsChecklistController {
  case class ViewQuestionnaireSummary(label: String, state: String, id: QuestionnaireId = QuestionnaireId.random)
  case class ViewGrouping(label: String, questionnaireSummaries: NonEmptyList[ViewQuestionnaireSummary])
  case class ViewModel(appName: String, groupings: NonEmptyList[ViewGrouping])

  def deriveState(extendedSubmission: ExtendedSubmission)(questionnaire: Questionnaire): String = {
    extendedSubmission.nextQuestions.get(questionnaire.id) match {
      case None => "Completed"
      case Some(qId) if qId == questionnaire.questions.head.question.id => "Not Started"
      case Some(qId) => "In Progress"
    }
  }

  def convertToSummary(extendedSubmission: ExtendedSubmission)(questionnaire: Questionnaire): ViewQuestionnaireSummary = {
    val state = deriveState(extendedSubmission)(questionnaire)
    ViewQuestionnaireSummary(questionnaire.label.value, state, questionnaire.id)
  }

  def convertToViewGrouping(extendedSubmission: ExtendedSubmission)(groupOfQuestionnaires: GroupOfQuestionnaires): ViewGrouping = {
    ViewGrouping(
      label = groupOfQuestionnaires.heading,
      questionnaireSummaries = groupOfQuestionnaires.links.map(convertToSummary(extendedSubmission))
    )
  }

  def convertSubmissionToViewModel(extendedSubmission: ExtendedSubmission)(appName: String): ViewModel = {
    val groupings = extendedSubmission.submission.groups.map(convertToViewGrouping(extendedSubmission))
    ViewModel(appName, groupings)
  }
}

@Singleton
class ProdCredsChecklistController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val apmConnector: ApmConnector,
    submissionService: SubmissionService,
    productionCredentialsChecklistView: ProductionCredentialsChecklistView)
    (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions
     with EitherTHelper[String] {

  import cats.implicits._
  import cats.instances.future.catsStdInstancesForFuture
  import ProdCredsChecklistController._

  def productionCredentialsChecklist(productionAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(productionAppId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)

    val success = (viewModel: ViewModel) => Ok(productionCredentialsChecklistView(viewModel))
    
    val res = for {
        extendedSubmission <- fromOptionF(submissionService.fetchLatestSubmission(productionAppId), "No subsmission and/or application found")
        viewModel           = convertSubmissionToViewModel(extendedSubmission)(request.application.name)
      } yield viewModel

    res.fold[Result](failed, success)
  }
}
