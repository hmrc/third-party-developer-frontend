/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.helper.EnvironmentNameService
import views.html.include.ChangeSubscriptionConfirmationView
import views.html.{AddAppSubscriptionsView, ManageSubscriptionsView, SubscribeRequestSubmittedView, UnsubscribeRequestSubmittedView}

import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, Result}
import play.twirl.api.Html
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{CheckInformation}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ManageLockedSubscriptions, SupportsSubscriptions}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{AdministratorOnly, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.SubscriptionRedirect
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.SubscriptionRedirect._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator

@Singleton
class SubscriptionsController @Inject() (
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
    subscribeRequestSubmittedView: SubscribeRequestSubmittedView,
    fraudPreventionConfig: FraudPreventionConfig
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig,
    val environmentNameService: EnvironmentNameService
  ) extends ApplicationController(mcc)
    with ApplicationHelper with FraudPreventionNavLinkHelper with WithUnsafeDefaultFormBinding {

  private def canManagePrivateApiSubscriptionsAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForAllStates(SupportsSubscriptions, AdministratorOnly)(applicationId)(fun)

  private def canManageLockedApiSubscriptionsAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForAllStates(ManageLockedSubscriptions, AdministratorOnly)(applicationId)(fun)

  private def canViewSubscriptionsInDevHubAction(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]) =
    checkActionForAllStates(SupportsSubscriptions, TeamMembersOnly)(applicationId)(fun)

  def manageSubscriptions(applicationId: ApplicationId): Action[AnyContent] = canViewSubscriptionsInDevHubAction(applicationId) { implicit request =>
    renderSubscriptions(
      request.application,
      request.userSession,
      (role: Collaborator.Role, data: PageData, form: Form[EditApplicationForm]) => {
        manageSubscriptionsView(
          role,
          data,
          form,
          applicationViewModelFromApplicationRequest(),
          data.subscriptions,
          data.openAccessApis,
          data.app.id,
          createOptionalFraudPreventionNavLinkViewModel(request.application, request.subscriptions, fraudPreventionConfig)
        )
      }
    )
  }

  def addAppSubscriptions(applicationId: ApplicationId): Action[AnyContent] = canViewSubscriptionsInDevHubAction(applicationId) { implicit request =>
    renderSubscriptions(
      request.application,
      request.userSession,
      (role: Collaborator.Role, data: PageData, form: Form[EditApplicationForm]) => {
        addAppSubscriptionsView(role, data, form, request.application, request.application.deployedTo, data.subscriptions, data.openAccessApis)
      }
    )
  }

  def renderSubscriptions(
      application: ApplicationWithCollaborators,
      userSession: UserSession,
      renderHtml: (Collaborator.Role, PageData, Form[EditApplicationForm]) => Html
    )(implicit request: ApplicationRequest[AnyContent]
    ): Future[Result] = {
    val subsData = APISubscriptions.groupSubscriptions(request.subscriptions)
    val form     = EditApplicationForm.withData(request.application)

    val html = renderHtml(request.role, PageData(request.application, subsData, request.openAccessApis), form)

    Future.successful(Ok(html))
  }

  private def redirect(redirectTo: String, applicationId: ApplicationId) = SubscriptionRedirect(redirectTo) match {
    case Some(MANAGE_PAGE)            => Redirect(routes.Details.details(applicationId))
    case Some(API_SUBSCRIPTIONS_PAGE) => Redirect(routes.SubscriptionsController.manageSubscriptions(applicationId))
    case None                         => Redirect(routes.Details.details(applicationId))
  }

  def changeApiSubscription(applicationId: ApplicationId, apiContext: ApiContext, apiVersion: ApiVersionNbr, redirectTo: String): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>
      val apiIdentifier   = ApiIdentifier(apiContext, apiVersion)
      val requestingEmail = request.userSession.developer.email

      def updateSubscription(form: ChangeSubscriptionForm) = form.subscribed match {
        case Some(subscribe) =>
          def service = if (subscribe) subscriptionsService.subscribeToApi _ else subscriptionsService.unsubscribeFromApi _

          service(request.application, apiIdentifier, requestingEmail) andThen { case _ => updateCheckInformation(request.application) }
        case _               =>
          Future.successful(ApplicationUpdateSuccessful)
      }

      def handleValidForm(form: ChangeSubscriptionForm) =
        if (request.application.areSubscriptionsLocked) {
          import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.Error._
          Future.successful(BadRequest(Json.toJson(BadRequestError)))
        } else {
          updateSubscription(form).map(_ => redirect(redirectTo, applicationId))
        }

      def handleInvalidForm(formWithErrors: Form[ChangeSubscriptionForm]) = errorHandler.badRequestTemplate.map(BadRequest(_))

      ChangeSubscriptionForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm);
    }

  def requestChangeApiSubscription(
      applicationId: ApplicationId,
      apiName: String,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      redirectTo: String,
      call: Call
    ): ApplicationRequest[AnyContent] => Future[Result] =
    (request: ApplicationRequest[AnyContent]) => {
      val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
      implicit val r    = request

      subscriptionsService
        .isSubscribedToApi(request.application.id, apiIdentifier)
        .map(subscribed =>
          Ok(
            changeSubscriptionConfirmationView(
              applicationViewModelFromApplicationRequest(),
              ChangeSubscriptionConfirmationForm.form,
              apiName,
              apiContext,
              apiVersion,
              subscribed,
              redirectTo,
              call
            )
          )
        )
    }

  def changeLockedApiSubscription(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersionNbr, redirectTo: String): Action[AnyContent] =
    canManageLockedApiSubscriptionsAction(applicationId) {
      val call: Call = routes.SubscriptionsController.changeLockedApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo.toString)
      requestChangeApiSubscription(applicationId, apiName, apiContext, apiVersion, redirectTo, call)
    }

  def changePrivateApiSubscription(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersionNbr, redirectTo: String): Action[AnyContent] =
    canManagePrivateApiSubscriptionsAction(applicationId) {
      val call: Call = routes.SubscriptionsController.changePrivateApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo)
      requestChangeApiSubscription(applicationId, apiName, apiContext, apiVersion, redirectTo, call)
    }

  def requestChangeApiSubscriptionAction(
      applicationId: ApplicationId,
      apiName: String,
      apiContext: ApiContext,
      apiVersion: ApiVersionNbr,
      redirectTo: String,
      call: Call
    ): ApplicationRequest[AnyContent] => Future[Result] =
    (request: ApplicationRequest[AnyContent]) => {
      val apiIdentifier = ApiIdentifier(apiContext, apiVersion)

      implicit val r = request

      def requestChangeSubscription(subscribed: Boolean) = {
        if (subscribed) {
          subscriptionsService
            .requestApiUnsubscribe(request.userSession, request.application, apiName, apiVersion)
            .map(_ => Ok(unsubscribeRequestSubmittedView(applicationViewModelFromApplicationRequest(), apiName, apiVersion)))
        } else {
          subscriptionsService
            .requestApiSubscription(request.userSession, request.application, apiName, apiVersion)
            .map(_ => Ok(subscribeRequestSubmittedView(applicationViewModelFromApplicationRequest(), apiName, apiVersion)))
        }
      }

      def handleValidForm(subscribed: Boolean)(form: ChangeSubscriptionConfirmationForm) = form.confirm match {
        case Some(true) => requestChangeSubscription(subscribed)
        case _          => Future.successful(redirect(redirectTo, applicationId))
      }

      def handleInvalidForm(subscribed: Boolean)(formWithErrors: Form[ChangeSubscriptionConfirmationForm]) =
        Future.successful(
          BadRequest(
            changeSubscriptionConfirmationView(
              applicationViewModelFromApplicationRequest(),
              formWithErrors,
              apiName,
              apiContext,
              apiVersion,
              subscribed,
              redirectTo,
              call
            )
          )
        )

      subscriptionsService
        .isSubscribedToApi(request.application.id, apiIdentifier)
        .flatMap(subscribed => ChangeSubscriptionConfirmationForm.form.bindFromRequest().fold(handleInvalidForm(subscribed), handleValidForm(subscribed)))
    }

  def changeLockedApiSubscriptionAction(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersionNbr, redirectTo: String): Action[AnyContent] =
    canManageLockedApiSubscriptionsAction(applicationId) {
      val call: Call = routes.SubscriptionsController.changeLockedApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo)
      requestChangeApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo, call)
    }

  def changePrivateApiSubscriptionAction(applicationId: ApplicationId, apiName: String, apiContext: ApiContext, apiVersion: ApiVersionNbr, redirectTo: String): Action[AnyContent] =
    canManagePrivateApiSubscriptionsAction(applicationId) {
      val call: Call = routes.SubscriptionsController.changePrivateApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo)
      requestChangeApiSubscriptionAction(applicationId, apiName, apiContext, apiVersion, redirectTo, call)
    }

  private def updateCheckInformation(app: ApplicationWithCollaborators)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    app.deployedTo match {
      case Environment.PRODUCTION =>
        applicationService.updateCheckInformation(app, app.details.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionsConfirmed = false))
      case _                      => Future.successful(ApplicationUpdateSuccessful)
    }
  }
}
