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
import domain.AccessType.{PRIVILEGED, ROPC}
import domain.{BadRequestError, DeveloperSession, Role, State}
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{ActionFilter, ActionRefiner, Request}
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

  def applicationAction(applicationId: String, user: DeveloperSession)(implicit ec: ExecutionContext) = new ActionRefiner[Request, ApplicationRequest] {
    override def refine[A](request: Request[A]) = {
      implicit val implicitRequest = request

      applicationService.fetchByApplicationId(applicationId)
        .map { application =>
          application.role(user.email)
            .map(role => ApplicationRequest(application, role, user, request))
            .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, user))))
        }
    }
  }

  def notRopcOrPrivilegedAppFilter = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request
      implicit val implicitUser = request.user

      val application = request.application
      application.access.accessType match {
        case PRIVILEGED | ROPC => Some(Ok(views.html.privilegedOrRopcApplication(application)))
        case _ => None
      }
    }
  }

  def appInStateProductionFilter = new ActionRefiner[ApplicationRequest, ApplicationRequest] {
    override protected def refine[A](request: ApplicationRequest[A]) = Future.successful {
      if (request.application.state.name == State.PRODUCTION) Right(request)
      else Left(BadRequest(Json.toJson(BadRequestError)))
    }
  }

  def appInStateTestingFilter = new ActionRefiner[ApplicationRequest, ApplicationRequest] {
    override protected def refine[A](request: ApplicationRequest[A]) = Future.successful {
      if (request.application.state.name == State.TESTING) Right(request)
      else Left(BadRequest(Json.toJson(BadRequestError)))
    }
  }

  def adminOnAppFilter = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request

      request.role match {
        case Role.ADMINISTRATOR => None
        case _ => Some(Forbidden(errorHandler.badRequestTemplate))
      }
    }
  }

  def adminIfProductionAppFilter = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request

      if (request.application.isPermittedToMakeChanges(request.role)) None
      else Some(Forbidden(errorHandler.badRequestTemplate))
    }
  }
}
