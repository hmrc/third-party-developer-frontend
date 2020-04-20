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
import play.api.libs.Crypto
import play.api.mvc._
import play.api.Logger
import play.api.mvc.Results.Redirect
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait DevHubAuthWrapper extends Results with HeaderCarrierConversion {

  val sessionService : SessionService

  // TODO: Name :(
  implicit def loggedIn2(implicit req : UserRequest[_]) : DeveloperSession = {
    req.developerSession
  }

  def atLeastPartLoggedInEnablingMfa2(body: UserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = {
    loadSessionAndValidate(_ => true)(UserRequest.apply)(body)
  }

  // TODO: Rename back to loggedInAction (and any other XXX2 methods / classes)
  def loggedInAction2(body: UserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext) : Action[AnyContent] = {
    loadSessionAndValidate(developerSession => {
      developerSession.loggedInState == LoggedInState.LOGGED_IN
    })(UserRequest.apply)(body)
  }

  def maybeLoggedInAction2(body: MaybeUserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext) : Action[AnyContent] = {
    loadSessionAndValidate(developerSession => {
      developerSession.loggedInState == LoggedInState.LOGGED_IN
    })((developerSession, request) => MaybeUserRequest.apply(Some(developerSession),request))(body)
  }

  private def fetchDeveloperSession[A](sessionId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[DeveloperSession]] = {
    sessionService.fetch(sessionId).map(maybeSession => maybeSession.map(DeveloperSession(_)))
  }

  def loadSession[A](implicit ec: ExecutionContext, request: Request[A]): Future[Option[DeveloperSession]] = {
    (for {
      cookie <- request.cookies.get(DevHubAuthWrapper.cookieName)
      sessionId <- DevHubAuthWrapper.decodeCookie(cookie.value)
    } yield fetchDeveloperSession(sessionId)).getOrElse(Future.successful(None))
  }

  private def loadSessionAndValidate[A](isValid : DeveloperSession => Boolean)
                                       (toRequest : (DeveloperSession, Request[AnyContent]) => A)
                                       (body: A => Future[Result])
                                       (implicit ec: ExecutionContext) : Action[AnyContent]
    = Action.async {

    implicit request: Request[AnyContent] =>
      val loginRedirect = Redirect(controllers.routes.UserLoginAccount.login())

      loadSession.flatMap(maybeSession => {
        maybeSession.fold(Future.successful(loginRedirect))(developerSession => {
          if (isValid(developerSession)){
            body(toRequest(developerSession, request))
          } else {
            // TODO: Should be forbidden
            Future.successful(loginRedirect)
          }
        })
      })
  }
}

case class UserRequest[A](developerSession: DeveloperSession, request: Request[A]) extends WrappedRequest[A](request)
case class MaybeUserRequest[A](developerSession: Option[DeveloperSession], request: Request[A]) extends WrappedRequest[A](request)

// TODO: Probably promote to injectable object
object DevHubAuthWrapper {
  val cookieName = "PLAY2AUTH_SESS_ID"
  protected val cookieSecureOption: Boolean = false // TODO: Load from config (MUST be true in prod.
  protected val cookieHttpOnlyOption: Boolean = true
  protected val cookieDomainOption: Option[String] = None
  protected val cookiePathOption: String = "/"
  protected val cookieMaxAge = Some(900) // 15 mins // TODO: Load from config

  def withSessionCookie(result : Result, sessionId: String) = {
    val c = Cookie(cookieName,  encodeCookie(sessionId), cookieMaxAge, cookiePathOption, cookieDomainOption, cookieSecureOption, cookieHttpOnlyOption)
    result.withCookies(c)
  }

  def encodeCookie(token : String) : String = Crypto.sign(token) + token

  def decodeCookie(token : String) : Option[String] = {
    val (hmac, value) = token.splitAt(40)

    val signedValue = Crypto.sign(value)

    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
      Some(value)
    } else {
      None
    }
  }

  def extractSessionIdFromCookie(request: RequestHeader): Option[String] = {
    request.cookies.get(DevHubAuthWrapper.cookieName) match {
      case Some(cookie) => decodeCookie(cookie.value)
      case _ => None
    }
  }

  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Logger.info(s"loginSucceeded - access_uri ${request.session.get("access_uri")}")
    val uri = request.session.get("access_uri").getOrElse(routes.AddApplication.manageApps().url)
    Future.successful(Redirect(uri).withNewSession)
  }
}