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
import modules.submissions.domain.services.SubmissionsFrontendJsonFormatters._

import helpers.EitherTHelper
import play.api.mvc._
import play.api.libs.json.Json
import cats.data.NonEmptyList

object QuestionsController {
  case class ErrorMessage(message: String)
  implicit val writesErrorMessage = Json.writes[ErrorMessage]

  case class RecordAnswersRequest(answers: NonEmptyList[String])
  implicit val readsRecordAnswersRequest = Json.reads[RecordAnswersRequest]
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
  import QuestionsController._

  def showQuestion(submissionId: SubmissionId, questionId: QuestionId) = withSubmission(submissionId) { implicit request => 
    val answers = request.submission.answersToQuestions
    val submission = request.submission
    val oQuestion = submission.findQuestion(questionId)
    val applicationId = request.application.id

    implicit val developerSession = request.developerSession

    (
      for {
        flowItem      <- fromOption(oQuestion, "Question not found in questionnaire")
        question       = oQuestion.get
      } yield {
        val submitAction: Call = controllers.checkpages.routes.ApplicationCheck.requestCheckPage(applicationId)
        Ok(questionView(question, applicationId, "12345", submitAction))
      }
    )
    .fold[Result](BadRequest(_), identity(_))
  }

  def recordAnswer(submissionId: SubmissionId, questionId: QuestionId) = withSubmissionJson(submissionId) { implicit request => 
    withJsonBody[RecordAnswersRequest] { recordAnswersRequest =>
      val failed = (msg: String) => BadRequest(Json.toJson(ErrorMessage(msg)))
      val success = (s: ExtendedSubmission) => Ok(Json.toJson(s))

      submissionService.recordAnswer(submissionId, questionId, recordAnswersRequest.answers).map(_.fold(failed, success))
    }
  }

  // def recordAnswer(submissionId: SubmissionId, questionId: QuestionId) = withSubmission(submissionId) { implicit request => 
  //   val previousAnswers = request.submission.answersToQuestions
  //   val submission = request.submission
  //   val oQuestion = submission.findQuestion(questionId)
  //   val applicationId = request.application.id

  //   implicit val developerSession = request.developerSession
    
  //   val failed = (msg: String) => BadRequest(Json.toJson(ErrorMessage(msg)))

  //   val success = (s: ExtendedSubmission) => Ok(Json.toJson(s))

  //   withJsonBody[RecordAnswersRequest] { answersRequest =>
  //     submissionSerivce.recordAnswer(submissionId, questionId, answersRequest.answers).map(_.fold(failed, success))
  //   }
    
  //   ???
  // }
}