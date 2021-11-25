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
  case class ViewModel(appId: ApplicationId, appName: String, groupings: NonEmptyList[ViewGrouping])

  def convertToSummary(extSubmission: ExtendedSubmission)(questionnaire: Questionnaire): ViewQuestionnaireSummary = {
    val progress = extSubmission.questionnaireProgress.get(questionnaire.id).get
    val state = QuestionnaireState.describe(progress.state)
    val url = progress.questionsToAsk.headOption.map(q => modules.submissions.controllers.routes.QuestionsController.showQuestion(extSubmission.submission.id, q).url)
    ViewQuestionnaireSummary(questionnaire.label.value, state, questionnaire.id, url)
  }

  def convertToViewGrouping(extSubmission: ExtendedSubmission)(groupOfQuestionnaires: GroupOfQuestionnaires): ViewGrouping = {
    ViewGrouping(
      label = groupOfQuestionnaires.heading,
      questionnaireSummaries = groupOfQuestionnaires.links.map(convertToSummary(extSubmission))
    )
  }

  def convertSubmissionToViewModel(extSubmission: ExtendedSubmission)(appId: ApplicationId, appName: String): ViewModel = {
    val groupings = extSubmission.submission.groups.map(convertToViewGrouping(extSubmission))
    ViewModel(appId, appName, groupings)
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
    val submissionService: SubmissionService,
    productionCredentialsChecklistView: ProductionCredentialsChecklistView)
    (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions
     with EitherTHelper[String]
     with SubmissionActionBuilders {

  import cats.implicits._
  import cats.instances.future.catsStdInstancesForFuture
  import ProdCredsChecklistController._

  def productionCredentialsChecklist(productionAppId: ApplicationId): Action[AnyContent] = withApplicationSubmission(StateFilter.inTesting, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)

    val success = (viewModel: ViewModel) => {

      def filterGroupingsForEmptyQuestionnaireSummaries(groupings: NonEmptyList[ViewGrouping]): Option[NonEmptyList[ViewGrouping]] = {
        val filterFn: ViewQuestionnaireSummary => Boolean = _.state == QuestionnaireState.describe(NotApplicable)
        
        groupings
          .map(g => {
            val qs = g.questionnaireSummaries.filterNot(filterFn).toNel
            (g.label, qs)
          })
          .collect {
            case (label, Some(qs)) => ViewGrouping(label, qs)
          }
          .toNel
      }

      filterGroupingsForEmptyQuestionnaireSummaries(viewModel.groupings).fold(
        BadRequest("No questionnaires applicable") 
      )(vg =>
        Ok(productionCredentialsChecklistView(viewModel.copy(groupings = vg)))
      )
    }
    
    val vm = for {
      submission          <- fromOptionF(submissionService.fetchLatestSubmission(productionAppId), "No subsmission and/or application found")
      viewModel           = convertSubmissionToViewModel(submission)(request.application.id, request.application.name)
    } yield viewModel

    vm.fold[Result](failed, success)
  }
}
