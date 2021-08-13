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
import connectors.ApmConnector
import controllers.checkpages.{CanUseCheckActions, DummySubscriptionsForm}
import domain.models.apidefinitions.ApiContext
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{ApplicationActionService, ApplicationService, SessionService}
import views.helper.IdFormatter
import views.html.{ConfirmApisView, TurnOffApisMasterView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import controllers.models.ApiSubscriptionsFlow
import scala.concurrent.Future
import domain.models.apidefinitions.APISubscriptionStatus

@Singleton
class SR20 @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      confirmApisView: ConfirmApisView,
                      turnOffApisMasterView:TurnOffApisMasterView,
                      val apmConnector: ApmConnector)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions{
       
def confirmApiSubscriptionsPage(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    val flow = ApiSubscriptionsFlow.fromSessionString(request.session.get("subscriptions").getOrElse(""))

    def getApiNameForContext(apiContext: ApiContext) =
      request.subscriptions
      .find(_.context == apiContext )
      .map(_.name)

    for {
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
    }
    yield {
      val data = (for {
        subscription <- upliftableApiIds.filter(flow.isSelected)
        name <- getApiNameForContext(subscription.context)
      }
      yield {
        s"$name - ${subscription.version.value}"
      })
      Ok(confirmApisView(sandboxAppId, data, upliftableApiIds.size > 1))
    }
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    val flow = ApiSubscriptionsFlow.fromSessionString(request.session.get("subscriptions").getOrElse(""))
    
    for {
      apiIdsToUnsubscribeFrom <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map(_.filterNot(flow.isSelected))
      // TODO - make upliftApplication take subscription Ids
      upliftedAppId <- apmConnector.upliftApplication(sandboxAppId)
      upliftedApplication <- apmConnector.fetchApplicationById(upliftedAppId).map(_.get)  // NB - we really should find this app
      unsubscribing <- Future.sequence(apiIdsToUnsubscribeFrom.map(id => applicationService.unsubscribeFromApi(upliftedApplication.application, id)))
    } yield {
      Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(upliftedAppId)).withSession(request.session - "subscriptions")
    }
  }

  def setSubscribedStatusFromFlow(flow: ApiSubscriptionsFlow)(apiSubscription: APISubscriptionStatus): APISubscriptionStatus = {
    apiSubscription.copy(subscribed = flow.isSelected(apiSubscription.apiIdentifier))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    val flow = ApiSubscriptionsFlow.fromSessionString(request.session.get("subscriptions").getOrElse(""))
    
    for {
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      selectedApiIds = upliftableApiIds.filter(flow.isSelected)
      subscriptionsWithFlowAdjusted = request.subscriptions
        .filter(s => upliftableApiIds.contains(s.apiIdentifier))
        .map(setSubscribedStatusFromFlow(flow))
    } yield {
      Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(subscriptionsWithFlowAdjusted)))
    }
  }

  def saveApiSubscriptionsSubmit(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    lazy val flow = ApiSubscriptionsFlow.fromSessionString(request.session.get("subscriptions").getOrElse(""))

    lazy val formSubmittedSubscriptions: Map[String, Boolean] = 
      request.body.asFormUrlEncoded.get
      .filter(_._1.contains("subscribed"))
      .mapValues(_.head == "true")
      .map {
        case (name, isSubscribed) => (name.replace("-subscribed", "") -> isSubscribed)
      }

    apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map { upliftableApiIds =>
      val apiLookups = upliftableApiIds.map( id => IdFormatter.identifier(id) -> id ).toMap
      val newFlow = ApiSubscriptionsFlow(formSubmittedSubscriptions.map {
        case (id, onOff) => apiLookups(id) -> onOff
      })

      if (formSubmittedSubscriptions.exists(_._2 == true)) {
        Redirect(controllers.routes.SR20.confirmApiSubscriptionsPage(sandboxAppId))
          .withSession(request.session + ("subscriptions" -> ApiSubscriptionsFlow.toSessionString(newFlow)))
      }
      else {
        val errorForm = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
        val sandboxSubscribedApis = request.subscriptions
            .filter(s => upliftableApiIds.contains(s.apiIdentifier))
            .map(setSubscribedStatusFromFlow(newFlow))

        Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(sandboxSubscribedApis), Some(errorForm)))
        .withSession(request.session + ("subscriptions" -> ApiSubscriptionsFlow.toSessionString(flow)))
      }
    }
  }
}
