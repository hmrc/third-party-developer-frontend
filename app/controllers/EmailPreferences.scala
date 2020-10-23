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
import play.api.Logger
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.Html
import service.{EmailPreferencesService, SessionService}
import views.emailpreferences.EmailPreferencesSummaryViewData
import views.html.emailpreferences._

import scala.concurrent.Future.successful
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
                                 flowSelectTopicsView: FlowSelectTopicsView)
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig) extends LoggedInController(mcc) {


  def flowStartPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(flowStartView()))
  }

  def flowSelectCategoriesPage: Action[AnyContent] = loggedInAction { implicit request =>
    flowShowSelectCategoriesView(TaxRegimeEmailPreferencesForm.form).map(Ok(_))
  }

  private def flowShowSelectCategoriesView(form: Form[TaxRegimeEmailPreferencesForm])(implicit request: UserRequest[AnyContent]) = {
    for {
      flow <- emailPreferencesService.fetchFlow(request.developerSession)
      visibleCategories <- emailPreferencesService.fetchCategoriesVisibleToUser(request.developerSession)
      selectedCategories = if (form.hasErrors) Set.empty[String] else flow.selectedCategories
    } yield flowSelectCategoriesView(form, visibleCategories.toList, selectedCategories)
  }

  def flowSelectCategoriesAction: Action[AnyContent] = loggedInAction { implicit request =>

    val form = TaxRegimeEmailPreferencesForm.form.bindFromRequest
    form.fold(
      formWithErrors => {
        flowShowSelectCategoriesView(formWithErrors).map(BadRequest(_))
      },
      { form =>
        emailPreferencesService.updateCategories(request.developerSession, form.taxRegime)
          .map(flow => Redirect(controllers.routes.EmailPreferences.flowSelectApisPage(flow.categoriesInOrder.head)))
      })
  }

  def flowSelectNoCategoriesAction: Action[AnyContent] = loggedInAction { implicit request =>
    emailPreferencesService.updateCategories(request.developerSession, List.empty[String])
      .map(_ => Redirect(controllers.routes.EmailPreferences.flowSelectTopicsPage()))
  }

  def flowSelectApisPage(category: String): Action[AnyContent] = loggedInAction { implicit request =>
    val form = SelectedApisEmailPreferencesForm.form
    if (category.isEmpty) {
      Future.successful(Redirect(controllers.routes.EmailPreferences.emailPreferencesSummaryPage()))
    } else {
      flowSelectApisView(form, category).map(Ok(_))
    }
  }

  private def flowSelectApisView(form: Form[SelectedApisEmailPreferencesForm], category: String)(implicit request: UserRequest[AnyContent]): Future[Html] = {
    for {
      categoryDetails <- emailPreferencesService.apiCategoryDetails(category)
      flow <- emailPreferencesService.fetchFlow(request.developerSession)
    } yield flowSelectApiView(form, categoryDetails.getOrElse(APICategoryDetails(category, category)), flow.visibleApisByCategory(category), flow.selectedApisByCategory(category))
  }

  def flowSelectApisAction: Action[AnyContent] = loggedInAction { implicit request =>

    def handleNextPage(sortedCategories: List[String], currentCategory: String): Result = {
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
        form => {val apiList = getListFromApiForm(form)
          emailPreferencesService
            .updateSelectedApis(request.developerSession, form.currentCategory, apiList)
            .map(flow => handleNextPage(flow.categoriesInOrder, form.currentCategory))
        }
      }
    )


  }

  private def getListFromApiForm(form: SelectedApisEmailPreferencesForm): List[String] = {
    Logger.error(s"****${form.apiRadio}*****")
    if(form.apiRadio.equalsIgnoreCase("ALL_APIS")){
      List("ALL_APIS")
    }else {
      form.selectedApi.toList
    }
  }

  def flowSelectTopicsPage(): Action[AnyContent] = loggedInAction {
    implicit request =>
      emailPreferencesService.fetchFlow(request.developerSession)
        .flatMap(flow => renderFlowSelectTopicsView(selectedTopics = flow.selectedTopics))
        .map(Ok(_))
  }

  private def renderFlowSelectTopicsView(form: Form[SelectedTopicsEmailPreferencesForm] = SelectedTopicsEmailPreferencesForm.form,
                                         selectedTopics: Set[String])
                                        (implicit request: UserRequest[AnyContent]): Future[Html] =
    successful(flowSelectTopicsView.apply(form, selectedTopics))

  def flowSelectTopicsAction: Action[AnyContent] = loggedInAction { implicit request =>
    val form = SelectedTopicsEmailPreferencesForm.form.bindFromRequest

    form.fold(
      formWithErrors => {
        renderFlowSelectTopicsView(formWithErrors, Set.empty).map(BadRequest(_))
      },
      {
        selectedTopicsForm =>
          val developerSession = request.developerSession
          for {
            flow <- emailPreferencesService.fetchFlow(developerSession)
            updateResult <- emailPreferencesService
              .updateEmailPreferences(developerSession.developer.email, flow.copy(selectedTopics = selectedTopicsForm.topic.toSet))
            _ = if (updateResult) emailPreferencesService.deleteFlow(developerSession.session.sessionId)
          } yield if (updateResult) Redirect(routes.EmailPreferences.emailPreferencesSummaryPage())
          else Redirect(routes.EmailPreferences.flowSelectTopicsPage())
      }
    )
  }

  def emailPreferencesSummaryPage(): Action[AnyContent] = loggedInAction { implicit request =>
    val unsubscribed: Boolean = request.flash.get("unsubscribed") match {
      case Some("true") => true
      case _ => false
    }

    val emailPreferences = request.developerSession.developer.emailPreferences
    val userServices: Set[String] = emailPreferences.interests.flatMap(_.services).toSet
    for {
      apiCategoryDetails <- emailPreferencesService.fetchAllAPICategoryDetails()
      apiNames <- emailPreferencesService.fetchAPIDetails(userServices)
    } yield Ok(emailPreferencesSummaryView(toDataObject(emailPreferences, apiNames, apiCategoryDetails, unsubscribed)))
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
