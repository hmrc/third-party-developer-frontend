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
import domain.models.applications.ApplicationId
import controllers.ApplicationRequest
import scala.concurrent.Future.successful
import domain.models.applications.State
import domain.models.developers.DeveloperSession
import domain.models.applications.CollaboratorRole

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

  implicit def userFromSubmissionApplicationRequest(implicit request: SubmissionApplicationRequest[_]): DeveloperSession = request.developerSession

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
            submission <- E.fromOptionF(submissionService.fetch(submissionId), NotFound(errorHandler.notFoundTemplate(input)) )
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
        .toRight(NotFound(errorHandler.notFoundTemplate(request)))
        .map(r => SubmissionApplicationRequest(r.application, request))
        .value
      }
    }

  def applicationSubmissionRefiner(implicit ec: ExecutionContext): ActionRefiner[ApplicationRequest, SubmissionApplicationRequest] =
    new ActionRefiner[ApplicationRequest, SubmissionApplicationRequest] {
      override def executionContext = ec
      override def refine[A](request: ApplicationRequest[A]): Future[Either[Result, SubmissionApplicationRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = request
        
        (
          for {
            submission <- E.fromOptionF(submissionService.fetchLatestSubmission(request.application.id), NotFound(errorHandler.notFoundTemplate(request)) )
          } yield SubmissionApplicationRequest(request.application, SubmissionRequest(submission.submission, UserRequest(request.user, request.request)))
        )
        .value
      }
    }

  object RoleFilter {
    type Type = CollaboratorRole => Boolean
    val isAdminRole: Type = _.isAdministrator
    val isTeamMember: Type = _ => true
  }

  def collaboratorFilter(allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember): ActionFilter[ApplicationRequest] = 
    new ActionFilter[ApplicationRequest] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
        val thisUserId = request.user.developer.userId
        val thisCollaboratorRole = request.application.collaborators.find(_.userId == thisUserId).map(_.role)

        successful(
          thisCollaboratorRole
          .filter(allowedRoleFilter)
          .fold[Option[Result]](
            Some(BadRequest("User is not authorised for this action"))
          )(
            _ => None
          )
        )
      }
    }

  def applicationStateFilter(allowedStateFilter: State => Boolean): ActionFilter[SubmissionApplicationRequest] = 
    new ActionFilter[SubmissionApplicationRequest] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: SubmissionApplicationRequest[A]): Future[Option[Result]] =
        if(allowedStateFilter(request.application.state.name)) {
          successful(None)
        } else {
          successful(Some(BadRequest("Application is in an incompatible state")))
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

  def withApplicationSubmission(applicationId: ApplicationId)(fun: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      val composedActions =
        Action andThen
        loggedInActionRefiner(onlyTrueIfLoggedInFilter) andThen
        applicationRequestRefiner(applicationId) andThen
        applicationSubmissionRefiner

      composedActions.async(fun)(request)
    }
  }

  object StateFilter {
    type Type = State => Boolean
    val notProduction: Type = _ != State.PRODUCTION
    val inTesting: Type = _ == State.TESTING
    val allAllowed: Type = _ => true
  }

  def withApplicationSubmission(allowedStateFilter: StateFilter.Type = StateFilter.allAllowed, allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember)(applicationId: ApplicationId)(fun: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      val composedActions =
        Action andThen
        loggedInActionRefiner(onlyTrueIfLoggedInFilter) andThen
        applicationRequestRefiner(applicationId) andThen
        collaboratorFilter(allowedRoleFilter) andThen
        applicationSubmissionRefiner andThen
        applicationStateFilter(allowedStateFilter)

      composedActions.async(fun)(request)
    }
  }
}