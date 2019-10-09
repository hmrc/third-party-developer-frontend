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

import config.{ApplicationConfig, ErrorHandler}
import domain.{BadRequestError, Capability, DeveloperSession, Permission, State}
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, ActionRefiner, Request, Result}
import service.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

trait ActionBuilders {

  val errorHandler: ErrorHandler
  val applicationService: ApplicationService
  implicit val appConfig: ApplicationConfig

  private implicit def hc(implicit request: Request[_]): HeaderCarrier =
    HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

  def applicationAction(applicationId: String, developerSession: DeveloperSession)(
    implicit ec: ExecutionContext): ActionRefiner[Request, ApplicationRequest] = new ActionRefiner[Request, ApplicationRequest] {

    override def refine[A](request: Request[A]): Future[Either[Result, ApplicationRequest[A]]] = {
      implicit val implicitRequest: Request[A] = request

      applicationService.fetchByApplicationId(applicationId)
        .map { application =>
          application.role(developerSession.developer.email)
            .map(role => ApplicationRequest(application, role, developerSession, request))
            .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, developerSession))))
        }
    }
  }

  private def forbiddenWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
      // TODO - should this be a forbiddenTemplate ?
      Some(Forbidden(errorHandler.badRequestTemplate))
    }
  }

  private def badRequestWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
      // TODO - should this be JSON ?
      Some(BadRequest(Json.toJson(BadRequestError)))
    }
  }

  def forbiddenWhenNotFilter(cond: ApplicationRequest[_] => Boolean): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      Future.successful(forbiddenWhenNot(cond(request)))
    }
  }

  def badRequestWhenNotFilter(cond: ApplicationRequest[_] => Boolean): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      Future.successful(badRequestWhenNot(cond(request)))
    }
  }

  def capabilityFilter(capability: Capability) =
    badRequestWhenNotFilter(req => capability.hasCapability(req.application))

  def permissionFilter(permission: Permission) =
    forbiddenWhenNotFilter(req => permission.hasPermissions(req.application, req.user.developer))
}
