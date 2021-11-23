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

// package modules.submissions.controllers

// import play.api.mvc._
// import play.api.mvc.Results._
// import controllers.ApplicationController
// import controllers.ActionBuilders
// import controllers.ApplicationRequest
// import modules.submissions.services.SubmissionService
// import domain.models.applications.ApplicationId
// import scala.concurrent.Future
// import controllers.SimpleApplicationActionBuilders
// import domain.models.developers.DeveloperSession
// import scala.concurrent.ExecutionContext
// import modules.submissions.domain.models.ExtendedSubmission
// import uk.gov.hmrc.http.HeaderCarrier
// import uk.gov.hmrc.play.http.HeaderCarrierConverter
// import helpers.EitherTHelper

// case class SubmissionRequest[A](extSubmission: ExtendedSubmission, request: ApplicationRequest[A]) extends MessagesRequest[A](request, request.messagesApi)

// trait SubmissionActionBuilders extends SimpleApplicationActionBuilders {
//   val submissionService: SubmissionService
//   implicit val ec: ExecutionContext
  
//   val ecPassThru: ExecutionContext = ec
  
//   private val ET = new EitherTHelper[Result] {
//     implicit val ec: ExecutionContext = ecPassThru
//   }

//   private implicit def hc(implicit request: Request[_]): HeaderCarrier =
//     HeaderCarrierConverter.fromRequestAndSession(request, request.session)
  
//   def submissionAction(applicationId: ApplicationId, developerSession: DeveloperSession)(implicit ec: ExecutionContext): ActionRefiner[ApplicationRequest, SubmissionRequest] =
//     new ActionRefiner[ApplicationRequest, SubmissionRequest] {
//       override protected def executionContext: ExecutionContext = ec

//       override def refine[A](request: ApplicationRequest[A]): Future[Either[Result, SubmissionRequest[A]]] = {
//         implicit val implicitRequest: MessagesRequest[A] = request
//         import cats.implicits._

//         lazy val notFound = NotFound(errorHandler.notFoundTemplate(Request(request, developerSession)))

//         (
//           for {
//             extSubmission <- ET.fromOptionF(submissionService.fetchLatestSubmission(applicationId), notFound)
//             submissionRequest = SubmissionRequest(extSubmission, request)
//           } yield submissionRequest
//         ).value
//       }
//     }
// }

// abstract class SubmissionApplicationController(mcc: MessagesControllerComponents) extends ApplicationController(mcc) with ActionBuilders {
//   val submissionService: SubmissionService

//   def whenTeamMemberOnAppWithActiveSubmission(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
//     loggedInAction { implicit request =>
//       val composedActions = Action andThen applicationAction(applicationId, loggedIn)
//       composedActions.async(fun)(request)
//     }
// }
