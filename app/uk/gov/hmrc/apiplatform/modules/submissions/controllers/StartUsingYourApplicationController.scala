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
import scala.concurrent.Future.successful

import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.StartUsingYourApplicationController.ViewModel
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.CanUseCheckActions
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.SupportsSubscriptions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.AdministratorOnly
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

object StartUsingYourApplicationController {
  case class ViewModel(appId: ApplicationId, appName: String, showApiConfig: Boolean)
}

@Singleton
class StartUsingYourApplicationController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val apmConnector: ApmConnector,
    val submissionService: SubmissionService,
    startUsingYourApplicationView: StartUsingYourApplicationView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with CanUseCheckActions
    with EitherTHelper[String]
    with SubmissionActionBuilders {

  def startUsingYourApplicationPage(productionAppId: ApplicationId) = checkActionForPreProduction(SupportsSubscriptions, AdministratorOnly)(productionAppId) { implicit request =>
    successful(Ok(startUsingYourApplicationView(ViewModel(productionAppId, request.application.name, request.hasSubscriptionFields))))
  }

  def startUsingYourApplicationAction(productionAppId: ApplicationId) = checkActionForPreProduction(SupportsSubscriptions, AdministratorOnly)(productionAppId) { implicit request =>
    val userEmail = request.developerSession.developer.email
    val failure   = BadRequest(errorHandler.badRequestTemplate)
    val success   = Redirect(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.ManageApplications.manageApps)
    submissionService.confirmSetupComplete(productionAppId, userEmail).map((esu: Either[String, Unit]) => esu.fold(_ => failure, _ => success))
  }
}
