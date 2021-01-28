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

package security

import java.security.MessageDigest

import cats.implicits._
import config.ApplicationConfig
import controllers.{routes, BaseController, MaybeUserRequest, UserRequest}
import domain.models.developers.{DeveloperSession, LoggedInState}
import play.api.Logger
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

trait DevHubAuthorization extends Results with FrontendHeaderCarrierProvider with CookieEncoding {
  self: BaseController =>

  private val alwaysTrueFilter: DeveloperSession => Boolean = _ => true
  private val onlyTrueIfLoggedInFilter: DeveloperSession => Boolean = _.loggedInState == LoggedInState.LOGGED_IN

  implicit val appConfig: ApplicationConfig

  val sessionService: SessionService

  implicit def loggedIn(implicit req: UserRequest[_]): DeveloperSession = {
    req.developerSession
  }

  def atLeastPartLoggedInEnablingMfaAction(body: UserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    loggedInActionWithFilter(body)(alwaysTrueFilter)

  def loggedInAction(body: UserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    loggedInActionWithFilter(body)(onlyTrueIfLoggedInFilter)

  private def loggedInActionWithFilter(body: UserRequest[AnyContent] => Future[Result])(filter: DeveloperSession => Boolean)
                                      (implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async {

      val loginRedirect = Redirect(controllers.routes.UserLoginAccount.login())

      implicit request: MessagesRequest[AnyContent] =>
        loadSession.flatMap(maybeSession => {
          maybeSession
            .filter(filter)
            .fold(Future.successful(loginRedirect)) { developerSession =>
              sessionService.updateUserFlowSessions(developerSession.session.sessionId)
                .flatMap(_ => body(UserRequest(developerSession, request)))
            }
        })
    }

  def maybeAtLeastPartLoggedInEnablingMfa(body: MaybeUserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request: MessagesRequest[AnyContent] => loadSession.flatMap(maybeDeveloperSession => body(MaybeUserRequest(maybeDeveloperSession, request)))
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
    Logger.info(s"loginSucceeded - access_uri ${request.session.get("access_uri")}")
    val uri = request.session.get("access_uri").getOrElse(routes.AddApplication.manageApps().url)
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

trait CookieEncoding {
  implicit val appConfig: ApplicationConfig

  private[security] lazy val cookieName = "PLAY2AUTH_SESS_ID"
  private[security] lazy val cookieSecureOption: Boolean = appConfig.securedCookie
  private[security] lazy val cookieHttpOnlyOption: Boolean = true
  private[security] lazy val cookieDomainOption: Option[String] = None
  private[security] lazy val cookiePathOption: String = "/"
  private[security] lazy val cookieMaxAge = appConfig.sessionTimeoutInSeconds.some

  val cookieSigner: CookieSigner

  def createCookie(sessionId: String): Cookie = {
    Cookie(
      cookieName,
      encodeCookie(sessionId),
      cookieMaxAge,
      cookiePathOption,
      cookieDomainOption,
      cookieSecureOption,
      cookieHttpOnlyOption
    )
  }

  def encodeCookie(token: String): String = {
    cookieSigner.sign(token) + token
  }

  def decodeCookie(token: String): Option[String] = {
    val (hmac, value) = token.splitAt(40)

    val signedValue = cookieSigner.sign(value)

    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
      Some(value)
    } else {
      None
    }
  }
}
