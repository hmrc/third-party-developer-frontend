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

import config.{ApplicationConfig, AuthConfigImpl, ErrorHandler}
import domain._
import jp.t2v.lab.play2.auth.{AuthElement, OptionalAuthElement}
import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAttributes}
import play.api.i18n.I18nSupport
import play.api.mvc._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

case object AppKey extends RequestAttributeKey[Future[Application]]

trait HeaderEnricher {

  def enrichHeaders(hc: HeaderCarrier, user: Option[DeveloperSession]) =
    user match {
      case Some(dev) => hc.withExtraHeaders("X-email-address" -> dev.email, "X-name" -> dev.displayedNameEncoded)
      case _ => hc
    }
}

abstract class LoggedInController extends BaseController with AuthElement {
  implicit def hc(implicit request: Request[_]): HeaderCarrier = {
    val carrier = super.hc
    request match {
      case x: RequestWithAttributes[_] => enrichHeaders(carrier, Some(loggedIn(x)))
      case x: ApplicationRequest[_] => enrichHeaders(carrier, Some(x.user))
      case _ => carrier
    }

  }

  def loggedInAction(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = {
    AsyncStack(AuthorityKey -> LoggedInUser) {
      f
    }
  }

  // TODO: Create a partAuthenticated action, or PartLoggedInController?

  def adminAction(app: Future[Application])(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = {
    AsyncStack(AuthorityKey -> AppAdmin(app), AppKey -> app) {
      f
    }
  }

  def teamMemberAction(app: Future[Application])(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = {
    AsyncStack(AuthorityKey -> AppTeamMember(app), AppKey -> app) {
      f
    }
  }

}

case class ApplicationRequest[A](application: Application, role: Role, user: DeveloperSession, request: Request[A]) extends WrappedRequest[A](request)

abstract class ApplicationController()
  extends LoggedInController with ActionBuilders {

  implicit def userFromRequest(implicit request: ApplicationRequest[_]): User = request.user

  def adminOnStandardApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                        (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    teamMemberOnApp(applicationId, Seq(notRopcOrPrivilegedAppFilter, adminOnAppFilter) ++: furtherActionFunctions)(fun)

  def adminIfStandardProductionApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                                  (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    teamMemberOnApp(applicationId, Seq(notRopcOrPrivilegedAppFilter, adminIfProductionAppFilter) ++: furtherActionFunctions)(fun)

  def teamMemberOnStandardApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                             (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    teamMemberOnApp(applicationId, notRopcOrPrivilegedAppFilter +: furtherActionFunctions)(fun)

  def adminOnApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    teamMemberOnApp(applicationId, Seq(adminOnAppFilter) ++: furtherActionFunctions)(fun)

  def adminOnTestingApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                       (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    teamMemberOnApp(applicationId, Seq(adminOnAppFilter, appInStateTestingFilter) ++: furtherActionFunctions)(fun)

  def teamMemberOnApp(applicationId: String, furtherActionFunctions: Seq[ActionFunction[ApplicationRequest, ApplicationRequest]] = Seq.empty)
                     (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    loggedInAction { implicit request =>
      val stackedActions = furtherActionFunctions.foldLeft(Action andThen applicationAction(applicationId, loggedIn))((a, af) => a.andThen(af))
      stackedActions.async(fun)(request)
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
          case Some(_) => loginSucceeded(request)
          case None => f(request)
        }
    }
  }
}

abstract class BaseController()
  extends AuthConfigImpl with I18nSupport with FrontendController with HeaderEnricher {

  val errorHandler: ErrorHandler
  val sessionService: SessionService
  override implicit val appConfig: ApplicationConfig

  def ensureLoggedOut(implicit request: Request[_], hc: HeaderCarrier) = {
    tokenAccessor.extract(request).map(sessionService.destroy).getOrElse(Future.successful(()))
  }
}
