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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import views.html.manageapplication.ApplicationDetailsView
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class ManageApplicationController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val clock: Clock,
    applicationDetailsView: ApplicationDetailsView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with FraudPreventionNavLinkHelper
    with ClockNow {

  def applicationDetails(applicationId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    successful(Ok(applicationDetailsView(applicationViewModelFromApplicationRequest(), request.subscriptions)))
  }
}
