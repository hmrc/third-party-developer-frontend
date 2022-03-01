/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc.MessagesControllerComponents
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, ApplicationActionService, SessionService}
import play.api.libs.crypto.CookieSigner
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.domain.services.SubmissionsFrontendJsonFormatters._

import scala.concurrent.Future.successful

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import play.api.mvc._
import play.api.libs.json.Json
import cats.data.NonEmptyList
import scala.concurrent.Future

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
  checkAnswersView: CheckAnswersView,
  mcc: MessagesControllerComponents
)(
  implicit override val ec: ExecutionContext,
  val appConfig: ApplicationConfig
) extends ApplicationController(mcc) 
  with SubmissionActionBuilders
  with EitherTHelper[String] {

  import cats.instances.future.catsStdInstancesForFuture

  private def processQuestion(submissionId: Submission.Id, questionId: QuestionId, answers: Option[ActualAnswer] = None, errors: Option[String] = None)(submitAction: Call)(implicit request: SubmissionApplicationRequest[AnyContent]) = {
    val currentAnswer = request.submission.latestInstance.answersToQuestions.get(questionId)
    val submission = request.submission
    val oQuestion = submission.findQuestion(questionId)
    val applicationId = request.application.id

    (
      for {
        flowItem      <- fromOption(oQuestion, "Question not found in questionnaire")
        question       = oQuestion.get
      } yield {
        errors.fold(
          Ok(questionView(question, applicationId, submitAction, currentAnswer, None))
        )(
          _ => BadRequest(questionView(question, applicationId, submitAction, currentAnswer, errors))
        )
      }
    )
    .fold[Result](BadRequest(_), identity(_))
  }

  def showQuestion(submissionId: Submission.Id, questionId: QuestionId, answers: Option[ActualAnswer] = None, errors: Option[String] = None) = withSubmission(submissionId) { implicit request => 
    val submitAction = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.recordAnswer(submissionId, questionId)
    processQuestion(submissionId, questionId, answers, errors)(submitAction)
  }

  def updateQuestion(submissionId: Submission.Id, questionId: QuestionId, answers: Option[ActualAnswer] = None, errors: Option[String] = None) = withSubmission(submissionId) { implicit request => 
    val submitAction: Call = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.updateAnswer(submissionId, questionId)
    processQuestion(submissionId, questionId, answers, errors)(submitAction)
  }

  private def processAnswer(submissionId: Submission.Id, questionId: QuestionId)(success: (ExtendedSubmission) => Future[Result])(implicit request: SubmissionApplicationRequest[AnyContent]) = {
    lazy val failed = (msg: String) => {
      logger.info(s"Failed to recordAnswer - $msg")
      showQuestion(submissionId, questionId, None, Some("Please provide an answer to the question"))(request)
    }

    val formValues = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    val submitAction = formValues.get("submit-action").flatMap(_.headOption)
    val answers = formValues.get("answer").fold(List.empty[String])(_.toList.filter(_.nonEmpty))

    import cats.implicits._
    import cats.instances.future.catsStdInstancesForFuture

    def validateAnswers(submitAction: Option[String], answers: List[String]): Either[String, List[String]] = (submitAction, answers) match {
      case (Some("acknowledgement"), Nil) => Either.right(Nil)
      case (Some("acknowledgement"), _) => Either.left("Bad request - values for acknowledgement")
      case (Some("save"), Nil) => Either.left("save action requires values")
      case (Some("save"), _) => Either.right(answers)
      case (Some("no-answer"), _) => Either.right(Nil)
      case (None, _) => Either.left("Bad request - no action")
      case (Some(_), _) => Either.left("Bad request - no such action")
    }

    (
      for {
        effectiveAnswers  <- fromEither(validateAnswers(submitAction, answers))
        result            <- fromEitherF(submissionService.recordAnswer(submissionId, questionId, effectiveAnswers))
      } yield result
    )
    .fold(failed, success)
    .flatten
  } 

  def recordAnswer(submissionId: Submission.Id, questionId: QuestionId) = withSubmission(submissionId) { implicit request => 
    val success = (extSubmission: ExtendedSubmission) => { 
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion = extSubmission.questionnaireProgress.get(questionnaire.id)
                        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)
      
      lazy val toProdChecklist = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(extSubmission.submission.applicationId)
      lazy val toNextQuestion = (nextQuestionId) => uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toProdChecklist)(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }

  def updateAnswer(submissionId: Submission.Id, questionId: QuestionId) = withSubmission(submissionId) { implicit request =>
    def hasQuestionBeenAnswered(questionId: QuestionId) = {
      request.submission.latestInstance.answersToQuestions.get(questionId).fold(false)(_ => true)
    }

    val success = (extSubmission: ExtendedSubmission) => { 
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion = extSubmission.questionnaireProgress.get(questionnaire.id)
                        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)
      
      lazy val toCheckAnswers = uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(request.submission.applicationId)
      lazy val toNextQuestion = (nextQuestionId: QuestionId) => if(hasQuestionBeenAnswered(nextQuestionId))
        uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(request.submission.applicationId)
      else
        uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.QuestionsController.updateQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toCheckAnswers)(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }
}