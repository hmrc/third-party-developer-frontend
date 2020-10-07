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

import cats.data.NonEmptyList
import config.{ApplicationConfig, ErrorHandler}
import domain.models.connectors.ExtendedApiDefinition
import domain.models.emailpreferences.APICategoryDetails
import javax.inject.Inject
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{EmailPreferencesService, SessionService}
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import scala.concurrent.{ExecutionContext, Future}

class EmailPreferences @Inject()(val sessionService: SessionService,
                                 mcc: MessagesControllerComponents,
                                 val errorHandler: ErrorHandler,
                                 val cookieSigner: CookieSigner,
                                 emailPreferencesService: EmailPreferencesService,
                                 emailPreferencesSummaryView: EmailPreferencesSummaryView,
                                 emailPreferencesUnsubscribeAllView: EmailPreferencesUnsubscribeAllView,
                                 flowStartView: FlowStartView,
                                 flowSelectCategoriesView: FlowSelectCategoriesView,
                                 flowSelectApiView: FlowSelectApiView,
                                 flowSelectTopicsview: FlowSelectTopicsView)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController(mcc) {


  def flowStartPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(flowStartView()))
  }

  def flowSelectCategoriesPage: Action[AnyContent] = loggedInAction { implicit request =>
    for {
      cacheItem <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
      visibleCategories <- emailPreferencesService.fetchCategoriesVisibleToUser(request.developerSession)
    } yield Ok(flowSelectCategoriesView(visibleCategories.toList, cacheItem.selectedCategories))
  }

  def flowSelectCategoriesAction: Action[AnyContent] = loggedInAction { implicit request =>
    //TODO what do we do if non are selected? for now redirect back to categories select page
    NonEmptyList.fromList(TaxRegimeEmailPreferencesForm.bindFromRequest.selectedTaxRegimes)
      .fold(Future.successful(Redirect(controllers.routes.EmailPreferences.flowSelectCategoriesPage()))) { categories =>
        for {
          flow <- emailPreferencesService.updateCategories(request.developerSession, categories.toList)
        } yield Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(flow.categoriesInOrder.head))
      }
  }

  def flowSelectApisPage(category: String): Action[AnyContent] = loggedInAction { implicit request =>
    val form = SelectedApisEmailPreferencesForm.form
    if (category.isEmpty) {
      Future.successful(Redirect(controllers.routes.EmailPreferences.emailPreferencesSummaryPage()))
    } else {
      flowSelectApisView(form, category).map(Ok(_))
    }
  }

  private def flowSelectApisView(form: Form[SelectedApisEmailPreferencesForm], category: String)(implicit request: UserRequest[AnyContent]) = {
    for {
      categoryDetails <- emailPreferencesService.apiCategoryDetails(category)
      flow <- emailPreferencesService.fetchFlowBySessionId(request.developerSession)
    } yield flowSelectApiView(form, categoryDetails.getOrElse(APICategoryDetails(category, category)), flow.visibleApisByCategory(category), flow.selectedApisByCategory(category))
  }

  def flowSelectApisAction: Action[AnyContent] = loggedInAction { implicit request =>

    // TODO Handle when None are selected.... do we need an ALL APIS checkbox?
    def handleNextPage(sortedCategories: List[String], currentCategory: String) = {
      val currentCategoryIndex = sortedCategories.indexOf(currentCategory)
      if (sortedCategories.size == currentCategoryIndex + 1) {
        Redirect(controllers.routes.EmailPreferences.flowSelectTopicsPage())
      } else {
        Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(sortedCategories(currentCategoryIndex + 1)))
      }
    }

    val form = SelectedApisEmailPreferencesForm.form.bindFromRequest
    form.fold(
      formWithErrors => {
        flowSelectApisView(formWithErrors, form.data.getOrElse("currentCategory", "")).map(BadRequest(_))
      },
      {
        form =>
          emailPreferencesService
            .updateSelectedApis(request.developerSession, form.currentCategory, form.selectedApi.toList)
            .map(flow => handleNextPage(flow.categoriesInOrder, form.currentCategory))
      }
    )


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
    if (emailPreferences.equals(domain.models.emailpreferences.EmailPreferences.noPreferences) && !unsubscribed) {
      Future.successful(Redirect(controllers.routes.EmailPreferences.flowStartPage()))
    } else {
      val userServices: Set[String] = emailPreferences.interests.flatMap(_.services).toSet
      for {
        apiCategoryDetails <- emailPreferencesService.fetchAllAPICategoryDetails()
        apiNames <- emailPreferencesService.fetchAPIDetails(userServices)
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

  def toDataObject(emailPreferences: domain.models.emailpreferences.EmailPreferences,
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
