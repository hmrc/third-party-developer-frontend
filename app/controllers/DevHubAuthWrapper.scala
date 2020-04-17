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

import domain.DeveloperSession
import jp.t2v.lab.play2.auth.AuthElement
import play.api.libs.Crypto
import play.api.mvc._
import service.SessionService

import scala.concurrent.{ExecutionContext, Future}

trait DevHubAuthWrapper extends Results with HeaderCarrierConversion {

  val sessionService : SessionService

  // TODO: Name :(
  implicit def loggedIn2(implicit req : UserRequest[_]) : DeveloperSession = {
    req.developerSession
  }

  def loggedInAction2(body: UserRequest[AnyContent] => Future[Result])(implicit ec: ExecutionContext) : Action[AnyContent] = Action.async {
    implicit request: Request[AnyContent] =>

      // TODO: Probably load from config?
      val cookieName = "PLAY2AUTH_SESS_ID"

      val loginRedirect = Redirect(controllers.routes.UserLoginAccount.login())

      (for {
        cookie <- request.cookies.get(cookieName)
        sessionId <- decodeCookie(cookie.value)
      } yield {
        sessionService
          .fetch(sessionId)
          .flatMap(maybeSession => {
            maybeSession.fold(Future.successful(loginRedirect))(session => {
              val developerSession = DeveloperSession(session)
              body(UserRequest(developerSession, request))
            })
          })
      }).getOrElse(Future.successful(loginRedirect))
  }

  private def decodeCookie(token : String) : Option[String] = {
    val (hmac, value) = token.splitAt(40)

    val signedValue = Crypto.sign(value)

    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
      Some(value)
    } else {
      None
    }
  }
}

case class UserRequest[A](developerSession: DeveloperSession, request: Request[A]) extends WrappedRequest[A](request)
