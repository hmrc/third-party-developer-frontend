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

package controllers


import config.{ApplicationConfig, ErrorHandler}
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service._
import views.helper.EnvironmentNameService
import views.html._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import domain.models.controllers.ManageApplicationsViewModel

@Singleton
class ManageApplications @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,

    appsByTeamMember: AppsByTeamMemberService,
    upliftDataService: UpliftDataService,
    
    manageApplicationsView: ManageApplicationsView,
    addApplicationSubordinateEmptyNestView: AddApplicationSubordinateEmptyNestView,
    mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig, val environmentNameService: EnvironmentNameService) 
   extends LoggedInController(mcc) {

  def manageApps: Action[AnyContent] = loggedInAction { implicit request =>
    appsByTeamMember.fetchAllSummariesByTeamMember(loggedIn.developer.userId) flatMap { 
      case (Nil, Nil)                                                    => successful(Ok(addApplicationSubordinateEmptyNestView()))
      case (sandboxApplicationSummaries, productionApplicationSummaries) => 
        val appIds = sandboxApplicationSummaries.filter(_.role.isAdministrator).map(_.id)
        upliftDataService.identifyUpliftableSandboxAppIds(appIds).map { upliftableApplicationIds =>
          Ok(manageApplicationsView(
            ManageApplicationsViewModel(sandboxApplicationSummaries, productionApplicationSummaries, upliftableApplicationIds)
          ))
        }
    }
  }

}
