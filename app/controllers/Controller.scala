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

import cats.data.NonEmptyList
import config.{ApplicationConfig, ErrorHandler}
import controllers.ManageSubscriptions.ApiDetails
import domain._
import model.{ApplicationViewModel, NoSubscriptionFieldsRefinerBehaviour}
import play.api.mvc._
import security.{DevHubAuthorization, ExtendedDevHubAuthorization}
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

trait HeaderEnricher {
  def enrichHeaders(hc: HeaderCarrier, user: Option[DeveloperSession]) : HeaderCarrier =
    user match {
      case Some(dev) => enrichHeaders(hc, dev)
      case _ => hc
    }

  def enrichHeaders(hc: HeaderCarrier, user: DeveloperSession) : HeaderCarrier =
     hc.withExtraHeaders("X-email-address" -> user.email, "X-name" -> user.displayedNameEncoded)

  implicit class RequestWithAjaxSupport(h: Headers) {
    def isAjaxRequest: Boolean = h.get("X-Requested-With").contains("XMLHttpRequest")
  }
}

case class UserRequest[A](developerSession: DeveloperSession, request: MessagesRequest[A])
  extends MessagesRequest[A](request, request.messagesApi)

case class MaybeUserRequest[A](developerSession: Option[DeveloperSession], request: MessagesRequest[A])
  extends MessagesRequest[A](request, request.messagesApi)

case class ApplicationRequest[A](application: Application, deployedTo: Environment, subscriptions: Seq[APISubscriptionStatus], role: Role, user: DeveloperSession, request: MessagesRequest[A])
  extends MessagesRequest[A](request, request.messagesApi)

case class ApplicationWithFieldDefinitionsRequest[A]( fieldDefinitions: NonEmptyList[APISubscriptionStatusWithSubscriptionFields],
                                                      applicationRequest: ApplicationRequest[A])
  extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

case class ApplicationWithSubscriptionFieldPage[A]( pageIndex: Int,
                                                    totalPages: Int,
                                                    apiSubscriptionStatus: APISubscriptionStatusWithSubscriptionFields,
                                                    apiDetails: ApiDetails,
                                                    applicationRequest: ApplicationRequest[A])
  extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

  case class ApplicationWithSubscriptionFields[A](  apiSubscription: APISubscriptionStatusWithSubscriptionFields,
                                                    applicationRequest: ApplicationRequest[A])
  extends MessagesRequest[A](applicationRequest, applicationRequest.messagesApi)

abstract class BaseController(mcc: MessagesControllerComponents) extends FrontendController(mcc) with DevHubAuthorization with HeaderEnricher {
  val errorHandler: ErrorHandler
  val sessionService: SessionService

  implicit def ec: ExecutionContext

  implicit val appConfig: ApplicationConfig
}

abstract class LoggedInController(mcc: MessagesControllerComponents) extends BaseController(mcc)

abstract class ApplicationController(mcc: MessagesControllerComponents)
  extends LoggedInController(mcc) with ActionBuilders {

  implicit def userFromRequest(implicit request: ApplicationRequest[_]): DeveloperSession = request.user

  def applicationViewModelFromApplicationRequest()(implicit request: ApplicationRequest[_]): ApplicationViewModel =
    ApplicationViewModel(request.application, hasSubscriptionFields(request))

  def hasSubscriptionFields(request: ApplicationRequest[_]) : Boolean = {
    request.subscriptions.exists(s => s.subscribed && s.fields.fields.nonEmpty)
  }

  def whenTeamMemberOnApp(applicationId: String)
                         (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    loggedInAction { implicit request =>
      val composedActions = Action andThen applicationAction(applicationId, loggedIn)
      composedActions.async(fun)(request)
    }

  private def checkActionWithStateCheck(stateCheck: State => Boolean)(capability: Capability, permissions: Permission)
                                                            (applicationId: String)
                                                            (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction { implicit request =>
      val composedActions = Action andThen
                            applicationAction(applicationId, loggedIn) andThen
                            capabilityFilter(capability) andThen
                            permissionFilter(permissions) andThen
                            approvalFilter(stateCheck)
        
      composedActions.async(fun)(request)
    }
  }

  def checkActionForAllStates = checkActionWithStateCheck(stateCheck = _ => true) _

  def checkActionForApprovedApps = checkActionWithStateCheck(_.isApproved) _
  
  def checkActionForApprovedOrTestingApps = checkActionWithStateCheck(state => state.isApproved || state.isInTesting) _

  def checkActionForTesting = checkActionWithStateCheck(_.isInTesting) _

  private object ManageSubscriptionsActions {
    def subscriptionsComposedActions(applicationId: String, noFieldsBehaviour : NoSubscriptionFieldsRefinerBehaviour)
                      (implicit request: UserRequest[AnyContent]) =
      Action andThen
        applicationAction(applicationId, loggedIn) andThen
        capabilityFilter(Capabilities.EditSubscriptionFields) andThen
        fieldDefinitionsExistRefiner(noFieldsBehaviour)
  }

  def subFieldsDefinitionsExistAction(applicationId: String,
                                      noFieldsBehaviour : NoSubscriptionFieldsRefinerBehaviour = NoSubscriptionFieldsRefinerBehaviour.BadRequest)
                                    (fun: ApplicationWithFieldDefinitionsRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction { implicit request: UserRequest[AnyContent] =>
      ManageSubscriptionsActions
        .subscriptionsComposedActions(applicationId, noFieldsBehaviour)
        .async(fun)(request)
    }
  }

  def subFieldsDefinitionsExistActionWithPageNumber(applicationId: String, pageNumber: Int)
                                                   (fun: ApplicationWithSubscriptionFieldPage[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction { implicit request =>
      (ManageSubscriptionsActions
        .subscriptionsComposedActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen subscriptionFieldPageRefiner(pageNumber))
        .async(fun)(request)
    }
  }

  def subFieldsDefinitionsExistActionByApi(applicationId: String, context: String, version: String)
                                                   (fun: ApplicationWithSubscriptionFields[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction { implicit request =>
      (ManageSubscriptionsActions
        .subscriptionsComposedActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen subscriptionFieldsRefiner(context, version))
        .async(fun)(request)
    }
  }
}

abstract class LoggedOutController(mcc: MessagesControllerComponents)
  extends BaseController(mcc) with ExtendedDevHubAuthorization {

  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: MaybeUserRequest[_] =>
        implicit val req: MaybeUserRequest[_] = x
        enrichHeaders(carrier, x.developerSession)
      case x: UserRequest[_] =>
        implicit val req: UserRequest[_] = x
        enrichHeaders(carrier, x.developerSession)
      case _ => carrier
    }
  }
}
