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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import config.{ApplicationConfig, ErrorHandler}
import service.{ApplicationService, ApplicationActionService, SessionService}
import play.api.libs.crypto.CookieSigner
import controllers.ApplicationController
import domain.models.applications.ApplicationId
import modules.submissions.views.html.QuestionView
import modules.submissions.domain.models._
import modules.submissions.controllers.SubmissionActionBuilders
import modules.submissions.services.SubmissionService

import helpers.EitherTHelper
import play.api.mvc._

@Singleton
class QuestionsController @Inject()(
  val errorHandler: ErrorHandler,
  val sessionService: SessionService,
  val applicationService: ApplicationService,
  val applicationActionService: ApplicationActionService,
  override val submissionSerivce: SubmissionService,
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

  def showQuestion(submissionId: SubmissionId, questionId: QuestionId) =  withSubmission(submissionId) { implicit request => 
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
        val submitAction: Call = ???
        Ok(questionView(question, applicationId, "12345", submitAction))
      }
    )
    .fold[Result](BadRequest(_), identity(_))
  }
}