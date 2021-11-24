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

import cats.data.NonEmptyList
import config.{ApplicationConfig, ErrorHandler}
import controllers.ManageSubscriptions.ApiDetails
import domain.models.apidefinitions.{ApiContext, APISubscriptionStatus, APISubscriptionStatusWithSubscriptionFields, APISubscriptionStatusWithWritableSubscriptionField, ApiVersion}
import domain.models.applications._
import domain.models.developers.DeveloperSession
import domain.models.controllers.ApplicationViewModel
import domain.models.controllers.NoSubscriptionFieldsRefinerBehaviour
import play.api.mvc._
import security.{DevHubAuthorization, ExtendedDevHubAuthorization}
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}
import service.ApplicationService
import domain.models.subscriptions.ApiData

trait HeaderEnricher {
  def enrichHeaders(hc: HeaderCarrier, user: Option[DeveloperSession]): HeaderCarrier =
    user match {
      case Some(dev) => enrichHeaders(hc, dev)
      case _         => hc
    }

  def enrichHeaders(hc: HeaderCarrier, user: DeveloperSession): HeaderCarrier =
    hc.withExtraHeaders("X-email-address" -> user.email, "X-name" -> user.displayedNameEncoded)

  implicit class RequestWithAjaxSupport(h: Headers) {
    def isAjaxRequest: Boolean = h.get("X-Requested-With").contains("XMLHttpRequest")
  }
}

class UserRequest[A](val developerSession: DeveloperSession, val msgRequest: MessagesRequest[A]) extends MessagesRequest[A](msgRequest, msgRequest.messagesApi) {
  lazy val userId = developerSession.developer.userId
}

class MaybeUserRequest[A](val developerSession: Option[DeveloperSession], request: MessagesRequest[A]) extends MessagesRequest[A](request, request.messagesApi)

class ApplicationRequest[A](
    val application: Application,
    val deployedTo: Environment,
    val subscriptions: List[APISubscriptionStatus],
    val openAccessApis: Map[ApiContext,ApiData],
    val role: CollaboratorRole,
    val userRequest: UserRequest[A]
) extends UserRequest[A](userRequest.developerSession, userRequest.msgRequest) with modules.submissions.controllers.HasApplication {
  def hasSubscriptionFields: Boolean = {
    subscriptions.exists(s => s.subscribed && s.fields.fields.nonEmpty)
  }
}

class ApplicationWithFieldDefinitionsRequest[A](
  val fieldDefinitions: NonEmptyList[APISubscriptionStatusWithSubscriptionFields],
  val applicationRequest: ApplicationRequest[A]
) extends ApplicationRequest[A](
  applicationRequest.application,
  applicationRequest.deployedTo,
  applicationRequest.subscriptions,
  applicationRequest.openAccessApis,
  applicationRequest.role,
  applicationRequest.userRequest
)

case class ApplicationWithSubscriptionFieldPage[A](
    pageIndex: Int,
    totalPages: Int,
    apiSubscriptionStatus: APISubscriptionStatusWithSubscriptionFields,
    apiDetails: ApiDetails,
    applicationRequest: ApplicationRequest[A]
) extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

case class ApplicationWithSubscriptionFields[A](apiSubscription: APISubscriptionStatusWithSubscriptionFields, applicationRequest: ApplicationRequest[A])
    extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

case class ApplicationWithWritableSubscriptionField[A](
  subscriptionWithSubscriptionField: APISubscriptionStatusWithWritableSubscriptionField,
  applicationRequest: ApplicationRequest[A])
  extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

abstract class BaseController(mcc: MessagesControllerComponents) extends FrontendController(mcc) with DevHubAuthorization with HeaderEnricher {
  val errorHandler: ErrorHandler
  val sessionService: SessionService

  implicit def ec: ExecutionContext

  implicit val appConfig: ApplicationConfig
}

