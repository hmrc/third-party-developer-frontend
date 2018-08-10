/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ApplicationGlobal
import connectors.ThirdPartyDeveloperConnector
import controllers.SubscriptionRedirect._
import domain._
import play.api.Play.current
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.include.{subscribeConfirmation, subscriptionFields, unsubscribeConfirmation}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Subscriptions extends ApplicationController with ApplicationHelper {

  def developerConnector: ThirdPartyDeveloperConnector

  def auditService: AuditService

  def subFieldsService: SubscriptionFieldsService

  def subscriptionsService: SubscriptionsService

  def apiSubscriptionsHelper: ApiSubscriptionsHelper

  def subscriptions(applicationId: String) = teamMemberOnStandardApp(applicationId) { implicit request =>
    apiSubscriptionsHelper.fetchPageDataFor(request.application).map { data =>
      val role = apiSubscriptionsHelper.roleForApplication(data.app, request.user.email)
      val form = EditApplicationForm.withData(data.app)
      val view = views.html.subscriptions(role, data, form, request.application, data.subscriptions, data.app.id, data.hasSubscriptions)
      Ok(view)
    } recover {
      case _: ApplicationNotFound => NotFound(ApplicationGlobal.notFoundTemplate)
    }
  }

  def subscribeToApi(applicationId: String, name: String, context: String, version: String) = adminOnApp(applicationId) { implicit request =>
    Future.successful(Ok(subscribeConfirmation(request.application, SubscriptionConfirmationForm.form, applicationId, name, context, version, SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE)))
  }

  def subscribeToApiAction(applicationId: String, name: String, context: String, version: String, subscriptionRedirect: String) = adminOnApp(applicationId) { implicit request =>
    def handleValidForm(app: Application, apiName: String, apiVersion: String, form: SubscriptionConfirmationForm) = {
      form.subscribeConfirm match {
        case Some("Yes") => subscriptionsService.requestApiSubscription(request.user, app, apiName, apiVersion).map(_ => Ok(views.html.subscribeRequestSubmitted(app, apiName, apiVersion)))
        case _ => Future.successful(Redirect(controllers.routes.Subscriptions.subscriptions(applicationId)))
      }
    }

    def handleInvalidForm(app: Application, apiName: String, apiVersion: String, form: Form[SubscriptionConfirmationForm]) = {
      Future.successful(Ok(subscribeConfirmation(app, form, applicationId, apiName, context, apiVersion, SubscriptionRedirect.withName(subscriptionRedirect))))
    }

    val requestForm = SubscriptionConfirmationForm.form.bindFromRequest

    requestForm.fold(
      formWithErrors => handleInvalidForm(request.application, name, version, formWithErrors),
      validForm => handleValidForm(request.application, name, version, validForm))
  }

  def unsubscribeFromApi(applicationId: String, name: String, context: String, version: String, redirectTo: String = MANAGE_PAGE.toString) = teamMemberOnApp(applicationId) { implicit request =>
    if (APPLICATION_CHECK_PAGE.toString == redirectTo) {
      implicit val navSection = "credentials"
      Future.successful(Ok(unsubscribeConfirmation(request.application, UnsubscribeConfirmationForm.form, name, context, version, APPLICATION_CHECK_PAGE.toString)))
    } else {
      Future.successful(Ok(unsubscribeConfirmation(request.application, UnsubscribeConfirmationForm.form, name, context, version, MANAGE_PAGE.toString)))
    }
  }

  def unsubscribeFromApiAction(applicationId: String, name: String, context: String, version: String, redirectTo: String = MANAGE_PAGE.toString) = teamMemberOnApp(applicationId) { implicit request =>
    def handleValidForm(app: Application, apiName: String, apiVersion: String, validForm: UnsubscribeConfirmationForm) = {

      def canUnsubscribe(application: Application, email: String) = {
        val role = apiSubscriptionsHelper.roleForApplication(application, email)

        application.deployedTo == Environment.SANDBOX || role == Role.ADMINISTRATOR || application.state.name == State.TESTING
      }

      def shouldDelayUnsubscribe(application: Application, email: String) = {
        application.deployedTo == Environment.PRODUCTION && application.state.name != State.TESTING
      }

      def unsubscribeWithDelay(application: Application, apiName: String, apiVersion: String) = {
        subscriptionsService.requestApiUnsubscribe(request.user, application, apiName, apiVersion).map(_ => Ok(views.html.unsubscribeRequestSubmitted(application, apiName, apiVersion)))
      }

      def createUnsubscribeResponse(application: Application, subscriptionRedirect: String)(implicit hc: HeaderCarrier) = {
        if (API_SUBSCRIPTIONS_PAGE.toString == subscriptionRedirect)
          Future.successful(Redirect(routes.Subscriptions.subscriptions(application.id)))
        else if (APPLICATION_CHECK_PAGE.toString == subscriptionRedirect)
          Future.successful(Redirect(routes.ApplicationCheck.apiSubscriptionsPage(application.id)))
        else Future.successful(Redirect(routes.ManageApplications.editApplication(application.id, None)))
      }

      def createAjaxUnsubscribeResponse(app: Application, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier) = {
        for {
          subs <- applicationService.apisWithSubscriptions(app)
        } yield Json.toJson(AjaxSubscriptionResponse.from(apiContext, apiVersion, subs))
      }

      def createResponse(app: Application, isAjaxRequest: Boolean, apiContext: String, apiVersion: String, subscriptionRedirect: String)(implicit hc: HeaderCarrier) = {
        if (isAjaxRequest) createAjaxUnsubscribeResponse(app, apiContext, apiVersion).map(r => Ok(r))
        else createUnsubscribeResponse(app, subscriptionRedirect)
      }

      def unsubscribeImmediately(application: Application, context: String, version: String) = {
        for {
          _ <- updateCheckInformation(application)
          _ <- applicationService.unsubscribeFromApi(application.id, context, version)
          response <- createResponse(app, request.headers.isAjaxRequest, context, apiVersion, redirectTo)
        } yield response
      }

      validForm.unsubscribeConfirm match {
        case Some("Yes") =>
          if (canUnsubscribe(request.application, request.user.email)) {
            if (shouldDelayUnsubscribe(request.application, request.user.email)) {
              unsubscribeWithDelay(app, apiName, apiVersion)
            } else {
              unsubscribeImmediately(app, context, version)
            }
          } else {
            Future.successful(Redirect(controllers.routes.Subscriptions.subscriptions(applicationId)))
          }
        case _ =>
          Future.successful(Redirect(controllers.routes.Subscriptions.subscriptions(applicationId)))
      }
    }

    def handleInvalidForm(app: Application, apiName: String, apiVersion: String, formWithErrors: Form[UnsubscribeConfirmationForm]) = {
      Future.successful(Ok(unsubscribeConfirmation(app, formWithErrors, apiName, context, apiVersion, SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString)))
    }

    val requestForm = UnsubscribeConfirmationForm.form.bindFromRequest

    requestForm.fold(
      formWithErrors => handleInvalidForm(request.application, name, version, formWithErrors),
      validForm => handleValidForm(request.application, name, version, validForm)
    )
  }

  private def createAjaxSubscriptionResponse(app: Application, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier) = {
    for {
      subs <- applicationService.apisWithSubscriptions(app)
    } yield Json.toJson(AjaxSubscriptionResponse.from(apiContext, apiVersion, subs))
  }

  private def createSubscriptionResponse(application: Application, subscriptionRedirect: String)(implicit hc: HeaderCarrier) = {
    if (API_SUBSCRIPTIONS_PAGE.toString == subscriptionRedirect)
      Future.successful(Redirect(routes.Subscriptions.subscriptions(application.id)))
    else if (APPLICATION_CHECK_PAGE.toString == subscriptionRedirect)
      Future.successful(Redirect(routes.ApplicationCheck.apiSubscriptionsPage(application.id)))
    else Future.successful(Redirect(routes.ManageApplications.editApplication(application.id, None)))
  }

  private def createResponse(app: Application, isAjaxRequest: Boolean, apiContext: String, apiVersion: String, subscriptionRedirect: String)(implicit hc: HeaderCarrier) = {
    if (isAjaxRequest) createAjaxSubscriptionResponse(app, apiContext, apiVersion).map(r => Ok(r))
    else createSubscriptionResponse(app, subscriptionRedirect)
  }

  def saveSubscriptionFields(applicationId: String, apiContext: String, apiVersion: String, subscriptionRedirect: String) = loggedInAction { implicit request =>
    def handleValidForm(validForm: SubscriptionFieldsForm) = {
      def saveFields(validForm: SubscriptionFieldsForm)(implicit hc: HeaderCarrier): Future[Any] = {
        if (validForm.fields.nonEmpty) {
          subFieldsService.saveFieldValues(
            applicationId,
            apiContext,
            apiVersion,
            Map(validForm.fields.map(f => f.name -> f.value.getOrElse("")): _ *))
        } else {
          Future.successful(())
        }
      }

      for {
        _ <- saveFields(validForm)
        app <- fetchApp(applicationId)
        response <- createResponse(app, request.headers.isAjaxRequest, apiContext, apiVersion, subscriptionRedirect)
      } yield response
    }

    def handleInvalidForm(formWithErrors: Form[SubscriptionFieldsForm]) = {
      Future.successful(BadRequest(
        subscriptionFields(
          SubscriptionFieldsViewModel(
            applicationId,
            apiContext,
            apiVersion,
            formWithErrors))))
    }

    SubscriptionFieldsForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def subscribeApplicationToApi(applicationId: String, apiContext: String, apiVersion: String, subscriptionRedirect: String): Action[AnyContent] = loggedInAction { implicit request =>
    def subscribeIfNotSubscribed(apiSubStatus: Seq[APISubscriptionStatus], applicationId: String, apiContext: String, apiVersion: String) = {
      if (apiSubStatus.filter(a => a.apiVersion.version == apiVersion && a.context == apiContext).head.subscribed) {
        Future.successful(())
      } else {
        applicationService.subscribeToApi(applicationId, apiContext, apiVersion)
      }
    }

    def updateCheckInformation(app: Application): Future[Any] = {
      app.deployedTo match {
        case Environment.PRODUCTION =>
          val result = applicationService.updateCheckInformation(app.id, app.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionsConfirmed = false))
          result
        case _ => Future.successful(())
      }
    }

    def handleValidForm(validForm: SubscriptionFieldsForm) = {
      for {
        app <- fetchApp(applicationId)
        _ <- updateCheckInformation(app)
        apiSubs <- applicationService.apisWithSubscriptions(app)
        _ <- subscribeIfNotSubscribed(apiSubs, applicationId, apiContext, apiVersion)
        response <- createResponse(app, request.headers.isAjaxRequest, apiContext, apiVersion, subscriptionRedirect)
      } yield response
    }

    def handleInvalidForm(formWithErrors: Form[SubscriptionFieldsForm]) = {
      Future.successful(BadRequest(
        subscriptionFields(
          SubscriptionFieldsViewModel(
            applicationId,
            apiContext,
            apiVersion,
            formWithErrors))))
    }

    SubscriptionFieldsForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  private def updateCheckInformation(app: Application)(implicit hc: HeaderCarrier): Future[Any] = {
    app.deployedTo match {
      case Environment.PRODUCTION =>
        applicationService.updateCheckInformation(app.id, app.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionsConfirmed = false))
      case _ => Future.successful(())
    }
  }
}

