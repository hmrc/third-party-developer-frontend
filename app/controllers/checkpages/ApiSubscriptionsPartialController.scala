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

package controllers.checkpages

import controllers.{ApplicationController, ApplicationRequest}
import domain.{CheckInformation, SubscriptionData, _}
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Call}

import scala.concurrent.Future
import controllers.APISubscriptions

trait ApiSubscriptionsPartialController  {
  self: ApplicationController with CanUseCheckActions =>

  private def asSubscriptionData(applicationRequest: ApplicationRequest[AnyContent]) =
    SubscriptionData(applicationRequest.role, applicationRequest.application, APISubscriptions.groupSubscriptions(applicationRequest.subscriptions))

  def apiSubscriptionsPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    Future.successful(Ok(apiSubscriptionsView(app, asSubscriptionData(request))))
  }

  def apiSubscriptionsAction(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    val app = request.application
    val subscriptionData = asSubscriptionData(request)
    val information = app.checkInformation.getOrElse(CheckInformation())

    // Grouped subscriptons removed API-EXAMPLE-MICROSERVICE before this code is ever executed
    def hasNonExampleSubscription(subscriptionData: SubscriptionData) =
      subscriptionData.subscriptions.fold(false)(subs => subs.apis.exists(_.hasSubscriptions))

    if( !hasNonExampleSubscription(subscriptionData) ) {
      val form = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
      Future.successful(BadRequest(apiSubscriptionsView(app, subscriptionData, Some(form))))
    }
    else {
      for {
        _ <- applicationService.updateCheckInformation(app.id, information.copy(apiSubscriptionsConfirmed = true))
      } yield Redirect(landingPageRoute(app.id))
    }
  }

  private def apiSubscriptionsView(
    app: Application,
    subscriptionData: SubscriptionData,
    form: Option[Form[DummySubscriptionsForm]] = None
    )(implicit request: ApplicationRequest[AnyContent]) = {
      views.html.checkpages.apiSubscriptions(
        app,
        subscriptionData.role,
        subscriptionData.subscriptions,
        app.id,
        apiSubscriptionsActionRoute(app.id),
        form
      )
  }

  protected def landingPageRoute(appId: String): Call
  protected def apiSubscriptionsActionRoute(appId: String): Call
}
