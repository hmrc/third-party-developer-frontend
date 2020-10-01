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
      categories <- fetchCategoriesVisibleToUser()
      cacheItem <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
    } yield Ok(flowSelectCategoriesView(categories, cacheItem))
  }

  private def fetchCategoriesVisibleToUser()(implicit hc: HeaderCarrier): Future[List[APICategoryDetails]] = {
    apiService.fetchAllAPICategoryDetails().map(_.toList.sortBy(_.name))
  }


  def flowSelectCategoriesAction: Action[AnyContent] = loggedInAction { implicit request =>
    for{
     flow <- emailPreferencesService.updateCategories(request.developerSession, TaxRegimeEmailPreferencesForm.bindFromRequest.selectedTaxRegimes)
    } yield Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(flow.categoriesInOrder.head))
  }

  def flowSelectApisPage(currentCategory: String): Action[AnyContent] = loggedInAction { implicit  request =>
    // get apis for category
    
    Future.successful(Ok(flowSelectApiView(currentCategory)))

  }

  def flowSelectApisAction: Action[AnyContent] = loggedInAction { implicit request =>
    // Parse form & update cache
    // If there are more categories to display redirect back to Select APIs page
    // Otherwise redirect to topics page
    val emailpreferences = request.developerSession.developer.emailPreferences
    val requestForm: Form[SelectedApisEmailPreferencesForm]  = SelectedApisEmailPreferencesForm.form.bindFromRequest
    val sortedCategories =  emailpreferences.interests.map(_.regime).toList.sorted
    val currentCategoryIndex : Int = sortedCategories.indexOf(requestForm.value.head.currentCategory)

    // is current category last category?
    if(sortedCategories.size == currentCategoryIndex+1) {
       Future.successful(Redirect(controllers.routes.EmailPreferences.flowSelectTopicsPage()))
    }else{
      val nextCategory: String = sortedCategories(currentCategoryIndex+1)
      Future.successful(Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(nextCategory)))
    }
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
                   filteredAPIs: Seq[ExtendedAPIDefinition],
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
