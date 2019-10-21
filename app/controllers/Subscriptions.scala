/*
 * Copyright 2019 HM Revenue & Customs
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
import connectors.ThirdPartyDeveloperConnector
import domain.Capabilities.{ManageLockedSubscriptions, SupportsSubscriptions}
import domain.Permissions.{AdministratorOnly, TeamMembersOnly}
import domain.SubscriptionRedirect._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Result}
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.include.{changeSubscriptionConfirmation, subscriptionFields}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Subscriptions @Inject()(val developerConnector: ThirdPartyDeveloperConnector,
                              val auditService: AuditService,
                              val subFieldsService: SubscriptionFieldsService,
                              val subscriptionsService: SubscriptionsService,
                              val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                              val applicationService: ApplicationService,
                              val sessionService: SessionService,
                              val errorHandler: ErrorHandler,
                              val messagesApi: MessagesApi,
                              implicit val appConfig: ApplicationConfig)
                             (implicit ec: ExecutionContext)
  extends ApplicationController with ApplicationHelper {

  private def canManageLockedApiSubscriptionsAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    permissionThenCapabilityAction(AdministratorOnly,ManageLockedSubscriptions)(applicationId)(fun)

  private def canViewSubscriptionsInDevHubAction(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    capabilityThenPermissionsAction(SupportsSubscriptions, TeamMembersOnly)(applicationId)(fun)

  def subscriptions(applicationId: String) = canViewSubscriptionsInDevHubAction(applicationId) { implicit request =>
    apiSubscriptionsHelper.fetchPageDataFor(request.application).map { data =>
      val role = apiSubscriptionsHelper.roleForApplication(data.app, request.user.email)
      val form = EditApplicationForm.withData(data.app)
      val view = views.html.subscriptions(role, data, form, request.application, data.subscriptions, data.app.id, data.hasSubscriptions)
      Ok(view)
    } recover {
      case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
    }
  }

  private def redirect(redirectTo: String, applicationId: String) = SubscriptionRedirect.withNameOption(redirectTo) match {
    case Some(API_SUBSCRIPTIONS_PAGE) => Redirect(routes.Subscriptions.subscriptions(applicationId))
    case Some(APPLICATION_CHECK_PAGE) => Redirect(routes.ApplicationCheck.apiSubscriptionsPage(applicationId))
    case _ => Redirect(routes.ManageApplications.editApplication(applicationId, None))
  }

  def changeApiSubscription(applicationId: String, apiContext: String, apiVersion: String, redirectTo: String) = whenTeamMemberOnApp(applicationId) {
    implicit request =>
      def updateSubscription(form: ChangeSubscriptionForm) = form.subscribed match {
        case Some(subscribe) =>
          def service = if (subscribe) applicationService.subscribeToApi _ else applicationService.unsubscribeFromApi _

          service(applicationId, apiContext, apiVersion) andThen { case _ => updateCheckInformation(request.application) }
        case _ =>
          Future.successful(redirect(redirectTo, applicationId))
      }

      def handleValidForm(form: ChangeSubscriptionForm) =
        if (request.application.hasLockedSubscriptions) {
          Future.successful(Forbidden(errorHandler.badRequestTemplate))
        } else {
          updateSubscription(form).map(_ => redirect(redirectTo, applicationId))
        }

      def handleInvalidForm(formWithErrors: Form[ChangeSubscriptionForm]) = Future.successful(BadRequest(errorHandler.badRequestTemplate))

      ChangeSubscriptionForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm);
  }

  def changeLockedApiSubscription(applicationId: String,
                                  apiName: String,
                                  apiContext: String,
                                  apiVersion: String,
                                  redirectTo: String) = canManageLockedApiSubscriptionsAction(applicationId) {
    implicit request =>
        applicationService.isSubscribedToApi(request.application, apiName, apiContext, apiVersion).map(subscribed =>
          Ok(changeSubscriptionConfirmation(
            request.application, ChangeSubscriptionConfirmationForm.form, apiName, apiContext, apiVersion, subscribed, redirectTo)))
  }

  def changeLockedApiSubscriptionAction(applicationId: String,
                                        apiName: String,
                                        apiContext: String,
                                        apiVersion: String,
                                        redirectTo: String) = canManageLockedApiSubscriptionsAction(applicationId) { implicit request =>
    def requestChangeSubscription(subscribed: Boolean) = {
      if (subscribed) {
        subscriptionsService.requestApiUnsubscribe(request.user, request.application, apiName, apiVersion)
          .map(_ => Ok(views.html.unsubscribeRequestSubmitted(request.application, apiName, apiVersion)))
      } else {
        subscriptionsService.requestApiSubscription(request.user, request.application, apiName, apiVersion)
          .map(_ => Ok(views.html.subscribeRequestSubmitted(request.application, apiName, apiVersion)))
      }
    }

    def handleValidForm(subscribed: Boolean)(form: ChangeSubscriptionConfirmationForm) = form.confirm match {
      case Some(_) => requestChangeSubscription(subscribed)
      case _ => Future.successful(redirect(redirectTo, applicationId))
    }

    def handleInvalidForm(subscribed: Boolean)(formWithErrors: Form[ChangeSubscriptionConfirmationForm]) =
      Future.successful(
        BadRequest(changeSubscriptionConfirmation(request.application, formWithErrors, apiName, apiContext, apiVersion, subscribed, redirectTo)))

    applicationService.isSubscribedToApi(request.application, apiName, apiContext, apiVersion).flatMap(subscribed =>
      ChangeSubscriptionConfirmationForm.form.bindFromRequest.fold(handleInvalidForm(subscribed), handleValidForm(subscribed)))
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

  private def createResponse(app: Application,
                             isAjaxRequest: Boolean,
                             apiContext: String,
                             apiVersion: String,
                             subscriptionRedirect: String)(implicit hc: HeaderCarrier): Future[Result] = {
    if (isAjaxRequest) createAjaxUnsubscribeResponse(app, apiContext, apiVersion).map(r => Ok(r))
    else Future.successful(redirect(subscriptionRedirect, app.id))
  }

  private def createAjaxUnsubscribeResponse(app: Application, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier) = {
    for {
      subs <- applicationService.apisWithSubscriptions(app)
    } yield Json.toJson(AjaxSubscriptionResponse.from(apiContext, apiVersion, subs))
  }

  private def updateCheckInformation(app: Application)(implicit hc: HeaderCarrier): Future[Any] = {
    app.deployedTo match {
      case Environment.PRODUCTION =>
        applicationService.updateCheckInformation(app.id, app.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionsConfirmed = false))
      case _ => Future.successful(())
    }
  }
}

class ApiSubscriptionsHelper @Inject()(applicationService: ApplicationService)(implicit ec: ExecutionContext) {

  def fetchPageDataFor(application: Application)(implicit hc: HeaderCarrier): Future[PageData] = {
    for {
      creds <- applicationService.fetchCredentials(application.id)
      subscriptions <- applicationService.apisWithSubscriptions(application)
    } yield {
      PageData(application, creds, APISubscriptions.groupSubscriptions(subscriptions))
    }
  }

  def fetchAllSubscriptions(application: Application, developer: DeveloperSession)(implicit hc: HeaderCarrier): Future[Option[SubscriptionData]] = {
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

case class SubscriptionFieldsViewModel(applicationId: String, apiContext: String, apiVersion: String, subFieldsForm: Form[SubscriptionFieldsForm])
