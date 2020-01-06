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

package config

import controllers.routes
import domain._
import jp.t2v.lab.play2.auth._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import service.SessionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._

trait AuthConfigImpl extends AuthConfig {

  val errorHandler: ErrorHandler
  val sessionService: SessionService
  implicit val appConfig: ApplicationConfig

  type Id = String
  type User = DeveloperSession
  type Authority = UserStatus

  override def idTag: ClassTag[String] = classTag[Id]

  override def sessionTimeoutInSeconds: Int = appConfig.sessionTimeoutInSeconds

  val dummyHeader = HeaderCarrier()

  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] =
    sessionService
      .fetch(id)(dummyHeader)
      .map(ses => ses.map(DeveloperSession.apply))

  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Logger.info(s"loginSucceeded - access_uri ${request.session.get("access_uri")}")
    val uri = request.session.get("access_uri").getOrElse(routes.AddApplication.manageApps().url)
    Future.successful(Redirect(uri).withNewSession)
  }

  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val redirectTo = appConfig.apiDocumentationFrontendUrl + "/api-documentation"
    val sslMatchedUrl = redirectTo.replaceFirst("http:", if (request.secure) "https:" else "http:")
    Future.successful(Redirect(sslMatchedUrl))
  }

  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Logger.info(s"authenticationFailed - request_uri: ${request.uri} access_uri: ${request.session.get("access_uri")}")
    val result: Result =
      if (request.headers.isAjaxRequest) {
        Forbidden(Json.toJson(Map(
          "code" -> "FORBIDDEN",
          "message" -> "Your session may have timed-out or you are not authorised to make this request")))
      } else {
        Redirect(
          routes.UserLoginAccount.login())
          .withSession("access_uri" -> request.uri)
      }

    Future.successful(result)
  }

  // Access page while not logged in (e.g. part logged in) redirect to logon page.
  override def authorizationFailed(request: RequestHeader, user: User,
                                   authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(
      routes.UserLoginAccount.login())
    )
  }

  def authorize(developerSession: User, requiredAuthority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = {
    def isAuthorized =
      (developerSession.session.loggedInState, requiredAuthority) match {
        case (LoggedInState.LOGGED_IN, LoggedInUser) => true
        case (LoggedInState.PART_LOGGED_IN_ENABLING_MFA, LoggedInUser) => false
        case (LoggedInState.LOGGED_IN, AtLeastPartLoggedInEnablingMfa) => true
        case (LoggedInState.PART_LOGGED_IN_ENABLING_MFA, AtLeastPartLoggedInEnablingMfa) => true
        case _ => false
      }

    Future.successful(isAuthorized)
  }

  override lazy val idContainer = AsyncIdContainer(new TransparentIdContainer[Id])

  override lazy val tokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = appConfig.securedCookie,
    cookieMaxAge = Some(sessionTimeoutInSeconds)
  )

  implicit class RequestWithAjaxSupport(h: Headers) {
    def isAjaxRequest: Boolean = h.get("X-Requested-With").contains("XMLHttpRequest")
  }
}

