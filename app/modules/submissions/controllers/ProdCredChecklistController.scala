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

import config.{ApplicationConfig, ErrorHandler}
import connectors.ApmConnector
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service.{ApplicationActionService, ApplicationService, SessionService}
import modules.submissions.views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext
import controllers.ApplicationController
import controllers.checkpages.CanUseCheckActions
import cats.data.NonEmptyList
import modules.questionnaires.domain.models.QuestionnaireId

object ProdCredsChecklistController {
  case class ViewQuestionnaireSummary(label: String, state: String, id: QuestionnaireId = QuestionnaireId.random)
  case class ViewGrouping(label: String, questionnaireSummaries: NonEmptyList[ViewQuestionnaireSummary])
  case class ViewModel(appName: String, groupings: NonEmptyList[ViewGrouping])
}

@Singleton
class ProdCredsChecklistController @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      val apmConnector: ApmConnector,
                      productionCredentialsChecklistView: ProductionCredentialsChecklistView)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions {

  import ProdCredsChecklistController._

  def productionCredentialsChecklist(productionAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(productionAppId) { implicit request =>
    val fixedViewModel = 
      ViewModel(
        request.application.name,
        NonEmptyList.of(
          ViewGrouping("About your processes", 
            NonEmptyList.of(
              ViewQuestionnaireSummary("Development practices", "In Progress"),
              ViewQuestionnaireSummary("Service management practices", "Not Started")
            )
          ),
          ViewGrouping("About your software", 
            NonEmptyList.of(
              ViewQuestionnaireSummary("Handling personal data", "Not Started"),
              ViewQuestionnaireSummary("Customers authorising your software", "Not Started"),
              ViewQuestionnaireSummary("Software security", "Not Started")
            )
          ),
          ViewGrouping("About your organisation", 
            NonEmptyList.of(
              ViewQuestionnaireSummary("Organisation details", "Not Started"),
              ViewQuestionnaireSummary("Marketing your software", "Not Started")            
            )
          ),
        )
      )
    successful(Ok(productionCredentialsChecklistView(fixedViewModel)))
  }
}
