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
import domain.models.apidefinitions.{APISubscriptionStatus, ApiContext}
import domain.models.applications.ApplicationId
import domain.models.applicationuplift.{ApiSubscriptions, ResponsibleIndividual, SellResellOrDistribute}
import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service.{ApplicationActionService, ApplicationService, GetProductionCredentialsFlowService, SessionService}
import views.helper.IdFormatter
import views.html.upliftJourney._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

case class ResponsibleIndividualForm(fullName: String, emailAddress: String)

object ResponsibleIndividualForm {
  def form: Form[ResponsibleIndividualForm] = Form(
    mapping(
      "fullName" -> requiredIndividualFullnameValidator,
      "emailAddress" -> requiredIndividualEmailValidator()
    )(ResponsibleIndividualForm.apply)(ResponsibleIndividualForm.unapply)
  )
}

final case class SellResellOrDistributeForm(answer: Option[String] = Some(""))

object SellResellOrDistributeForm {

  def form: Form[SellResellOrDistributeForm] = Form(
    mapping(
      "answer" -> optional(text)
        .verifying(FormKeys.sellResellOrDistributeConfirmNoChoiceKey, s => s.isDefined)
    )(SellResellOrDistributeForm.apply)(SellResellOrDistributeForm.unapply)
  )
}

