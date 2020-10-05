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
import domain.models.connectors.ExtendedApiDefinition.toApiDefinition
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import domain.models.developers.DeveloperSession
import javax.inject.Inject
import model.APICategoryDetails
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{APIService, EmailPreferencesService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferences @Inject()(val sessionService: SessionService,
                                 mcc: MessagesControllerComponents,
                                 val errorHandler: ErrorHandler,
                                 val cookieSigner: CookieSigner,
                                 emailPreferencesService: EmailPreferencesService,
                                 apiService: APIService,
                                 emailPreferencesSummaryView: EmailPreferencesSummaryView,
                                 emailPreferencesUnsubscribeAllView: EmailPreferencesUnsubscribeAllView,
                                 flowStartView: FlowStartView,
                                 flowSelectCategoriesView: FlowSelectCategoriesView,
                                 flowSelectApiView: FlowSelectApiView,
                                 flowSelectTopicsview: FlowSelectTopicsView)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController(mcc) {


  def flowStartPage: Action[AnyContent] = loggedInAction { implicit request =>
    // update cache with whats in the session if its not empty
    Future.successful(Ok(flowStartView()))
  }

  def flowSelectCategoriesPage: Action[AnyContent] = loggedInAction { implicit request =>
    
    for {
      visibleCategories <- fetchCategoriesVisibleToUser(request.developerSession)
      filteredCategories <- apiService.fetchAllAPICategoryDetails().map(_.filter(x => visibleCategories.contains(x.category)))
      cacheItem <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
    } yield Ok(flowSelectCategoriesView(filteredCategories.toList, cacheItem))
  }

  private def fetchCategoriesVisibleToUser(session: DeveloperSession)(implicit hc: HeaderCarrier) = {
    for {
      apisDefs <- emailPreferencesService.fetchApiDefinitionsVisibleToUser(session)
      categories =if(apisDefs.nonEmpty) apisDefs.map(_.categories).reduce(_ ++ _).distinct.sorted else Seq.empty
    }yield categories
  }


  def flowSelectCategoriesAction: Action[AnyContent] = loggedInAction { implicit request =>

    //TODO what do we do if non are selected? for now redirect back to categories select page
    val formData = TaxRegimeEmailPreferencesForm.bindFromRequest.selectedTaxRegimes
    if (formData.isEmpty) {
          Future.successful(Redirect(controllers.routes.EmailPreferences.flowSelectCategoriesPage()))
    } else {
      for {
        flow <- emailPreferencesService.updateCategories(request.developerSession, formData)
      } yield Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(flow.categoriesInOrder.head))
    }
  }

  def flowSelectApisPage(currentCategory: String): Action[AnyContent] = loggedInAction { implicit  request =>
    // get apis for category
    for{
      cacheItem <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
      filteredAPisByCategory = cacheItem.visibleApis.filter(_.categories.contains(currentCategory))
      selectedApis = cacheItem.selectedAPIs.get(currentCategory).getOrElse(Set.empty)
    } yield Ok(flowSelectApiView(currentCategory, filteredAPisByCategory, selectedApis))

  }

  def flowSelectApisAction: Action[AnyContent] = loggedInAction { implicit request =>
    // Parse form & update cache
    // If there are more categories to display redirect back to Select APIs page
    // Otherwise redirect to topics page
    val requestForm: SelectedApisEmailPreferencesForm  = SelectedApisEmailPreferencesForm.bindFromRequest
    // TODO Hanlde None are selected.... do we need an ALL APIS checkbox?
    requestForm.selectedApis.foreach(x => println(s"selected API $x"))
    emailPreferencesService.updateSelectedApis(request.developerSession, requestForm.currentCategory,  requestForm.selectedApis)
    def handleNextPage(sortedCategories: List[String], currentCategoryIndex: Int) = {
      if (sortedCategories.size == currentCategoryIndex + 1) {
        Redirect(controllers.routes.EmailPreferences.flowSelectTopicsPage())
      } else {
        val nextCategory: String = sortedCategories(currentCategoryIndex + 1)
       Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(nextCategory))
      }
    }

    for{
      emailpreferences <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
      sortedCategories = emailpreferences.selectedCategories.toList.sorted
      currentCategoryIndex = sortedCategories.indexOf(requestForm.currentCategory)
    } yield    handleNextPage(sortedCategories, currentCategoryIndex)


  }

  def flowSelectTopicsPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(flowSelectTopicsview(Set.empty)))
  }

  def flowSelectTopicsAction: Action[AnyContent] = loggedInAction { implicit request =>
    // val requestForm: TaxRegimeEmailPreferencesForm = TaxRegimeEmailPreferencesForm.bindFromRequest

    // Persist Email Preferences changes to TPD
    Future.successful(Redirect(routes.EmailPreferences.emailPreferencesSummaryPage()))
  }

  def emailPreferencesSummaryPage(): Action[AnyContent] = loggedInAction { implicit request =>
    val unsubscribed: Boolean = request.flash.get("unsubscribed") match {
      case Some("true") => true
      case _ => false
    }

    val emailPreferences = request.developerSession.developer.emailPreferences
    if (emailPreferences.equals(model.EmailPreferences.noPreferences) && !unsubscribed) {
      Future.successful(Redirect(controllers.routes.EmailPreferences.flowStartPage()))
    } else {
      val userServices: Set[String] = emailPreferences.interests.flatMap(_.services).toSet
      for {
        apiCategoryDetails <- apiService.fetchAllAPICategoryDetails()
        apiNames <- apiService.fetchAPIDetails(userServices)
      } yield Ok(emailPreferencesSummaryView(toDataObject(emailPreferences, apiNames, apiCategoryDetails, unsubscribed)))
    }
  }

  def unsubscribeAllPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(emailPreferencesUnsubscribeAllView()))
  }

  def unsubscribeAllAction: Action[AnyContent] = loggedInAction { implicit request =>
    emailPreferencesService.removeEmailPreferences(request.developerSession.developer.email).map {
      case true => Redirect(routes.EmailPreferences.emailPreferencesSummaryPage()).flashing("unsubscribed" -> "true")
      case false => Redirect(routes.EmailPreferences.emailPreferencesSummaryPage())
    }
  }

  def toDataObject(emailPreferences: model.EmailPreferences,
                   filteredAPIs: Seq[ExtendedApiDefinition],
                   categories: Seq[APICategoryDetails],
                   unsubscribed: Boolean): EmailPreferencesSummaryViewData =
    EmailPreferencesSummaryViewData(
      createCategoryMap(categories, emailPreferences.interests.map(_.regime)),
      filteredAPIs.map(a => (a.serviceName, a.name)).toMap, unsubscribed)

  def createCategoryMap(apisCategories: Seq[APICategoryDetails], usersCategories: Seq[String]): Map[String, String] = {
    apisCategories
      .filter(category => usersCategories.contains(category.category))
      .map(c => (c.category, c.name))
      .toMap
  }

}
