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

import cats.data.NonEmptyList

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationState}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.QuestionnaireState.NotApplicable
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

object ProdCredsChecklistController {
  case class DummyForm(dummy: String = "dummy")

  object DummyForm {
    import play.api.data.Forms.{ignored, mapping}

    def form: Form[DummyForm] = {
      Form(
        mapping(
          "dummy" -> ignored("dummy")
        )(DummyForm.apply)(DummyForm.unapply)
      )
    }
  }

  case class ViewQuestionnaireSummary(label: String, state: String, isComplete: Boolean, id: Questionnaire.Id = Questionnaire.Id.random, nextQuestionUrl: Option[String] = None) {
    lazy val fieldName = label.toLowerCase
  }
  case class ViewGrouping(label: String, questionnaireSummaries: NonEmptyList[ViewQuestionnaireSummary])
  case class ViewModel(appId: ApplicationId, appName: ApplicationName, isNewTermsOfUseUplift: Boolean, groupings: NonEmptyList[ViewGrouping])

  def convertToSummary(extSubmission: ExtendedSubmission)(questionnaire: Questionnaire): ViewQuestionnaireSummary = {
    val progress   = extSubmission.questionnaireProgress.get(questionnaire.id).get
    val state      = QuestionnaireState.describe(progress.state)
    val isComplete = QuestionnaireState.isCompleted(progress.state)
    val url        = progress.questionsToAsk.headOption.map(q =>
      uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.showQuestion(extSubmission.submission.id, q).url
    )
    ViewQuestionnaireSummary(questionnaire.label.value, state, isComplete, questionnaire.id, url)
  }

  def convertToViewGrouping(extSubmission: ExtendedSubmission)(groupOfQuestionnaires: GroupOfQuestionnaires): ViewGrouping = {
    ViewGrouping(
      label = groupOfQuestionnaires.heading,
      questionnaireSummaries = groupOfQuestionnaires.links.map(convertToSummary(extSubmission))
    )
  }

  def convertSubmissionToViewModel(extSubmission: ExtendedSubmission)(appId: ApplicationId, appName: ApplicationName, appState: ApplicationState): ViewModel = {
    val groupings = extSubmission.submission.groups.map(convertToViewGrouping(extSubmission))
    ViewModel(appId, appName, appState.name.isProduction, groupings)
  }

  def filterGroupingsForEmptyQuestionnaireSummaries(groupings: NonEmptyList[ViewGrouping]): Option[NonEmptyList[ViewGrouping]] = {
    import cats.implicits._

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
}

@Singleton
class ProdCredsChecklistController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val submissionService: SubmissionService,
    productionCredentialsChecklistView: ProductionCredentialsChecklistView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with EitherTHelper[String]
    with SubmissionActionBuilders
    with WithUnsafeDefaultFormBinding {

  import ProdCredsChecklistController._
  import SubmissionActionBuilders.{ApplicationStateFilter, RoleFilter}

  def productionCredentialsChecklistPage(
      productionAppId: ApplicationId
    ): Action[AnyContent] = withApplicationSubmission(ApplicationStateFilter.inTestingOrProduction, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    val show = (viewModel: ViewModel) => {
      filterGroupingsForEmptyQuestionnaireSummaries(viewModel.groupings).fold(
        BadRequest("No questionnaires applicable")
      )(vg =>
        Ok(productionCredentialsChecklistView(viewModel.copy(groupings = vg), DummyForm.form.fillAndValidate(DummyForm("dummy"))))
      )
    }
    successful(show(convertSubmissionToViewModel(request.extSubmission)(request.application.id, request.application.name, request.application.state)))
  }

  def productionCredentialsChecklistAction(
      productionAppId: ApplicationId
    ) = withApplicationSubmission(ApplicationStateFilter.inTestingOrProduction, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    def handleValidForm(validForm: DummyForm) = {
      if (request.extSubmission.submission.status.isAnsweredCompletely) {
        successful(Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(productionAppId)))
      } else {
        val viewModel = convertSubmissionToViewModel(request.extSubmission)(request.application.id, request.application.name, request.application.state)

        successful(
          filterGroupingsForEmptyQuestionnaireSummaries(viewModel.groupings).fold(
            throw new AssertionError("submissions with only n/a questionnaires will be marked as complete")
          )(vg => {
            val form = vg.flatMap(group => group.questionnaireSummaries)
              .filter(!_.isComplete)
              .foldLeft(DummyForm.form.fill(validForm))((form, summary) => form.withError(summary.fieldName, s"Complete the ${summary.label.toLowerCase} section"))

            Ok(productionCredentialsChecklistView(viewModel.copy(groupings = vg), form))
          })
        )
      }
    }

    def handleInvalidForm(formWithErrors: Form[DummyForm]) =
      throw new AssertionError("DummyForm has no validation rules and so can never be invalid")

    DummyForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