@Singleton
class SR20 @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      confirmApisView: ConfirmApisView,
                      turnOffApisMasterView:TurnOffApisMasterView,
                      val apmConnector: ApmConnector,
                      responsibleIndividualView: ResponsibleIndividualView,
                      flowService: GetProductionCredentialsFlowService,
                      sellResellOrDistributeSoftwareView: SellResellOrDistributeSoftwareView)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions{

  val responsibleIndividualForm: Form[ResponsibleIndividualForm] = ResponsibleIndividualForm.form
  val sellResellOrDistributeForm: Form[SellResellOrDistributeForm] = SellResellOrDistributeForm.form

  def confirmApiSubscriptionsPage(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def getApiNameForContext(apiContext: ApiContext) =
      request.subscriptions
      .find(_.context == apiContext )
      .map(_.name)

    for {
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      flow <- flowService.fetchFlow(request.user)
      subscriptionFlow = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
    }
    yield {
      val data: Set[String] = (for {
        subscription <- upliftableApiIds.filter(subscriptionFlow.isSelected)
        name <- getApiNameForContext(subscription.context)
      }
      yield {
        s"$name - ${subscription.version.value}"
      })
      Ok(confirmApisView(sandboxAppId, data, upliftableApiIds.size > 1))
    }
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      flow <- flowService.fetchFlow(request.user)
      subscriptionFlow = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
      apiIdsToSubscribeTo <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId).map(_.filter(subscriptionFlow.isSelected))
      upliftedAppId <- apmConnector.upliftApplication(sandboxAppId,apiIdsToSubscribeTo)
    } yield {
      Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(upliftedAppId)).withSession(request.session - "subscriptions")
    }
  }

  def setSubscribedStatusFromFlow(apiSubscriptions: ApiSubscriptions)(apiSubscription: APISubscriptionStatus): APISubscriptionStatus = {
    apiSubscription.copy(subscribed = apiSubscriptions.isSelected(apiSubscription.apiIdentifier))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      flow <- flowService.fetchFlow(request.user)
      subscriptionFlow = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
      upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      subscriptionsWithFlowAdjusted = request.subscriptions
        .filter(s => upliftableApiIds.contains(s.apiIdentifier))
        .map(setSubscribedStatusFromFlow(subscriptionFlow))
    } yield {
      Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(subscriptionsWithFlowAdjusted), DummySubscriptionsForm.form))
    }
  }

  def saveApiSubscriptionsSubmit(sandboxAppId: ApplicationId) = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    lazy val formSubmittedSubscriptions: Map[String, Boolean] = 
      request.body.asFormUrlEncoded.get
      .filter(_._1.contains("subscribed"))
      .mapValues(_.head == "true")
      .map {
        case (name, isSubscribed) => (name.replace("-subscribed", "") -> isSubscribed)
      }

    apmConnector.fetchUpliftableSubscriptions(sandboxAppId).flatMap { upliftableApiIds =>
      val apiLookups = upliftableApiIds.map( id => IdFormatter.identifier(id) -> id ).toMap
      val newFlow = ApiSubscriptions(formSubmittedSubscriptions.map {
        case (id, onOff) => apiLookups(id) -> onOff
      })

      if (formSubmittedSubscriptions.exists(_._2 == true)) {
        flowService.storeApiSubscriptions(newFlow, request.user)
        .map(_ => Redirect(controllers.routes.SR20.confirmApiSubscriptionsPage(sandboxAppId)))
      }
      else {
        val errorForm = DummySubscriptionsForm.form.withError(FormError("apiSubscriptions", "error.turnoffapis.requires.at.least.one"))
        for {
          flow                  <- flowService.fetchFlow(request.user)
          subscriptionFlow       = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
          sandboxSubscribedApis  = request.subscriptions.filter(s => upliftableApiIds.contains(s.apiIdentifier))
                                   .map(setSubscribedStatusFromFlow(subscriptionFlow))
        } yield            
          Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(sandboxSubscribedApis), errorForm))
      }
    }
  }

  def responsibleIndividual(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      flow <- flowService.fetchFlow(request.user)
      form = flow.responsibleIndividual.fold[Form[ResponsibleIndividualForm]](responsibleIndividualForm)(x => responsibleIndividualForm.fill(ResponsibleIndividualForm(x.fullName, x.emailAddress)))
    } yield Ok(responsibleIndividualView(sandboxAppId, form))
  }

  def responsibleIndividualAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def handleValidForm(form: ResponsibleIndividualForm): Future[Result] = {
      val responsibleIndividual = ResponsibleIndividual(form.fullName, form.emailAddress)
      flowService.storeResponsibleIndividual(responsibleIndividual, request.user)
      .map(_ => Redirect(controllers.routes.SR20.sellResellOrDistributeYourSoftware(sandboxAppId)))
    }
    
    def handleInvalidForm(form: Form[ResponsibleIndividualForm]): Future[Result] = {
      successful(BadRequest(responsibleIndividualView(sandboxAppId, form)))
    }

    responsibleIndividualForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def sellResellOrDistributeYourSoftware(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      flow <- flowService.fetchFlow(request.user)
      form = flow.sellResellOrDistribute.fold[Form[SellResellOrDistributeForm]](sellResellOrDistributeForm)(x => sellResellOrDistributeForm.fill(SellResellOrDistributeForm(Some(x.answer))))
    } yield Ok(sellResellOrDistributeSoftwareView(sandboxAppId, form))
  }

  def sellResellOrDistributeYourSoftwareAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def handleInvalidForm(formWithErrors: Form[SellResellOrDistributeForm]) =
      Future(BadRequest(sellResellOrDistributeSoftwareView(sandboxAppId, formWithErrors)))

    def handleValidForm(validForm: SellResellOrDistributeForm) = {
      validForm.answer match {
        case Some(answer) =>
          flowService.storeSellResellOrDistribute(SellResellOrDistribute(answer), request.user)
            .flatMap(_ =>
                for {
                  upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
                  apiSubscriptions         = ApiSubscriptions(upliftableSubscriptions.map(id => (id, true)).toMap)
                  _ <- flowService.storeApiSubscriptions(apiSubscriptions, request.user)
                }
                yield {
                  Redirect(controllers.routes.SR20.confirmApiSubscriptionsPage(sandboxAppId))
                }
            )
        case None => throw new IllegalStateException("Should never get here")
      }
    }
    sellResellOrDistributeForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