abstract class LoggedInController(mcc: MessagesControllerComponents) extends BaseController(mcc) {
  implicit def developerSessionFromRequest(implicit request: UserRequest[_]): DeveloperSession = request.developerSession
}

abstract class ApplicationController(mcc: MessagesControllerComponents) extends LoggedInController(mcc) with ActionBuilders {
  val applicationService: ApplicationService

  def applicationViewModelFromApplicationRequest()(implicit request: ApplicationRequest[_]): ApplicationViewModel =
    ApplicationViewModel(request.application, request.hasSubscriptionFields, hasPpnsFields(request))

  def whenTeamMemberOnApp(applicationId: ApplicationId)(block: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId)
      ).invokeBlock(request, block)
    }
    
  private def checkActionWithStateCheck(
      stateCheck: State => Boolean
  )(capability: Capability, permissions: Permission)(applicationId: ApplicationId)(block: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId) andThen
        capabilityFilter(capability) andThen
        permissionFilter(permissions) andThen
        approvalFilter(stateCheck)
      ).invokeBlock(request, block)
    }
  }

  def checkActionForAllStates = checkActionWithStateCheck(stateCheck = _ => true) _

  def checkActionForApprovedApps = checkActionWithStateCheck(_.isApproved) _

  def checkActionForApprovedOrTestingApps = checkActionWithStateCheck(state => state.isApproved || state.isInTesting) _

  def checkActionForTesting = checkActionWithStateCheck(_.isInTesting) _


  private def subscriptionsBaseActions(applicationId: ApplicationId, noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour) = 
    loggedInActionRefiner() andThen
    applicationRequestRefiner(applicationId) andThen
    capabilityFilter(Capabilities.EditSubscriptionFields) andThen
    fieldDefinitionsExistRefiner(noFieldsBehaviour)

  def subFieldsDefinitionsExistAction(
    applicationId: ApplicationId,
    noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour = NoSubscriptionFieldsRefinerBehaviour.BadRequest
  )(block: ApplicationWithFieldDefinitionsRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, noFieldsBehaviour)
      )
      .invokeBlock(request, block)
    }
  }

  def subFieldsDefinitionsExistActionWithPageNumber(
    applicationId: ApplicationId,
    pageNumber: Int
  )(block: ApplicationWithSubscriptionFieldPage[AnyContent] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
        subscriptionFieldPageRefiner(pageNumber)
      )
      .invokeBlock(request, block)
    }
  }

  def subFieldsDefinitionsExistActionByApi(applicationId: ApplicationId, context: ApiContext, version: ApiVersion)(
      block: ApplicationWithSubscriptionFields[AnyContent] => Future[Result]
  ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
        subscriptionFieldsRefiner(context, version)
      )
      .invokeBlock(request, block)
    }
  }

  def singleSubFieldsWritableDefinitionActionByApi(applicationId: ApplicationId, context: ApiContext, version: ApiVersion, fieldName: String)(
      block: ApplicationWithWritableSubscriptionField[AnyContent] => Future[Result]
  ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
        subscriptionFieldsRefiner(context, version) andThen
        writeableSubscriptionFieldRefiner(fieldName)
      )
      .invokeBlock(request, block)
    }
  }

  def subscribedToApiWithPpnsFieldAction(applicationId: ApplicationId, capability: Capability, permissions: Permission)
                                        (block: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId) andThen
        capabilityFilter(capability) andThen
        permissionFilter(permissions) andThen
        subscribedToApiWithPpnsFieldFilter
      )
      .invokeBlock(request, block)  
    }
  }
}

abstract class LoggedOutController(mcc: MessagesControllerComponents) extends BaseController(mcc) with ExtendedDevHubAuthorization {

  implicit def developerSessionFromRequest(implicit request: UserRequest[_]): DeveloperSession = request.developerSession

  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: MaybeUserRequest[_] => enrichHeaders(carrier, x.developerSession)
      case x: UserRequest[_]      => enrichHeaders(carrier, x.developerSession)
      case _                      => carrier
    }
  }
}
