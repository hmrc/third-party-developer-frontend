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
import play.api.data.Form
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
import connectors.ThirdPartyApplicationProductionConnector
import domain.models.applications.State

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
  val submissionService: SubmissionService,
  productionApplicationConnector: ThirdPartyApplicationProductionConnector,
  confirmCancelRequestForProductionCredentialsView: ConfirmCancelRequestForProductionCredentialsView,
  cancelledRequestForProductionCredentialsView: CancelledRequestForProductionCredentialsView
)
(
  implicit val ec: ExecutionContext,
  val appConfig: ApplicationConfig
) extends ApplicationController(mcc)
  with EitherTHelper[String]
  with SubmissionActionBuilders {
  
  import cats.implicits._
  import cats.instances.future.catsStdInstancesForFuture
  import SubmissionActionBuilders.StateFilter

  private val exec = ec
  private val ET = new EitherTHelper[Result] { implicit val ec: ExecutionContext = exec }
  private val failed = (err: String) => BadRequestWithErrorMessage(err)

  def cancelRequestForProductionCredentialsPage(appId: ApplicationId) = withApplicationSubmission(StateFilter.notProduction)(appId) { implicit request =>
    (
      for {
        extSubmission          <- ET.fromOptionF(submissionService.fetchLatestSubmission(appId), failed("No subsmission and/or application found"))
        _                      <- ET.cond(request.application.state.name != State.PRODUCTION, (), failed("Application submissions can only be cancelled when not already in production."))
      } yield Ok(confirmCancelRequestForProductionCredentialsView(appId, CancelRequestController.DummyForm.form))
    )
    .fold[Result](identity, identity)
  }

  def cancelRequestForProductionCredentialsAction(appId: ApplicationId) = withApplicationSubmission(StateFilter.notProduction)(appId) { implicit request =>
    lazy val goBackToProdCredsChecklist = Redirect(modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklist(appId))

    val isValidSubmit: (String) => Boolean = (s) => s == "cancel-request" || s == "dont-cancel-request"

    val x = (
      for {
        extSubmission          <- ET.fromOptionF(submissionService.fetchLatestSubmission(appId), failed("No subsmission and/or application found"))
        _                      <- ET.cond(request.application.state.name != State.PRODUCTION, (), failed("Application submissions can only be cancelled when not already in production."))
        formValues              = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
        submitAction           <- ET.fromOption(
                                    formValues.get("submit-action")
                                      .flatMap(_.headOption)
                                      .filter(isValidSubmit), 
                                    failed("Bad form data")
                                  )
        cancelAction           <- ET.cond(submitAction == "cancel-request", submitAction, goBackToProdCredsChecklist )
        _                      <- ET.liftF(productionApplicationConnector.deleteApplication(appId))
      } yield Ok(cancelledRequestForProductionCredentialsView(request.application.name))
    )
    x.fold[Result](identity, identity)
  }
}