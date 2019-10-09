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
import domain.{BadRequestError, DeveloperSession, Environment, Role, State}
<<<<<<< HEAD
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
=======
>>>>>>> origin/master
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

  def standardAppFilter: ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = Future.successful {
      implicit val implicitRequest: ApplicationRequest[A] = request
      implicit val implicitUser: DeveloperSession = request.user

      val application = request.application
      application.access.accessType match {
        case PRIVILEGED | ROPC => Some(Forbidden(errorHandler.badRequestTemplate))
        case _ => None
      }
    }
  }

  def appInStateProductionFilter: ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = Future.successful {
      implicit val implicitRequest: ApplicationRequest[A] = request

      request.application.state.name match {
        case State.PRODUCTION => None
        case _ => Some(BadRequest(Json.toJson(BadRequestError)))
      }
    }
  }

  def appInStateTestingFilter: ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = Future.successful {
      implicit val implicitRequest: ApplicationRequest[A] = request

      request.application.state.name match {
        case State.TESTING => None
        case _ => Some(BadRequest(Json.toJson(BadRequestError)))
      }
    }
  }

  def adminOnAppFilter: ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = Future.successful {
      implicit val implicitRequest: ApplicationRequest[A] = request

      request.role match {
        case Role.ADMINISTRATOR => None
        case _ => Some(Forbidden(errorHandler.badRequestTemplate))
      }
    }
  }

  def sandboxOrAdminIfProductionAppFilter: ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = Future.successful {
      implicit val implicitRequest: ApplicationRequest[A] = request

      (request.application.deployedTo, request.role) match {
        case (Environment.SANDBOX, _) => None
        case (_, Role.ADMINISTRATOR) => None
        case _ => Some(Forbidden(errorHandler.badRequestTemplate))
      }
    }
  }

  def adminFilter = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request

      if (request.role == Role.ADMINISTRATOR) None
      else Some(Forbidden(errorHandler.badRequestTemplate))
    }
  }

  def notProductionAppFilter = new ActionFilter[ApplicationRequest] {
    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request

      if (request.application.deployedTo == Environment.SANDBOX) None
      else Some(Forbidden(errorHandler.badRequestTemplate))
    }
  }
}
