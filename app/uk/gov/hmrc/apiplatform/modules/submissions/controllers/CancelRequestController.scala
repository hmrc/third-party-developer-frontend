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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.{ClockNow, EitherTHelper}
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.{CancelledRequestForProductionCredentialsView, ConfirmCancelRequestForProductionCredentialsView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApplicationCommandConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

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
    appCmdConnector: ApplicationCommandConnector,
    confirmCancelRequestForProductionCredentialsView: ConfirmCancelRequestForProductionCredentialsView,
    cancelledRequestForProductionCredentialsView: CancelledRequestForProductionCredentialsView,
    val clock: Clock
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with EitherTHelper[String]
    with ClockNow
    with SubmissionActionBuilders {

  import cats.instances.future.catsStdInstancesForFuture
  import SubmissionActionBuilders.ApplicationStateFilter

  private val exec   = ec
  private val ET     = new EitherTHelper[Result] { implicit val ec: ExecutionContext = exec }
  private val failed = (err: String) => BadRequestWithErrorMessage(err)

  def cancelRequestForProductionCredentialsPage(appId: ApplicationId) = withApplicationSubmission(ApplicationStateFilter.notProduction)(appId) { implicit request =>
    Future.successful(Ok(confirmCancelRequestForProductionCredentialsView(appId, CancelRequestController.DummyForm.form)))
  }

  def cancelRequestForProductionCredentialsAction(appId: ApplicationId) = withApplicationSubmission(ApplicationStateFilter.notProduction)(appId) { implicit request =>
    lazy val goBackToRegularPage =
      if (request.submissionRequest.extSubmission.submission.status.isAnsweredCompletely) {
        Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(appId))
      } else {
        Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(appId))
      }

    val isValidSubmit: (String) => Boolean = (s) => s == "cancel-request" || s == "dont-cancel-request"
    val formValues                         = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    val reasons                            = "DevHub user cancelled request for production credentials"
    val instigator                         = request.userId
    val deleteRequest                      = ApplicationCommands.DeleteApplicationByCollaborator(instigator, reasons, instant())

    val x =
      (
        for {
          submitAction <- ET.fromOption(
                            formValues.get("submit-action")
                              .flatMap(_.headOption)
                              .filter(isValidSubmit),
                            failed("Bad form data")
                          )
          cancelAction <- ET.cond(submitAction == "cancel-request", submitAction, goBackToRegularPage)
          _            <- ET.liftF(appCmdConnector.dispatchWithThrow(appId, deleteRequest, Set.empty))
        } yield Ok(cancelledRequestForProductionCredentialsView(request.application.name))
      )
    x.fold[Result](identity, identity)
  }
}
