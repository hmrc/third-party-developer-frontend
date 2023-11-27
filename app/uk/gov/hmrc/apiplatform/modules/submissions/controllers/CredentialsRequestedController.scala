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

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.SubmissionActionBuilders.SubmissionStatusFilter
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.models.AnswersViewModel
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.models.AnswersViewModel._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{AskWhen, Submission}
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.CanUseCheckActions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

object CredentialsRequestedController {

  case class CredentialsRequestedViewModel(
      appId: ApplicationId,
      appName: String,
      subscriptions: Seq[APISubscriptionStatus],
      sellResellOrDistribute: String,
      isNewTermsOfUseUplift: Boolean,
      answersViewModel: AnswersViewModel.ViewModel
    )
}

@Singleton
class CredentialsRequestedController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val apmConnector: ApmConnector,
    val submissionService: SubmissionService,
    credentialsRequestedView: CredentialsRequestedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with CanUseCheckActions
    with EitherTHelper[String]
    with SubmissionActionBuilders {

  import SubmissionActionBuilders.{ApplicationStateFilter, RoleFilter}
  import cats.instances.future.catsStdInstancesForFuture
  import CredentialsRequestedController.CredentialsRequestedViewModel

  val redirectToGetProdCreds = (applicationId: ApplicationId) => Redirect(routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))

  /*, Read/Write and State details */
  def credentialsRequestedPage(productionAppId: ApplicationId) = withApplicationAndSubmissionInSpecifiedState(
    ApplicationStateFilter.pendingApprovalOrProduction,
    RoleFilter.isTeamMember,
    SubmissionStatusFilter.submittedGrantedOrDeclined
  )(redirectToGetProdCreds(productionAppId))(productionAppId) { implicit request =>
    val failed = (err: String) => BadRequestWithErrorMessage(err)

    val success = (viewModel: CredentialsRequestedViewModel) => {
      val err = request.msgRequest.flash.get("error")
      Ok(credentialsRequestedView(viewModel, err))
    }

    def convertToViewModel(application: Application, submission: Submission, subscriptions: List[APISubscriptionStatus], answersViewModel: ViewModel): CredentialsRequestedViewModel = {
      val inHouse                = submission.context.get(AskWhen.Context.Keys.IN_HOUSE_SOFTWARE)
      val sellResellOrDistribute = if (inHouse.contains("No")) "Yes" else "No"
      val selectedSubscriptions  = subscriptions.filter(s => s.subscribed)
      val isNewTermsOfUseUplift  = application.state.name.isProduction
      CredentialsRequestedViewModel(application.id, application.name, selectedSubscriptions, sellResellOrDistribute, isNewTermsOfUseUplift, answersViewModel)
    }

    val vm = for {
      extSubmission   <- fromOptionF(submissionService.fetchLatestExtendedSubmission(productionAppId), "No submission and/or application found")
      answersViewModel = convertSubmissionToViewModel(extSubmission)(request.application.id, request.application.name)
      viewModel        = convertToViewModel(request.application, extSubmission.submission, request.subscriptions, answersViewModel)
    } yield viewModel

    vm.fold[Result](failed, success)
  }
}
