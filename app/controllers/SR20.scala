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
import controllers.{APISubscriptions, ApplicationController, ApplicationRequest}
import controllers.checkpages.{ApiSubscriptionsPartialController, CanUseCheckActions, DummySubscriptionsForm, routes}
import domain.models
import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import domain.models.applications.{Application, ApplicationId, TermsOfUseStatus}
import domain.models.controllers.SubscriptionData
import domain.models.subscriptions.ApiData
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import service.{ApplicationActionService, ApplicationService, SessionService}
import views.html.checkpages.ApiSubscriptionsView
import views.html.{ConfirmApisView, TurnOffApisView}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier
import connectors.ApmConnector



@Singleton
class SR20 @Inject() (
                             val errorHandler: ErrorHandler,
                             val sessionService: SessionService,
                             val applicationActionService: ApplicationActionService,
                             val applicationService: ApplicationService,
                             mcc: MessagesControllerComponents,
                             turnOffApisView: TurnOffApisView,
                             confirmApisView: ConfirmApisView,
                             val cookieSigner: CookieSigner,
                             val apmConnector: ApmConnector
                           )(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions{

  private def asSubscriptionData(applicationRequest: ApplicationRequest[AnyContent]) =
    models.controllers.SubscriptionData(applicationRequest.role, applicationRequest.application, APISubscriptions.groupSubscriptions(applicationRequest.subscriptions))

  def confirmApiSubscription(sandboxAppId: ApplicationId): Action[AnyContent] = canUseChecksAction(sandboxAppId) { implicit request =>
  // val app = request.application
    // Future.successful(Ok(renderConfirmApiSubscriptionView(app, asSubscriptionData(request), request.openAccessApis)))
    val subscriptionData = asSubscriptionData(request)
    println("***** request: " + request.subscriptions)
    Future.successful(Ok(confirmApisView(sandboxAppId, 
    List(
      ApiIdentifier(ApiContext("test-api-1"), ApiVersion("1.0")),
      ApiIdentifier(ApiContext("test-api-2"), ApiVersion("1.1"))
      )
    )))
  }

  protected def confirmApiSubscriptionRoute(appId: ApplicationId): Call = routes.SR20.confirmApiSubscription(appId)
  
  // private def renderConfirmApiSubscriptionView(
  //                                         app: Application,
  //                                         subscriptionData: SubscriptionData,
  //                                         openAccessApis: Map[ApiContext, ApiData],
  //                                         form: Option[Form[DummySubscriptionsForm]] = None
  //                                       )(implicit request: ApplicationRequest[AnyContent]) = {
  //   confirmApiSubscriptionView(
  //     app,
  //     subscriptionData.role,
  //     subscriptionData.subscriptions,
  //     openAccessApis,
  //     app.id,
  //     confirmApiSubscriptionRoute(app.id),
  //     form
  //   )
  // }

  def upliftSandboxApplication(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    upliftApplicationAndShowRequestCheckPage(sandboxAppId)
  }

  private def upliftApplicationAndShowRequestCheckPage(sandboxAppId: ApplicationId)(implicit hc: HeaderCarrier) = {
    for {
      newAppId <- apmConnector.upliftApplication(sandboxAppId)
    } yield Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(newAppId))
  }
}
