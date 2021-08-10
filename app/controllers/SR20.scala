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
import domain.models
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{ApplicationActionService, ApplicationService, SessionService}
import views.html.{ConfirmApisView, TurnOffApisView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SR20 @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      confirmApisView: ConfirmApisView,
                      turnOffApisView: TurnOffApisView,
                      val apmConnector: ApmConnector)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions{
       
  def confirmApiSubscriptions(upliftedAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>

    for {
      allApis <- apmConnector.fetchAllApis(models.applications.Environment.SANDBOX)
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(upliftedAppId)
    }
    yield {
      val upliftableSubscriptionsWithData = upliftableSubscriptions
        .flatMap(upliftableSubscription => allApis
        .get(upliftableSubscription.context)
        .map { apiData => (apiData.name, upliftableSubscription.version.value) })

      Ok(confirmApisView(upliftedAppId, upliftableSubscriptionsWithData))
    }
  }

  def confirmApiSubscriptionsAction(upliftedAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(upliftedAppId)))
  }

  def changeApiSubscriptions(upliftedAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(upliftedAppId) { implicit request =>
    val subscriptionData = APISubscriptions.groupSubscriptions(request.subscriptions.filter(_.subscribed))
    Future.successful(Ok(turnOffApisView(request.application, request.role, subscriptionData)))
  }

  def changeApiSubscriptionsAction(upliftedAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(upliftedAppId) { implicit request =>

    val subscriptionData = APISubscriptions.groupSubscriptions(request.subscriptions.filter(_.subscribed))

    if (subscriptionData.isEmpty) {
      val errorForm = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
      Future.successful(Ok(turnOffApisView(request.application, request.role, subscriptionData, Some(errorForm))))
    }
    else
      Future.successful(Redirect(controllers.routes.SR20.confirmApiSubscriptions(upliftedAppId)))
  }
}
