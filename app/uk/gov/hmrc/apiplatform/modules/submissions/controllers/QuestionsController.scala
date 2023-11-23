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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import cats.data.NonEmptyList
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.{Json, OFormat, OWrites, Reads}
import play.api.mvc.{MessagesControllerComponents, _}
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.services.SubmissionsFrontendJsonFormatters._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId

object QuestionsController {
  case class ErrorMessage(message: String)
  implicit val writesErrorMessage: OWrites[ErrorMessage] = Json.writes[ErrorMessage]

  case class InboundRecordAnswersRequest(answers: NonEmptyList[String])
  implicit val readsInboundRecordAnswersRequest: Reads[InboundRecordAnswersRequest] = Json.reads[InboundRecordAnswersRequest]

  case class ViewErrorInfo private (summary: String, message: String)

  object ViewErrorInfo {
    implicit val format: OFormat[ViewErrorInfo] = Json.format[ViewErrorInfo]

    def apply(errorInfo: ErrorInfo): ViewErrorInfo = errorInfo match {
      case ErrorInfo(summary, Some(message)) => new ViewErrorInfo(summary, message)
      case ErrorInfo(summary, None)          => new ViewErrorInfo(summary, summary)
    }
  }
}

@Singleton
class QuestionsController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    override val submissionService: SubmissionService,
    val cookieSigner: CookieSigner,
    questionView: QuestionView,
    checkAnswersView: CheckAnswersView,
    mcc: MessagesControllerComponents
  )(implicit override val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with SubmissionActionBuilders
    with EitherTHelper[String] {

  import cats.instances.future.catsStdInstancesForFuture
  import QuestionsController._

  private def processQuestion(
      submissionId: SubmissionId,
      questionId: Question.Id,
      onFormAnswer: Option[ActualAnswer],
      errorInfo: Option[ErrorInfo]
    )(
      submitAction: Call
    )(implicit request: SubmissionApplicationRequest[AnyContent]
    ) = {
    val persistedAnswer = request.submission.latestInstance.answersToQuestions.get(questionId)
    val submission      = request.submission
    val oQuestion       = submission.findQuestion(questionId)
    val applicationId   = request.application.id

    (
      for {
        flowItem <- fromOption(oQuestion, "Question not found in questionnaire")
        question  = oQuestion.get
      } yield {
        errorInfo.fold(
          Ok(questionView(question, applicationId, submitAction, persistedAnswer, None))
        )(ei => BadRequest(questionView(question, applicationId, submitAction, onFormAnswer, Some(ViewErrorInfo(ei)))))
      }
    )
      .fold[Result](BadRequest(_), identity(_))
  }

  def showQuestion(submissionId: SubmissionId, questionId: Question.Id, onFormAnswer: Option[ActualAnswer] = None, errorInfo: Option[ErrorInfo] = None): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val submitAction = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.recordAnswer(submissionId, questionId)
      processQuestion(submissionId, questionId, onFormAnswer, errorInfo)(submitAction)
    }

  def updateQuestion(submissionId: SubmissionId, questionId: Question.Id, onFormAnswer: Option[ActualAnswer] = None, errorInfo: Option[ErrorInfo] = None): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val submitAction = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.updateAnswer(submissionId, questionId)
      processQuestion(submissionId, questionId, onFormAnswer, errorInfo)(submitAction)
    }

  private def processAnswer(
      submissionId: SubmissionId,
      questionId: Question.Id
    )(
      success: (ExtendedSubmission) => Future[Result]
    )(implicit request: SubmissionApplicationRequest[AnyContent]
    ) = {
    def failed(answers: List[String]) = (msg: String) => {
      val defaultMessage = "Please provide an answer to the question"
      import cats.implicits._

      logger.info(s"Failed to recordAnswer - $msg")

      val question = request.submission.findQuestion(questionId).get

      val errorInfo = (question match {
        case q: Question with ErrorMessaging => q.errorInfo
        case _                               => None
      })
        .getOrElse(ErrorInfo(defaultMessage, defaultMessage))

      val onFormAnswer = question match {
        case q: TextQuestion => answers.headOption.map(TextAnswer)
        case _               => None
      }

      showQuestion(submissionId, questionId, onFormAnswer, errorInfo.some)(request)
    }

    val formValues   = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    val submitAction = formValues.get("submit-action").flatMap(_.headOption)
    val rawAnswers   = formValues.get("answer").fold(List.empty[String])(_.toList.filter(_.nonEmpty))
    val answers      = rawAnswers.map(a => a.trim())

    import cats.implicits._
    import cats.instances.future.catsStdInstancesForFuture

    def validateAnswers(submitAction: Option[String], answers: List[String]): Either[String, List[String]] = (submitAction, answers) match {
      case (Some("acknowledgement"), Nil) => Either.right(Nil)
      case (Some("acknowledgement"), _)   => Either.left("Bad request - values for acknowledgement")
      case (Some("save"), Nil)            => Either.left("save action requires values")
      case (Some("save"), List(""))       => Either.left("save action requires non blank values")
      case (Some("save"), _)              => Either.right(answers)
      case (Some("no-answer"), _)         => Either.right(Nil)
      case (None, _)                      => Either.left("Bad request - no action")
      case (Some(_), _)                   => Either.left("Bad request - no such action")
    }

    (
      for {
        effectiveAnswers <- fromEither(validateAnswers(submitAction, answers))
        // TODO - add validation
        result           <- fromEitherF(submissionService.recordAnswer(submissionId, questionId, effectiveAnswers))
      } yield result
    )
      .fold(failed(answers), success)
      .flatten
  }

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion  = extSubmission.questionnaireProgress.get(questionnaire.id)
        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)

      lazy val toProdChecklist =
        uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(extSubmission.submission.applicationId)
      lazy val toNextQuestion  = (nextQuestionId) => uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toProdChecklist)(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }

  def updateAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    def hasQuestionBeenAnswered(questionId: Question.Id) = {
      request.submission.latestInstance.answersToQuestions.get(questionId).fold(false)(_ => true)
    }

    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion  = extSubmission.questionnaireProgress.get(questionnaire.id)
        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)

      lazy val toCheckAnswers = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(request.submission.applicationId)
      lazy val toNextQuestion = (nextQuestionId: Question.Id) =>
        if (hasQuestionBeenAnswered(nextQuestionId)) {
          uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(request.submission.applicationId)
        } else {
          uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.updateQuestion(submissionId, nextQuestionId)
        }

      successful(Redirect(nextQuestion.fold(toCheckAnswers)(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }
}
