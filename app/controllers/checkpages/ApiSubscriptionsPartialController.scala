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

package controllers.checkpages

import controllers.{APISubscriptions, ApplicationController, ApplicationRequest}
import domain.models
import domain.models.applications.{Application, ApplicationId, CheckInformation}
import domain.models.controllers.SubscriptionData
import domain.models.apidefinitions.ApiContext
import domain.models.subscriptions.ApiData
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}
import views.html.checkpages.ApiSubscriptionsView

import scala.concurrent.Future


trait ApiSubscriptionsPartialController {
  self: ApplicationController with CanUseCheckActions =>

  val apiSubscriptionsViewTemplate: ApiSubscriptionsView

  private def asSubscriptionData(applicationRequest: ApplicationRequest[AnyContent]) =
    models.controllers.SubscriptionData(applicationRequest.role, applicationRequest.application, APISubscriptions.groupSubscriptions(applicationRequest.subscriptions))

  def apiSubscriptionsPage(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    Future.successful(Ok(apiSubscriptionsView(app, asSubscriptionData(request), request.openAccessApis)))
  }

  def apiSubscriptionsAction(appId: ApplicationId): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val subscriptionData = asSubscriptionData(request)
    val openAccessApis = request.openAccessApis
    val information = app.checkInformation.getOrElse(CheckInformation())

    // Grouped subscriptons removed API-EXAMPLE-MICROSERVICE before this code is ever executed
    def hasNonExampleSubscription(subscriptionData: SubscriptionData) =
      subscriptionData.subscriptions.fold(false)(subs => subs.apis.exists(_.hasSubscriptions))

    if (!hasNonExampleSubscription(subscriptionData)) {
      val form = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
      Future.successful(BadRequest(apiSubscriptionsView(app, subscriptionData, openAccessApis, Some(form))))
    } else {
      for {
        _ <- applicationService.updateCheckInformation(app, information.copy(apiSubscriptionsConfirmed = true))
      } yield Redirect(landingPageRoute(app.id))
    }
  }

  private def apiSubscriptionsView(
      app: Application,
      subscriptionData: SubscriptionData,
      openAccessApis: Map[ApiContext, ApiData],
      form: Option[Form[DummySubscriptionsForm]] = None
  )(implicit request: ApplicationRequest[AnyContent]) = {
    apiSubscriptionsViewTemplate(
      app,
      subscriptionData.role,
      subscriptionData.subscriptions,
      openAccessApis,
      app.id,
      apiSubscriptionsActionRoute(app.id),
      form
    )
  }

  protected def landingPageRoute(appId: ApplicationId): Call
  protected def apiSubscriptionsActionRoute(appId: ApplicationId): Call
}
