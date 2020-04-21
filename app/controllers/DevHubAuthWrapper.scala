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

import java.security.MessageDigest

import domain.{DeveloperSession, LoggedInState}
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

// TODO : Add some test for this please.
trait DevHubAuthWrapper extends Results with HeaderCarrierConversion with CookieEncoding {

  val sessionService : SessionService

  // TODO: Can / should we make private
  val cookieName2 = "PLAY2AUTH_SESS_ID"

  private val cookieSecureOption: Boolean = false // TODO: Load from config (MUST be true in prod.
  private val cookieHttpOnlyOption: Boolean = true
  private val cookieDomainOption: Option[String] = None
  private val cookiePathOption: String = "/"
  private val cookieMaxAge = Some(900) // 15 mins // TODO: Load from config

  // TODO: Name :(
  implicit def loggedIn2(implicit req : UserRequest[_]) : DeveloperSession = {
    req.developerSession
  }

  def atLeastPartLoggedInEnablingMfa2(body: UserRequest[AnyContent] => Future[Result])
                                     (implicit ec: ExecutionContext): Action[AnyContent] =
    loggedInActionWithFilter(body)(_ => true)


  // TODO: Rename back to loggedInAction (and any other XXX2 methods / classes)
  def loggedInAction2(body: UserRequest[AnyContent] => Future[Result])
                     (implicit ec: ExecutionContext) : Action[AnyContent] =
    loggedInActionWithFilter(body)(_.loggedInState == LoggedInState.LOGGED_IN)

  private def loggedInActionWithFilter(body: UserRequest[AnyContent] => Future[Result])
                                      (filter : DeveloperSession => Boolean)
                                      (implicit ec: ExecutionContext) : Action[AnyContent] = Action.async {

    val loginRedirect = Redirect(controllers.routes.UserLoginAccount.login())

    implicit request: Request[AnyContent] =>
      loadSession.flatMap(maybeSession => {
        maybeSession
          .filter(filter)
          .fold(Future.successful(loginRedirect))(developerSession => body(UserRequest(developerSession,request)))
      })
  }

  def maybeAtLeastPartLoggedInEnablingMfa2(body: MaybeUserRequest[AnyContent] => Future[Result])
                          (implicit ec: ExecutionContext) : Action[AnyContent] = Action.async {
    implicit request: Request[AnyContent] =>
      loadSession.flatMap(
        maybeDeveloperSession => body(MaybeUserRequest(maybeDeveloperSession, request))
      )
  }

  private def fetchDeveloperSession[A](sessionId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[DeveloperSession]] = {
    sessionService
      .fetch(sessionId)
      .map(maybeSession => maybeSession.map(DeveloperSession(_)))
  }

  private def loadSession[A](implicit ec: ExecutionContext, request: Request[A]): Future[Option[DeveloperSession]] = {
    (for {
      cookie <- request.cookies.get(cookieName2)
      sessionId <- decodeCookie(cookie.value)
    } yield fetchDeveloperSession(sessionId))
      .getOrElse(Future.successful(None))
  }

  def withSessionCookie(result : Result, sessionId: String): Result = {
    val c = Cookie(cookieName2,  encodeCookie(sessionId), cookieMaxAge, cookiePathOption, cookieDomainOption, cookieSecureOption, cookieHttpOnlyOption)
    result.withCookies(c)
  }



  def extractSessionIdFromCookie(request: RequestHeader): Option[String] = {
    request.cookies.get(cookieName2) match {
      case Some(cookie) => decodeCookie(cookie.value)
      case _ => None
    }
  }
}

case class UserRequest[A](developerSession: DeveloperSession, request: Request[A]) extends WrappedRequest[A](request)
case class MaybeUserRequest[A](developerSession: Option[DeveloperSession], request: Request[A]) extends WrappedRequest[A](request)

trait CookieEncoding {

  val cookieSigner : CookieSigner

  def encodeCookie(token : String) : String = {
    cookieSigner.sign(token) + token
  }

  def decodeCookie(token : String) : Option[String] = {
    val (hmac, value) = token.splitAt(40)

    val signedValue = cookieSigner.sign(value)

    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
      Some(value)
    } else {
      None
    }
  }
}