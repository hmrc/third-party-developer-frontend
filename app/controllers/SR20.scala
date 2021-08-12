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
import domain.models.apidefinitions.{APISubscriptionStatus, ApiContext, ApiIdentifier, ApiVersion}
import domain.models.applications.ApplicationId
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Session}
import service.{ApplicationActionService, ApplicationService, SessionService}
import views.helper.IdFormatter
import views.html.{ConfirmApisView, TurnOffApisView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
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
       
  def confirmApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    val stuff = request.session

    def getApiNameForContext(apiContext: ApiContext) = {
      request.subscriptions.find(_.context == apiContext ).map(_.name)
    }

    for {
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
    }
    yield {
      val upliftableSubscriptionsWithData = upliftableSubscriptions
        .flatMap(upliftableSubscription => getApiNameForContext(upliftableSubscription.context)
          .map { (_, upliftableSubscription.version.value) })

      Ok(confirmApisView(sandboxAppId, upliftableSubscriptionsWithData)).withNewSession
    }
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(sandboxAppId)))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    val subscribedApis = request.subscriptions.filter(_.subscribed)
    Future.successful(Ok(turnOffApisView(request.application, request.role, APISubscriptions.groupSubscriptions(subscribedApis))))
  }

  def changeApiSubscription(applicationId: ApplicationId, apiContext: ApiContext, apiVersion: ApiVersion): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>

      val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
      val subscribedApis = request.subscriptions.filter(_.subscribed)

      val session: Session = request.session.get(apiIdentifier.toString) match {
        case Some(_) => request.session - apiIdentifier.toString
        case None => request.session + (apiIdentifier.toString -> "false")
      }

      Future.successful(Ok(turnOffApisView(request.application, request.role, APISubscriptions.groupSubscriptions(subscribedApis), None))
        .withSession(session)
      )
  }

  def saveApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    val formSubmittedSubscriptions: Map[String, Boolean] = request.body.asFormUrlEncoded.get.mapValues(_.head == "true")
      .filter(_._1.contains("subscribed"))
      .map(apiSubscription => { (apiSubscription._1.replace("-subscribed", "") -> apiSubscription._2 ) })

    val sandboxSubscribedApis = request.subscriptions.filter(_.subscribed)

    val sandboxSubscribedApiIds = sandboxSubscribedApis
      .map(sandboxSubscribedApi => ApiIdentifier(sandboxSubscribedApi.context, sandboxSubscribedApi.apiVersion.version))

    val apiIdLookup = sandboxSubscribedApiIds.map( sandboxSubscribedApiId => { IdFormatter.identifier(sandboxSubscribedApiId) -> sandboxSubscribedApiId } ).toMap

    val things = formSubmittedSubscriptions.map {
      case (k, v) => apiIdLookup(k).toString -> v.toString
    }.toList.mkString("[", ",", "]")

    if (formSubmittedSubscriptions.exists(_._2 == true)) {
      successful(Redirect(controllers.routes.SR20.confirmApiSubscriptions(sandboxAppId)).withSession(request.session + ("subscriptions" -> things)))
      //      val sessionSubscribedApis = getSessionApis(request.session, sandboxSubscribedApis, false)
      //      val unsubscribeRequests = sessionSubscribedApis map (sa => {
      //        applicationService.unsubscribeFromApi(request.application, ApiIdentifier(sa.context, sa.apiVersion.version))
      //      })
      //      Future.sequence(unsubscribeRequests).map( _ => Redirect(controllers.routes.SR20.confirmApiSubscriptions(sandboxAppId)) )
    }
    else {
      val errorForm = DummySubscriptionsForm.form.bind(Map("hasNonExampleSubscription" -> "false"))
      successful(Ok(turnOffApisView(request.application, request.role, APISubscriptions.groupSubscriptions(sandboxSubscribedApis), Some(errorForm))))
    }
  }

  private def getSessionApis(session: Session, subscribedApis: List[APISubscriptionStatus], subscribed: Boolean = true) =
    subscribedApis.map(sa =>
      session.data.get(ApiIdentifier(sa.context, sa.apiVersion.version).toString) match {
        case Some(_) => sa.copy(subscribed = false)
        case None => sa.copy(subscribed = true)
      }
    ).filter(_.subscribed == subscribed)
}
