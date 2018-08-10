/*
 * Copyright 2018 HM Revenue & Customs
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

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import uk.gov.hmrc.http.HeaderCarrier

trait AuthConfigImpl extends AuthConfig {

  type Id = String
  type User = Developer
  type Authority = UserStatus
  override def idTag = classTag[Id]
  override def sessionTimeoutInSeconds = appConfig.sessionTimeoutInSeconds
  val dummyHeader = HeaderCarrier()

  val appConfig: ApplicationConfig
  val sessionService: SessionService

  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] =
    sessionService.fetch(id)(dummyHeader).map(_.map(_.developer))

  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Logger.info(s"loginSucceeded - access_uri ${request.session.get("access_uri")}")
    val uri = request.session.get("access_uri").getOrElse(routes.ManageApplications.manageApps.url)
    Future.successful(Redirect(uri).withNewSession)
  }

  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    val redirectTo = appConfig.apiDocumentationFrontendUrl + "/api-documentation"
    val sslMatchedUrl = redirectTo.replaceFirst("http:", if (request.secure) "https:" else "http:")
    Future.successful(Redirect(sslMatchedUrl))
  }

  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Logger.info(s"authenticationFailed - request_uri: ${request.uri} access_uri: ${request.session.get("access_uri")}")
    if (request.headers.isAjaxRequest) Future.successful(Forbidden(Json.toJson(Map("code" -> "FORBIDDEN", "message" -> "Your session may have timed-out or you are not authorised to make this request"))))
    else Future.successful(Redirect(routes.UserLoginAccount.login).withSession("access_uri" -> request.uri))
  }

  override def authorizationFailed(request: RequestHeader, user: Developer,
                                   authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(NotFound(ApplicationGlobal.notFoundTemplate(Request(request, user))))
  }

  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = {
    def getRole(app: Future[Application], user: Developer) = app.map(_.collaborators.find(_.emailAddress == user.email))

    authority match {
      case AppAdmin(app) => getRole(app, user).map(_.exists(_.role == Role.ADMINISTRATOR))
      case AppTeamMember(app) => getRole(app, user).map(_.isDefined)
      case _ => Future.successful(true)
    }
  }

  override lazy val idContainer = AsyncIdContainer(new TransparentIdContainer[Id])

  override lazy val tokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = appConfig.securedCookie,
    cookieMaxAge = Some(sessionTimeoutInSeconds)
  )

  implicit class RequestWithAjaxSupport(h: Headers) {
    def isAjaxRequest = h.get("X-Requested-With").contains("XMLHttpRequest")
  }
}

