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
import config.ApplicationConfig
import controllers.ApplicationController
import domain.models.applications.ApplicationId
import modules.submissions.domain.models.SubmissionId
import play.api.data.Form
import scala.concurrent.Future.successful
import helpers.EitherTHelper
import modules.submissions.services.SubmissionService
import play.api.mvc.Result
import domain.models.controllers.BadRequestWithErrorMessage
import service.SessionService
import service.ApplicationActionService
import service.ApplicationService
import play.api.libs.crypto.CookieSigner
import config.ErrorHandler
import modules.submissions.views.html.{CancelledRequestForProductionCredentialsView, ConfirmCancelRequestForProductionCredentialsView}

object CancelRequestController {
  case class DummyForm(dummy: String = "dummy")

  object DummyForm {
    import play.api.data.Forms.{ignored, mapping}

    def form: Form[DummyForm] = {
      Form(
        mapping(
          "dummy" -> ignored("dummy")
        )(DummyForm.apply)(DummyForm.unapply)
      )
    }
  }
}

@Singleton
class CancelRequestController @Inject() (
  val errorHandler: ErrorHandler,
  val cookieSigner: CookieSigner,
  val sessionService: SessionService,
  val applicationActionService: ApplicationActionService,
  val applicationService: ApplicationService,
  mcc: MessagesControllerComponents,
  submissionService: SubmissionService,
  confirmCancelRequestForProductionCredentialsView: ConfirmCancelRequestForProductionCredentialsView,
  cancelledRequestForProductionCredentialsView: CancelledRequestForProductionCredentialsView,
)
(
  implicit val ec: ExecutionContext,
  val appConfig: ApplicationConfig
) extends ApplicationController(mcc) with EitherTHelper[String] {
  
  import cats.implicits._
  import cats.instances.future.catsStdInstancesForFuture

  def cancelRequestForProductionCredentialsPage(appId: ApplicationId) = whenTeamMemberOnApp(appId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)
    val success = (id: SubmissionId) => Ok(confirmCancelRequestForProductionCredentialsView(appId, CancelRequestController.DummyForm.form))
    
    (
      for {
        extSubmission          <- fromOptionF(submissionService.fetchLatestSubmission(appId), "No subsmission and/or application found")
      } yield extSubmission.submission.id
    )
    .fold[Result](failed, success)
  }

  def cancelRequestForProductionCredentialsAction(appId: ApplicationId) = whenTeamMemberOnApp(appId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)
    
    def success(id: SubmissionId, action: String) = action match {
      case "cancel-request" => Ok(cancelledRequestForProductionCredentialsView(request.application.name))
      case "dont-cancel-request" => Redirect(modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklist(appId))
    }

    val isValidSubmit: (String) => Boolean = (s) => s == "cancel-request" || s == "dont-cancel-request"

    (
      for {
        extSubmission          <- fromOptionF(submissionService.fetchLatestSubmission(appId), "No subsmission and/or application found")
        formValues              = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
        submitAction           <- fromOption(formValues.get("submit-action").flatMap(_.headOption).filter(isValidSubmit), "Bad form data")
      } yield (extSubmission.submission.id, submitAction)
    )
    .fold[Result](failed, (success _).tupled)
  }
}