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

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.instances.future.catsStdInstancesForFuture

import play.api.mvc.{ActionRefiner, _}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission.Status
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationActionBuilders, ApplicationRequest, HasApplication, TpdfeBaseController, UserRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus

class SubmissionRequest[A](val extSubmission: ExtendedSubmission, val userRequest: UserRequest[A]) extends UserRequest[A](userRequest.userSession, userRequest.msgRequest) {
  lazy val submission         = extSubmission.submission
  lazy val answersToQuestions = submission.latestInstance.answersToQuestions
}

class SubmissionApplicationRequest[A](val application: ApplicationWithCollaborators, val submissionRequest: SubmissionRequest[A], val subscriptions: List[APISubscriptionStatus])
    extends SubmissionRequest[A](submissionRequest.extSubmission, submissionRequest.userRequest) with HasApplication

object SubmissionActionBuilders {

  object RoleFilter {
    type Type = Collaborator.Role => Boolean
    val isAdminRole: Type  = _.isAdministrator
    val isTeamMember: Type = _ => true
  }

  object ApplicationStateFilter {
    type Type = State => Boolean
    val notProduction: Type               = !_.isProduction
    val production: Type                  = _.isProduction
    val preProduction: Type               = _.isPreProduction
    val inTesting: Type                   = _.isTesting
    val allAllowed: Type                  = _ => true
    val pendingApproval: Type             = _.isPendingApproval
    val pendingApprovalOrProduction: Type = _.isPendingApprovalOrProduction
    val inTestingOrProduction: Type       = _.isInTestingOrProduction
  }

  object SubmissionStatusFilter {
    type Type = Status => Boolean
    val answeredCompletely: Type = _.isAnsweredCompletely
    val submitted: Type          = _.isSubmitted

    val submittedGrantedOrDeclined: Type = status =>
      status.isSubmitted || status.isGranted || status.isGrantedWithWarnings || status.isDeclined || status.isFailed || status.isWarnings || status.isPendingResponsibleIndividual
    val granted: Type                    = status => status.isGranted || status.isGrantedWithWarnings
    val allAllowed: Type                 = _ => true
  }
}

trait SubmissionActionBuilders {
  self: ApplicationActionBuilders with TpdfeBaseController =>

  import SubmissionActionBuilders.{ApplicationStateFilter, RoleFilter, SubmissionStatusFilter}

  def submissionService: SubmissionService

  private def submissionRefiner(submissionId: SubmissionId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, SubmissionRequest] =
    new ActionRefiner[UserRequest, SubmissionRequest] {
      def executionContext = ec

      def refine[A](input: UserRequest[A]): Future[Either[Result, SubmissionRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = input
        (
          for {
            submission <- ETR.fromOptionM(submissionService.fetch(submissionId), errorHandler.notFoundTemplate(input).map(NotFound(_)))
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

        ETR.fromOptionM(applicationActionService.process(request.submission.applicationId, request.userRequest), errorHandler.notFoundTemplate(request).map(NotFound(_)))
          .map(r => new SubmissionApplicationRequest(r.application, request, r.subscriptions))
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
            submission <- ETR.fromOptionM(submissionService.fetchLatestExtendedSubmission(request.application.id), errorHandler.notFoundTemplate(request).map(NotFound(_)))
          } yield new SubmissionApplicationRequest(request.application, new SubmissionRequest(submission, request.userRequest), request.subscriptions)
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
            )(_ => None)
        )
      }
    }

  private def submissionFilter[SR[_] <: SubmissionRequest[_]](submissionStatusFilter: SubmissionStatusFilter.Type)(redirectOnIncomplete: => Result): ActionFilter[SR] =
    new ActionFilter[SR] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: SR[A]): Future[Option[Result]] =
        if (submissionStatusFilter(request.extSubmission.submission.status)) {
          successful(None)
        } else {
          successful(Some(redirectOnIncomplete))
        }
    }

  private def applicationStateFilter[AR[_] <: MessagesRequest[_] with HasApplication](allowedStateFilter: State => Boolean): ActionFilter[AR] =
    new ActionFilter[AR] {

      override protected def executionContext: ExecutionContext = ec

      override protected def filter[A](request: AR[A]): Future[Option[Result]] =
        if (allowedStateFilter(request.application.state.name)) {
          successful(None)
        } else {
          successful(Some(BadRequest(s"Application is in an incompatible state of ${request.application.state.name}")))
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

  def withApplicationSubmission(
      allowedStateFilter: ApplicationStateFilter.Type = ApplicationStateFilter.allAllowed,
      allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember
    )(
      applicationId: ApplicationId
    )(
      block: SubmissionApplicationRequest[AnyContent] => Future[Result]
    )(implicit ec: ExecutionContext
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
          applicationRequestRefiner(applicationId) andThen
          collaboratorFilter(allowedRoleFilter) andThen
          applicationSubmissionRefiner andThen
          applicationStateFilter(allowedStateFilter)
      ).invokeBlock(request, block)
    }
  }

  def withApplicationAndSubmissionInSpecifiedState(
      allowedStateFilter: ApplicationStateFilter.Type = ApplicationStateFilter.allAllowed,
      allowedRoleFilter: RoleFilter.Type = RoleFilter.isTeamMember,
      submissionStatusFilter: SubmissionStatusFilter.Type = SubmissionStatusFilter.allAllowed
    )(
      redirectOnNotMatched: => Result
    )(
      applicationId: ApplicationId
    )(
      block: SubmissionApplicationRequest[AnyContent] => Future[Result]
    )(implicit ec: ExecutionContext
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
          applicationRequestRefiner(applicationId) andThen
          collaboratorFilter(allowedRoleFilter) andThen
          applicationSubmissionRefiner andThen
          applicationStateFilter(allowedStateFilter) andThen
          submissionFilter[SubmissionApplicationRequest](submissionStatusFilter)(redirectOnNotMatched)
      ).invokeBlock(request, block)
    }
  }
}
