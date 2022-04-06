/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Result}
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.StartUsingYourApplicationController.ViewModel
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.{RequestProductionCredentials, SubmissionService}
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.CanUseCheckActions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

object StartUsingYourApplicationController {
  case class ViewModel(appId: ApplicationId, appName: String, showApiConfig: Boolean)
}

@Singleton
class StartUsingYourApplicationController @Inject()(
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val apmConnector: ApmConnector,
    val submissionService: SubmissionService,
    startUsingYourApplicationView: StartUsingYourApplicationView)
      (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions
     with EitherTHelper[String]
     with SubmissionActionBuilders {

  import SubmissionActionBuilders.{ApplicationStateFilter, RoleFilter}
52677.52
  def startUsingYourApplicationPage(productionAppId: ApplicationId) = withApplicationSubmission(ApplicationStateFilter.preProduction, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    val showApiConfig = request.subscriptions.exists(s => s.subscribed && s.fields.fields.nonEmpty)
    successful(Ok(startUsingYourApplicationView(ViewModel(request.application.id, request.application.name, showApiConfig))))
  }

  def startUsingYourApplicationAction(productionAppId: ApplicationId) = withApplicationSubmission(ApplicationStateFilter.preProduction, RoleFilter.isAdminRole)(productionAppId) { implicit request =>
    val userEmail = request.developerSession.developer.email
    val failure = BadRequest(errorHandler.badRequestTemplate)
    val success = Redirect(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.Details.details(productionAppId))
    submissionService.confirmSetupComplete(productionAppId, userEmail).map((esu: Either[String,Unit]) => esu.fold(_ => failure, _ => success))
  }
}
