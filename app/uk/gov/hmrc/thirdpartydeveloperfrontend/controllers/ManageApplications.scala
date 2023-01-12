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

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.apiplatform.modules.uplift.services.UpliftLogic
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ManageApplicationsViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.LocalDateTimeFormatters
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import views.helper.EnvironmentNameService
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageApplications @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,
    appsByTeamMember: AppsByTeamMemberService,
    upliftLogic: UpliftLogic,
    manageApplicationsView: ManageApplicationsView,
    mcc: MessagesControllerComponents
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig,
    val environmentNameService: EnvironmentNameService
  ) extends LoggedInController(mcc) with LocalDateTimeFormatters {

  def manageApps: Action[AnyContent] = loggedInAction { implicit request =>
    for {
      upliftData                 <- upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(request.userId)
      sandboxApplicationSummaries = upliftData.sandboxApplicationSummaries
      upliftableApplicationIds    = upliftData.upliftableApplicationIds
      productionAppSummaries     <- appsByTeamMember.fetchProductionSummariesByTeamMember(request.userId)
    } yield (sandboxApplicationSummaries, productionAppSummaries) match {
      case (Nil, Nil) => Redirect(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.noapplications.routes.NoApplications.noApplicationsPage)
      case _          => Ok(manageApplicationsView(
          ManageApplicationsViewModel(sandboxApplicationSummaries, productionAppSummaries, upliftableApplicationIds, upliftData.hasAppsThatCannotBeUplifted)
        ))
    }
  }

}
