/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.security

import cats.implicits._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import controllers.{routes, BaseController, MaybeUserRequest, UserRequest}
import domain.models.developers.{DeveloperSession, LoggedInState}
import play.api.mvc._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider
import cats.data.OptionT
import cats.data.EitherT
import uk.gov.hmrc.modules.common.services.ApplicationLogger

import scala.concurrent.{ExecutionContext, Future}

trait DevHubAuthorization extends FrontendHeaderCarrierProvider with CookieEncoding with ApplicationLogger {
  self: BaseController =>
    
  implicit val appConfig: ApplicationConfig

  val sessionService: SessionService

  object DeveloperSessionFilter {
    type Type = DeveloperSession => Boolean

    val alwaysTrueFilter: DeveloperSessionFilter.Type = _ => true
    val onlyTrueIfLoggedInFilter: DeveloperSessionFilter.Type = _.loggedInState == LoggedInState.LOGGED_IN
  }

  def loggedInActionRefiner(filter: DeveloperSessionFilter.Type = DeveloperSessionFilter.onlyTrueIfLoggedInFilter): ActionRefiner[MessagesRequest, UserRequest] = 
    new ActionRefiner[MessagesRequest, UserRequest] {
      def executionContext = ec
      def refine[A](msgRequest: MessagesRequest[A]): Future[Either[Result, UserRequest[A]]] = {
        lazy val loginRedirect = Redirect(routes.UserLoginAccount.login())

        implicit val request = msgRequest

        OptionT(loadSession)
        .filter(filter)
        .toRight(loginRedirect)
        .flatMap(ds => {
          EitherT.liftF[Future, Result, UserRequest[A]](
            sessionService.updateUserFlowSessions(ds.session.sessionId)
            .map(_ => new UserRequest(ds, msgRequest))
          )
        })
        .value
      }
    }   
      
  def atLeastPartLoggedInEnablingMfaAction(block: UserRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      loggedInActionRefiner(DeveloperSessionFilter.alwaysTrueFilter).invokeBlock(request, block)
    }

  def loggedInAction(block: UserRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      loggedInActionRefiner(DeveloperSessionFilter.onlyTrueIfLoggedInFilter).invokeBlock(request, block)
    }

  def maybeAtLeastPartLoggedInEnablingMfa(body: MaybeUserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] => loadSession.flatMap(maybeDeveloperSession => body(new MaybeUserRequest(maybeDeveloperSession, request)))
  }

  private[security] def loadSession[A](implicit ec: ExecutionContext, request: Request[A]): Future[Option[DeveloperSession]] = {
    (for {
      cookie <- request.cookies.get(cookieName)
      sessionId <- decodeCookie(cookie.value)
    } yield fetchDeveloperSession(sessionId))
      .getOrElse(Future.successful(None))
  }

  private def fetchDeveloperSession[A](sessionId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[DeveloperSession]] = {
    sessionService
      .fetch(sessionId)
      .map(maybeSession => maybeSession.map(DeveloperSession(_)))
  }
}

trait ExtendedDevHubAuthorization extends DevHubAuthorization {
  self: BaseController =>
  def loggedOutAction(body: MessagesRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] =>
      loadSession.flatMap {
        case Some(developerSession) if developerSession.loggedInState.isLoggedIn => loginSucceeded(request)
        case _                                                                   => body(request)
      }
  }

  def loginSucceeded(request: RequestHeader): Future[Result] = {
    logger.info(s"loginSucceeded - access_uri ${request.session.get("access_uri")}")
    val uri = request.session.get("access_uri").getOrElse(routes.ManageApplications.manageApps().url)
    Future.successful(Redirect(uri).withNewSession)
  }

  def withSessionCookie(result: Result, sessionId: String): Result = {
    result.withCookies(createCookie(sessionId))
  }

  def extractSessionIdFromCookie(request: RequestHeader): Option[String] = {
    request.cookies.get(cookieName) match {
      case Some(cookie) => decodeCookie(cookie.value)
      case _            => None
    }
  }

  def destroySession(request: RequestHeader)(implicit hc: HeaderCarrier): Option[Future[Int]] = {
    extractSessionIdFromCookie(request)
      .map(sessionId => sessionService.destroy(sessionId))
  }

  def removeCookieFromResult(result: Result): Result = {
    result.discardingCookies(DiscardingCookie(cookieName))
  }
}
