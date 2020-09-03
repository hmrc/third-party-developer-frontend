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

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications._
import domain.models.applications.Capabilities.{ManageLockedSubscriptions, SupportsSubscriptions}
import domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import domain.models.developers.DeveloperSession
import domain.models.views.SubscriptionRedirect
import domain.models.views.SubscriptionRedirect._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.twirl.api.Html
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.helper.EnvironmentNameService
import views.html.{AddAppSubscriptionsView, ManageSubscriptionsView, SubscribeRequestSubmittedView, UnsubscribeRequestSubmittedView}
import views.html.include.ChangeSubscriptionConfirmationView

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Subscriptions @Inject() (
    val developerConnector: ThirdPartyDeveloperConnector,
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val subscriptionsService: SubscriptionsService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    manageSubscriptionsView: ManageSubscriptionsView,
    addAppSubscriptionsView: AddAppSubscriptionsView,
    changeSubscriptionConfirmationView: ChangeSubscriptionConfirmationView,
    unsubscribeRequestSubmittedView: UnsubscribeRequestSubmittedView,
    subscribeRequestSubmittedView: SubscribeRequestSubmittedView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig, val environmentNameService: EnvironmentNameService)
    extends ApplicationController(mcc)
    with ApplicationHelper {

  private def canManageLockedApiSubscriptionsAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForAllStates(ManageLockedSubscriptions, AdministratorOnly)(applicationId)(fun)

  private def canViewSubscriptionsInDevHubAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForAllStates(SupportsSubscriptions, TeamMembersOnly)(applicationId)(fun)

  def manageSubscriptions(applicationId: ApplicationId): Action[AnyContent] = canViewSubscriptionsInDevHubAction(applicationId) { implicit request =>
    renderSubscriptions(
      request.application,
      request.user,
      (role: Role, data: PageData, form: Form[EditApplicationForm]) => {
        manageSubscriptionsView(role, data, form, applicationViewModelFromApplicationRequest, data.subscriptions, data.app.id)
      }
    )
  }

  def addAppSubscriptions(applicationId: ApplicationId): Action[AnyContent] = canViewSubscriptionsInDevHubAction(applicationId) { implicit request =>
    renderSubscriptions(
      request.application,
      request.user,
      (role: Role, data: PageData, form: Form[EditApplicationForm]) => {
        addAppSubscriptionsView(role, data, form, request.application, request.application.deployedTo, data.subscriptions)
      }
    )
  }

  def renderSubscriptions(application: Application, user: DeveloperSession, renderHtml: (Role, PageData, Form[EditApplicationForm]) => Html)(
      implicit request: ApplicationRequest[AnyContent]
  ): Future[Result] = {
    val subsData = APISubscriptions.groupSubscriptions(request.subscriptions)
    val role = request.role
    val form = EditApplicationForm.withData(request.application)

    val html = renderHtml(role, PageData(request.application, subsData), form)

    Future.successful(Ok(html))
  }

  private def redirect(redirectTo: String, applicationId: ApplicationId) = SubscriptionRedirect.withNameOption(redirectTo) match {
    case Some(MANAGE_PAGE)            => Redirect(routes.Details.details(applicationId))
    case Some(APPLICATION_CHECK_PAGE) => Redirect(controllers.checkpages.routes.ApplicationCheck.apiSubscriptionsPage(applicationId))
    case Some(API_SUBSCRIPTIONS_PAGE) => Redirect(routes.Subscriptions.manageSubscriptions(applicationId))
    case None                         => Redirect(routes.Details.details(applicationId))
  }

  def changeApiSubscription(applicationId: ApplicationId, apiContext: ApiContext, apiVersion: ApiVersion, redirectTo: String): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>
      def updateSubscription(form: ChangeSubscriptionForm) = form.subscribed match {
        case Some(subscribe) =>
          def service = if (subscribe) applicationService.subscribeToApi _ else applicationService.unsubscribeFromApi _

          service(request.application, apiContext, apiVersion) andThen { case _ => updateCheckInformation(request.application) }
        case _ =>
          Future.successful(redirect(redirectTo, applicationId))
      }

      def handleValidForm(form: ChangeSubscriptionForm) =
        if (request.application.hasLockedSubscriptions) {
          import domain.Error._
          Future.successful(BadRequest(Json.toJson(BadRequestError)))
        } else {
          updateSubscription(form).map(_ => redirect(redirectTo, applicationId))
        }

      def handleInvalidForm(formWithErrors: Form[ChangeSubscriptionForm]) = Future.successful(BadRequest(errorHandler.badRequestTemplate))

      ChangeSubscriptionForm.form.bindFromRequest.fold(handleInvalidForm, handleValidForm);
    }

  def changeLockedApiSubscription(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersion, redirectTo: String): Action[AnyContent] =
    canManageLockedApiSubscriptionsAction(applicationId) { implicit request =>
      applicationService
        .isSubscribedToApi(request.application, apiName, apiContext, apiVersion)
        .map(subscribed =>
          Ok(
            changeSubscriptionConfirmationView(
              applicationViewModelFromApplicationRequest,
              ChangeSubscriptionConfirmationForm.form,
              apiName,
              apiContext,
              apiVersion,
              subscribed,
              redirectTo
            )
          )
        )
    }

  def changeLockedApiSubscriptionAction(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersion, redirectTo: String): Action[AnyContent] =
    canManageLockedApiSubscriptionsAction(applicationId) { implicit request =>
      def requestChangeSubscription(subscribed: Boolean) = {
        if (subscribed) {
          subscriptionsService
            .requestApiUnsubscribe(request.user, request.application, apiName, apiVersion)
            .map(_ => Ok(unsubscribeRequestSubmittedView(applicationViewModelFromApplicationRequest, apiName, apiVersion)))
        } else {
          subscriptionsService
            .requestApiSubscription(request.user, request.application, apiName, apiVersion)
            .map(_ => Ok(subscribeRequestSubmittedView(applicationViewModelFromApplicationRequest, apiName, apiVersion)))
        }
      }

      def handleValidForm(subscribed: Boolean)(form: ChangeSubscriptionConfirmationForm) = form.confirm match {
        case Some(_) => requestChangeSubscription(subscribed)
        case _       => Future.successful(redirect(redirectTo, applicationId))
      }

      def handleInvalidForm(subscribed: Boolean)(formWithErrors: Form[ChangeSubscriptionConfirmationForm]) =
        Future.successful(
          BadRequest(changeSubscriptionConfirmationView(applicationViewModelFromApplicationRequest, formWithErrors, apiName, apiContext, apiVersion, subscribed, redirectTo))
        )

      applicationService
        .isSubscribedToApi(request.application, apiName, apiContext, apiVersion)
        .flatMap(subscribed => ChangeSubscriptionConfirmationForm.form.bindFromRequest.fold(handleInvalidForm(subscribed), handleValidForm(subscribed)))
    }

  private def updateCheckInformation(app: Application)(implicit hc: HeaderCarrier): Future[Any] = {
    app.deployedTo match {
      case Environment.PRODUCTION =>
        applicationService.updateCheckInformation(app, app.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionsConfirmed = false))
      case _ => Future.successful(())
    }
  }
}
