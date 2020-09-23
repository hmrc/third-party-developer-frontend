/*
 * Copyright 2020 HM Revenue & Customs
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
import domain.models.connectors.ExtendedAPIDefinition
import javax.inject.Inject
import model.APICategoryDetails
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{APIService, SessionService}
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import scala.concurrent.ExecutionContext

class EmailPreferences @Inject()(val sessionService: SessionService,
                                 mcc: MessagesControllerComponents,
                                 val errorHandler: ErrorHandler,
                                 val cookieSigner: CookieSigner,
                                 apiService: APIService,
                                 emailPreferencesSummaryView: EmailPreferencesSummaryView)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController(mcc) {

  def emailPreferencesSummaryPage: Action[AnyContent] = loggedInAction { implicit request =>
    val emailPreferences = request.developerSession.developer.emailPreferences
    val userServices: Set[String] = emailPreferences.interests.flatMap(_.services).toSet

    for {
      apiCategoryDetails <- apiService.fetchAllAPICategoryDetails()
      apiNames <- apiService.fetchAPIDetails(userServices)
      //apis <- apiService.fetchAPIDetails(userServices)
      // get list of apis from email prefs
      // for each call apm to get extended service names etc
      // pass this list to view
      // Map(serviceName:String, displayName:String) 
    } yield Ok(emailPreferencesSummaryView(toDataObject(emailPreferences, apiNames, apiCategoryDetails)))
  }

  def toDataObject(emailPreferences: model.EmailPreferences,
                   filteredAPIs: Seq[ExtendedAPIDefinition],
                   categories: Seq[APICategoryDetails]): EmailPreferencesSummaryViewData =
    EmailPreferencesSummaryViewData(
      createCategoryMap(categories, emailPreferences.interests.map(_.regime)),
      filteredAPIs.map(a => (a.serviceName, a.name)).toMap)

  def createCategoryMap(apisCategories: Seq[APICategoryDetails], usersCategories: Seq[String]): Map[String, String] = {
    apisCategories
      .filter(category => usersCategories.contains(category.category))
      .map(c => (c.category, c.name))
      .toMap
  }

}
