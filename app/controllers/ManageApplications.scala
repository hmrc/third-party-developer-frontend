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

package controllers


import config.{ApplicationConfig, ErrorHandler}
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service._
import views.helper.EnvironmentNameService
import views.html._

import scala.concurrent.ExecutionContext
import domain.models.controllers.ManageApplicationsViewModel
import modules.uplift.services.UpliftLogic

@Singleton
class ManageApplications @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,

    appsByTeamMember: AppsByTeamMemberService,
    upliftLogic: UpliftLogic,
    
    manageApplicationsView: ManageApplicationsView,
    addApplicationSubordinateEmptyNestView: AddApplicationSubordinateEmptyNestView,
    mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig, val environmentNameService: EnvironmentNameService)
   extends LoggedInController(mcc) {

  def manageApps: Action[AnyContent] = loggedInAction { implicit request =>
    for {
      upliftData                  <- upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(request.userId)
      sandboxApplicationSummaries = upliftData.sandboxApplicationSummaries
      upliftableApplicationIds    = upliftData.upliftableApplicationIds
      
      productionAppSummaries <- appsByTeamMember.fetchProductionSummariesByTeamMember(request.userId)
    } yield (sandboxApplicationSummaries, productionAppSummaries) match {
      case (Nil, Nil) => Ok(addApplicationSubordinateEmptyNestView())
      case _ =>         Ok(manageApplicationsView(
          ManageApplicationsViewModel(sandboxApplicationSummaries, productionAppSummaries, upliftableApplicationIds)
        ))
    }
  }

}
