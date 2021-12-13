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
import controllers.ApplicationActionBuilders
import controllers.HasApplication
import modules.submissions.domain.models._
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
import domain.models.applications.CollaboratorRole

class SubmissionRequest[A](val extSubmission: ExtendedSubmission, val userRequest: UserRequest[A]) extends UserRequest[A](userRequest.developerSession, userRequest.msgRequest) {
  lazy val submission = extSubmission.submission
  lazy val answersToQuestions = submission.answersToQuestions
}

class SubmissionApplicationRequest[A](val application: Application, val submissionRequest: SubmissionRequest[A]) extends SubmissionRequest[A](submissionRequest.extSubmission, submissionRequest.userRequest) with HasApplication

object SubmissionActionBuilders {
  
  object RoleFilter {
    type Type = CollaboratorRole => Boolean
    val isAdminRole: Type = _.isAdministrator
    val isTeamMember: Type = _ => true
  }

  
  object StateFilter {
    type Type = State => Boolean
    val notProduction: Type = _ != State.PRODUCTION
    val inTesting: Type = _ == State.TESTING
    val allAllowed: Type = _ => true
    val pendingApproval: Type = s => s == State.PENDING_GATEKEEPER_APPROVAL || s == State.PENDING_REQUESTER_VERIFICATION
  }

}
trait SubmissionActionBuilders {
  self: BaseController with ApplicationActionBuilders =>

  import SubmissionActionBuilders._

  val submissionService: SubmissionService
  
  private[this] val ecPassThru = ec

  private val E = new EitherTHelper[Result] {
    implicit val ec: ExecutionContext = ecPassThru
  }

  private def submissionRefiner(submissionId: SubmissionId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, SubmissionRequest] =
    new ActionRefiner[UserRequest, SubmissionRequest] {
      def executionContext = ec
      def refine[A](input: UserRequest[A]): Future[Either[Result, SubmissionRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = input
        (
          for {
            submission <- E.fromOptionF(submissionService.fetch(submissionId), NotFound(errorHandler.notFoundTemplate(input)) )
          } yield new SubmissionRequest(submission, input)
        )
        .value
      }
    }

  private def submissionApplicationRefiner(implicit ec: ExecutionContext): ActionRefiner[SubmissionRequest, SubmissionApplicationRequest] =
    new ActionRefiner[SubmissionRequest, SubmissionApplicationRequest] {
      override def executionContext = ec
      override def refine[A](request: SubmissionRequest[A]): Future[Either[Result, SubmissionApplicationRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = request
        
        applicationActionService.process(request.submission.applicationId, request.userRequest)
        .toRight(NotFound(errorHandler.notFoundTemplate(request)))
        .map(r => new SubmissionApplicationRequest(r.application, request))
        .value
      }
    }

  private def applicationSubmissionRefiner(implicit ec: ExecutionContext): ActionRefiner[ApplicationRequest, SubmissionApplicationRequest] =
    new ActionRefiner[ApplicationRequest, SubmissionApplicationRequest] {
      override def executionContext = ec
      override def refine[A](request: ApplicationRequest[A]): Future[Either[Result, SubmissionApplicationRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = request
        
        (
          for {
            submission <- E.fromOptionF(submissionService.fetchLatestSubmission(request.application.id), NotFound(errorHandler.notFoundTemplate(request)) )
          } yield new SubmissionApplicationRequest(request.application, new SubmissionRequest(submission, request.userRequest))
        )
        .value
      }
    }

  private def collaboratorFilter(allowedRoleFilter: RoleFilter.Type): ActionFilter[ApplicationRequest] = 
    new ActionFilter[ApplicationRequest] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
        val thisCollaboratorRole = request.application.collaborators.find(_.userId == request.userId).map(_.role)

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

  private def completedSubmissionFilter[SR[_] <: SubmissionRequest[_]](redirectOnIncomplete: => Result): ActionFilter[SR] = 
    new ActionFilter[SR] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: SR[A]): Future[Option[Result]] =
        if(request.extSubmission.isCompleted) {
          successful(None)
        } else {
          successful(Some(redirectOnIncomplete))
        }
    }
    

  private def applicationStateFilter[AR[_] <: MessagesRequest[_] with HasApplication](allowedStateFilter: State => Boolean): ActionFilter[AR] = 
    new ActionFilter[AR] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: AR[A]): Future[Option[Result]] =
        if(allowedStateFilter(request.application.state.name)) {
          successful(None)
        } else {
          successful(Some(BadRequest("Application is in an incompatible state")))
        }
    }

  def withSubmission(submissionId: SubmissionId)(block: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        submissionRefiner(submissionId) andThen
        submissionApplicationRefiner
      ).invokeBlock(request, block)
    }
  }

  def withApplicationSubmission(allowedStateFilter: StateFilter.Type = StateFilter.allAllowed, allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember)(applicationId: ApplicationId)(block: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId) andThen
        collaboratorFilter(allowedRoleFilter) andThen
        applicationSubmissionRefiner andThen
        applicationStateFilter(allowedStateFilter)
      ).invokeBlock(request,block)
    }
  }

  def withApplicationAndCompletedSubmission(allowedStateFilter: StateFilter.Type = StateFilter.allAllowed, allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember)(redirectOnIncomplete: => Result)(applicationId: ApplicationId)(block: SubmissionApplicationRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId) andThen
        collaboratorFilter(allowedRoleFilter) andThen
        applicationSubmissionRefiner andThen
        applicationStateFilter(allowedStateFilter) andThen
        completedSubmissionFilter(redirectOnIncomplete)
      ).invokeBlock(request, block)
    }
  }
}