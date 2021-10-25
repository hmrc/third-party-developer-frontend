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
import modules.submissions.domain.models.NotApplicable

object ProdCredsChecklistController {
  case class ViewQuestionnaireSummary(label: String, state: String, id: QuestionnaireId = QuestionnaireId.random, nextQuestionUrl: Option[String] = None)
  case class ViewGrouping(label: String, questionnaireSummaries: NonEmptyList[ViewQuestionnaireSummary])
  case class ViewModel(appName: String, groupings: NonEmptyList[ViewGrouping])

  def convertToSummary(submission: Submission)(questionnaire: Questionnaire): ViewQuestionnaireSummary = {
    val progress = submission.questionnaireProgress.get(questionnaire.id).get
    val state = progress.state.toString
    val url = progress.nextQuestion.map(qid => modules.submissions.controllers.routes.QuestionsController.showQuestion(submission.id, qid).url)
    ViewQuestionnaireSummary(questionnaire.label.value, state, questionnaire.id, url)
  }

  def convertToViewGrouping(submission: Submission)(groupOfQuestionnaires: GroupOfQuestionnaires): ViewGrouping = {
    ViewGrouping(
      label = groupOfQuestionnaires.heading,
      questionnaireSummaries = groupOfQuestionnaires.links.map(convertToSummary(submission))
    )
  }

  def convertSubmissionToViewModel(submission: Submission)(appName: String): ViewModel = {
    val groupings = submission.groups.map(convertToViewGrouping(submission))
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

    val success = (viewModel: ViewModel) => {
      def filterNotApplicableQuestionnaireSummaries(questionnaireSummaries: NonEmptyList[ViewQuestionnaireSummary]): Option[NonEmptyList[ViewQuestionnaireSummary]] = {
        questionnaireSummaries.filterNot(_.state == NotApplicable.toString).toNel
      }

      def filterGroupingsForEmptyQuestionnaireSummaries(groupings: NonEmptyList[ViewGrouping]): Option[NonEmptyList[ViewGrouping]] = {
        groupings
          .filterNot(g => filterNotApplicableQuestionnaireSummaries(g.questionnaireSummaries).isEmpty)
          .map(g => g.copy(questionnaireSummaries = filterNotApplicableQuestionnaireSummaries(g.questionnaireSummaries).get)) // TODO: Is there a way to do both of these lines in one?
          .toNel
      }

      filterGroupingsForEmptyQuestionnaireSummaries(viewModel.groupings).fold(
        BadRequest("") // TODO: Make better or redirect to summary page
      )(vg =>
        Ok(productionCredentialsChecklistView(viewModel.copy(groupings = vg)))
      )
    }
    
    val vm = for {
      submission          <- fromOptionF(submissionService.fetchLatestSubmission(productionAppId), "No subsmission and/or application found")
      viewModel           = convertSubmissionToViewModel(submission)(request.application.name)
    } yield viewModel

    vm.fold[Result](failed, success)
  }
}
