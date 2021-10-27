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

import controllers.UserRequest
import controllers.BaseController
import controllers.SimpleApplicationActionBuilders
import modules.submissions.domain.models._
import config.ErrorHandler
import scala.concurrent.ExecutionContext
import play.api.mvc.ActionRefiner
import scala.concurrent.Future
import play.api.mvc._
import helpers.EitherTHelper

import cats.implicits._
import cats.instances.future.catsStdInstancesForFuture
import domain.models.applications.Application
import modules.submissions.services.SubmissionService

case class SubmissionRequest[A](submission: Submission , userRequest: UserRequest[A]) extends MessagesRequest[A](userRequest, userRequest.messagesApi) {
  lazy val answersToQuestions = submission.answersToQuestions
  lazy val developerSession = userRequest.developerSession
}

case class SubmissionApplicationRequest[A](application: Application, submissionRequest: SubmissionRequest[A]) extends MessagesRequest[A](submissionRequest, submissionRequest.messagesApi) {
  lazy val submission = submissionRequest.submission
  lazy val answersToQuestions = submission.answersToQuestions
  lazy val developerSession = submissionRequest.userRequest.developerSession
}

trait SubmissionActionBuilders extends SimpleApplicationActionBuilders {
  self: BaseController =>

  val errorHandler: ErrorHandler
  val submissionService: SubmissionService
  private[this] val ecPassThru = ec

  val E = new EitherTHelper[Result] {
    implicit val ec: ExecutionContext = ecPassThru
  }

  def submissionRefiner(submissionId: SubmissionId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, SubmissionRequest] =
    new ActionRefiner[UserRequest, SubmissionRequest] {
      def executionContext = ec
      def refine[A](input: UserRequest[A]): Future[Either[Result, SubmissionRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = input
        (
          for {
            submission <- E.fromOptionF(submissionService.fetch(submissionId), NotFound(errorHandler.notFoundTemplate(Request(input, input.developerSession))) )
          } yield SubmissionRequest(submission.submission, input)
        )
        .value
      }
    }

  def submissionApplicationRefiner(implicit ec: ExecutionContext): ActionRefiner[SubmissionRequest, SubmissionApplicationRequest] =
    new ActionRefiner[SubmissionRequest, SubmissionApplicationRequest] {
      override def executionContext = ec
      override def refine[A](request: SubmissionRequest[A]): Future[Either[Result, SubmissionApplicationRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = request
        
        applicationActionService.process(request.submission.applicationId, request.userRequest.developerSession)
        .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, request.userRequest.developerSession))))
        .map(r => SubmissionApplicationRequest(r.application, request))
        .value
      }
    }

  def withSubmission(submissionId: SubmissionId)(fun: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      val composedActions =
        Action andThen
        loggedInActionRefiner(onlyTrueIfLoggedInFilter) andThen
        submissionRefiner(submissionId) andThen
        submissionApplicationRefiner

      composedActions.async(fun)(request)
    }
  }
}