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
import config.{ApplicationConfig, AuthConfigImpl, ErrorHandler}
import domain._
import jp.t2v.lab.play2.auth.{AuthElement, OptionalAuthElement}
import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAttributes}
import model.ApplicationViewModel
import play.api.i18n.I18nSupport
import play.api.mvc._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

case object AppKey extends RequestAttributeKey[Future[Application]]

trait HeaderEnricher {

  def enrichHeaders(hc: HeaderCarrier, user: Option[DeveloperSession]) =
    user match {
      case Some(dev) => hc.withExtraHeaders("X-email-address" -> dev.email, "X-name" -> dev.displayedNameEncoded)
      case _ => hc
    }
}

// TODO : Remove AuthElement
abstract class LoggedInController extends BaseController with AuthElement with DevHubAuthWrapper {

  // TODO: Get rid Play2-auth
  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: RequestWithAttributes[_] => enrichHeaders(carrier, Some(loggedIn(x)))
      case x: ApplicationRequest[_] => enrichHeaders(carrier, Some(x.user))
      case _ => carrier
    }

  }

  // TODO: Get rid Play2-auth
  def atLeastPartLoggedInEnablingMfa(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = {
    AsyncStack(AuthorityKey -> AtLeastPartLoggedInEnablingMfa) {
      f
    }
  }
}

case class ApplicationRequest[A](application: Application, subscriptions: Seq[APISubscriptionStatus], role: Role, user: DeveloperSession, request: Request[A])
  extends WrappedRequest[A](request)

case class ApplicationWithFieldDefinitionsRequest[A](fieldDefinitions: NonEmptyList[APISubscriptionStatus], applicationRequest: ApplicationRequest[A])
  extends WrappedRequest[A](applicationRequest)

abstract class ApplicationController()
  extends LoggedInController with ActionBuilders {

  implicit def userFromRequest(implicit request: ApplicationRequest[_]): DeveloperSession = request.user

  def applicationViewModelFromApplicationRequest()(implicit request: ApplicationRequest[_]): ApplicationViewModel =
    ApplicationViewModel(request.application, request.subscriptions.exists(s => s.subscribed && s.fields.isDefined))

  def whenTeamMemberOnApp(applicationId: String)
                         (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    loggedInAction2 { implicit request =>
      val stackedActions = Action andThen applicationAction(applicationId, loggedIn2)
      stackedActions.async(fun)(request)
    }
  
  def capabilityThenPermissionsAction(capability: Capability, permissions: Permission)
                                     (applicationId: String)
                                     (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction2 { implicit request =>
      val stackedActions = Action andThen applicationAction(applicationId, loggedIn2) andThen capabilityFilter(capability) andThen permissionFilter(permissions)
      stackedActions.async(fun)(request)
    }
  }

  def permissionThenCapabilityAction(permissions: Permission, capability: Capability)
                                    (applicationId: String)
                                    (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction2 { implicit request =>
      val stackedActions = Action andThen applicationAction(applicationId, loggedIn2) andThen permissionFilter(permissions) andThen capabilityFilter(capability)
      stackedActions.async(fun)(request)
    }
  }

  def subFieldsDefinitionsExistAction(applicationId: String)
                                    (fun: ApplicationWithFieldDefinitionsRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    loggedInAction2 { implicit request =>
      val stackedActions =
        Action andThen applicationAction(applicationId, loggedIn2) andThen fieldDefinitionsExistRefiner
      stackedActions.async(fun)(request)
    }
  }
}


abstract class LoggedOutController()
  extends BaseController() with OptionalAuthElement {

  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: RequestWithAttributes[_] => implicit val req = x
        enrichHeaders(carrier, loggedIn)
      case _ => carrier
    }
  }

  def loggedOutAction(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = {
    AsyncStack {
      implicit request =>
        loggedIn match {
          case Some(session) if session.loggedInState.isLoggedIn => loginSucceeded(request)
          case Some(_) | None => f(request)
        }
    }
  }
}

trait HeaderCarrierConversion
  extends uk.gov.hmrc.play.bootstrap.controller.BaseController
    with uk.gov.hmrc.play.bootstrap.controller.Utf8MimeTypes {

  override implicit def hc(implicit rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSessionAndRequest(rh.headers, Some(rh.session), Some(rh))
}

abstract class BaseController()
  extends AuthConfigImpl with I18nSupport with HeaderCarrierConversion with HeaderEnricher {

  val errorHandler: ErrorHandler
  val sessionService: SessionService

  implicit def ec: ExecutionContext

  override implicit val appConfig: ApplicationConfig

  def ensureLoggedOut(implicit request: Request[_], hc: HeaderCarrier) = {
    tokenAccessor.extract(request).map(sessionService.destroy).getOrElse(Future.successful(()))
  }
}