object Subscriptions extends Subscriptions with WithAppConfig {
  lazy val sessionService = SessionService
  lazy val applicationService = ApplicationServiceImpl
  lazy val developerConnector = ThirdPartyDeveloperConnector
  lazy val auditService = AuditService
  lazy val apiSubscriptionsHelper = ApiSubscriptionsHelper
  lazy val subFieldsService = SubscriptionFieldsService

  override def subscriptionsService = SubscriptionsService
}

trait ApiSubscriptionsHelper {

  def applicationService: ApplicationService

  def fetchPageDataFor(application: Application)(implicit hc: HeaderCarrier): Future[PageData] = {
    for {
      creds <- applicationService.fetchCredentials(application.id)
      subscriptions <- applicationService.apisWithSubscriptions(application)
    } yield {
      PageData(application, creds, APISubscriptions.groupSubscriptions(subscriptions))
    }
  }

  def fetchAllSubscriptions(application: Application, developer: Developer)(implicit hc: HeaderCarrier): Future[Option[SubscriptionData]] = {
    fetchPageDataFor(application).map { data =>
      val role = roleForApplication(data.app, developer.email)
      Some(SubscriptionData(role, application, data.subscriptions, data.hasSubscriptions))
    } recover {
      case _: ApplicationNotFound => None
    }
  }

  def roleForApplication(application: Application, email: String) =
    application.role(email).getOrElse(throw new ApplicationNotFound)
}

object ApiSubscriptionsHelper extends ApiSubscriptionsHelper {
  lazy val applicationService = ApplicationServiceImpl
}

case class SubscriptionFieldsViewModel(applicationId: String, apiContext: String, apiVersion: String, subFieldsForm: Form[SubscriptionFieldsForm])
