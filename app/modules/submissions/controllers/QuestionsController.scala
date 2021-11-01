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

import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import scala.concurrent.ExecutionContext
import config.{ApplicationConfig, ErrorHandler}
import service.{ApplicationService, ApplicationActionService, SessionService}
import play.api.libs.crypto.CookieSigner
import controllers.ApplicationController
import modules.submissions.views.html.QuestionView
import modules.submissions.domain.models._
import modules.submissions.services.SubmissionService
import modules.submissions.domain.services.SubmissionsJsonFormatters._

import scala.concurrent.Future.successful

import helpers.EitherTHelper
import play.api.mvc._
import play.api.libs.json.Json
import cats.data.NonEmptyList

object QuestionsController {
  case class ErrorMessage(message: String)
  implicit val writesErrorMessage = Json.writes[ErrorMessage]

  case class InboundRecordAnswersRequest(answers: NonEmptyList[String])
  implicit val readsInboundRecordAnswersRequest = Json.reads[InboundRecordAnswersRequest]
}

@Singleton
class QuestionsController @Inject()(
  val errorHandler: ErrorHandler,
  val sessionService: SessionService,
  val applicationService: ApplicationService,
  val applicationActionService: ApplicationActionService,
  override val submissionService: SubmissionService,
  val cookieSigner: CookieSigner,
  questionView: QuestionView,
  mcc: MessagesControllerComponents
)(
  implicit override val ec: ExecutionContext,
  val appConfig: ApplicationConfig
) extends ApplicationController(mcc) 
  with SubmissionActionBuilders
  with EitherTHelper[String] {

  import cats.instances.future.catsStdInstancesForFuture

  def showQuestion(submissionId: SubmissionId, questionId: QuestionId, answers: Option[ActualAnswer] = None, errors: Option[String] = None) = withSubmission(submissionId) { implicit request => 
    val currentAnswer = request.submission.answersToQuestions.get(questionId)
    val submission = request.submission
    val oQuestion = submission.findQuestion(questionId)
    val applicationId = request.application.id

    implicit val developerSession = request.developerSession
    (
      for {
        flowItem      <- fromOption(oQuestion, "Question not found in questionnaire")
        question       = oQuestion.get
      } yield {
        val submitAction: Call = modules.submissions.controllers.routes.QuestionsController.recordAnswer(submissionId, questionId)
        errors.fold(
          Ok(questionView(question, applicationId, submitAction, currentAnswer, None))
        )(
          _ => BadRequest(questionView(question, applicationId, submitAction, currentAnswer, errors))
        )
      }
    )
    .fold[Result](BadRequest(_), identity(_))
  }

  def recordAnswer(submissionId: SubmissionId, questionId: QuestionId) = withSubmission(submissionId) { implicit request => 

    val failed = (msg: String) => showQuestion(submissionId, questionId, None, Some("Please provide an answer to the question"))(request)

    val success = (extSubmission: ExtendedSubmission) => { 
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion = extSubmission.questionnaireProgress.get(questionnaire.id)
                        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)
      
      lazy val toProdChecklist = modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklist(extSubmission.submission.applicationId)
      lazy val toNextQuestion = (nextQuestionId) => modules.submissions.controllers.routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toProdChecklist)(toNextQuestion)))
    }
  
    val formValues = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    (
      for {
        formValue <- fromOption(formValues.get("answer"), "Submission found no answer")
        answers   <- fromOption(NonEmptyList.fromList(formValue.toList), "Answer was empty")
        newSubmission <- fromEitherF(submissionService.recordAnswer(submissionId, questionId, answers))
      } yield newSubmission
    )
    .fold(failed, success)
    .flatten
  }
}