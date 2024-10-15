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

package uk.gov.hmrc.thirdpartydeveloperfrontend.security

import scala.concurrent.{ExecutionContext, Future}

import cats.data.{EitherT, OptionT}
import cats.implicits._

import play.api.mvc.{Action, _}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{MaybeUserRequest, TpdfeBaseController, UserRequest, routes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

trait DevHubAuthorization extends CookieEncoding with ApplicationLogger {
  self: FrontendController =>

  implicit def ec: ExecutionContext

  def sessionService: SessionService

  object DeveloperSessionFilter {
    type Type = UserSession => Boolean

    val alwaysTrueFilter: DeveloperSessionFilter.Type         = _ => true
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
          .flatMap(userSession => {
            EitherT.liftF[Future, Result, UserRequest[A]](
              sessionService.updateUserFlowSessions(userSession.sessionId)
                .map { _ => new UserRequest(userSession, msgRequest) }
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

  def removeDeviceSessionCookieFromResult(result: Result): Result = {
    result.discardingCookies(DiscardingCookie(devicecookieName))
  }

  private[security] def loadSession[A](implicit request: Request[A]): Future[Option[UserSession]] = {
    (for {
      sessionId <- extractUserSessionIdFromCookie(request)
    } yield fetchDeveloperSession(sessionId))
      .getOrElse(Future.successful(None))
  }

  private def fetchDeveloperSession[A](sessionId: UserSessionId)(implicit hc: HeaderCarrier): Future[Option[UserSession]] = {
    sessionService.fetch(sessionId)
  }
}

trait ExtendedDevHubAuthorization extends DevHubAuthorization {
  self: TpdfeBaseController =>

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

  def withSessionCookie(result: Result, sessionId: UserSessionId): Result = {
    result.withCookies(createUserCookie(sessionId))
  }

  def withSessionAndDeviceCookies(result: Result, sessionId: UserSessionId, deviceSessionId: String): Result = {
    result.withCookies(createUserCookie(sessionId), createDeviceCookie(deviceSessionId))
  }

  def destroyUserSession(request: RequestHeader)(implicit hc: HeaderCarrier): Option[Future[Int]] = {
    extractUserSessionIdFromCookie(request)
      .map(sessionId => sessionService.destroy(sessionId))
  }

  def removeUserSessionCookieFromResult(result: Result): Result = {
    result.discardingCookies(DiscardingCookie(cookieName))
  }

}
