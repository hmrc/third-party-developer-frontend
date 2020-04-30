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

import cats.data.NonEmptyList
import config.{ApplicationConfig, ErrorHandler}
import controllers.ManageSubscriptions.{ApiDetails, toDetails}
import domain._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
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

      for {
        application <- applicationService.fetchByApplicationId(applicationId)
        subs <- applicationService.apisWithSubscriptions(application)
      } yield {
        application
          .role(developerSession.developer.email)
          .map(role => ApplicationRequest(application, subs, role, developerSession, request))
          .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, developerSession))))
      }
    }
  }

  def fieldDefinitionsExistRefiner(implicit ec: ExecutionContext): ActionRefiner[ApplicationRequest, ApplicationWithFieldDefinitionsRequest]
      = new ActionRefiner[ApplicationRequest, ApplicationWithFieldDefinitionsRequest] {

    def refine[A](input: ApplicationRequest[A]): Future[Either[Result, ApplicationWithFieldDefinitionsRequest[A]]] = {
      implicit val implicitRequest: Request[A] = input.request

      Future.successful(
        NonEmptyList.fromList(
          input.subscriptions.filter(
            s => s.subscribed && s.fields.isDefined
          ).toList
        )
        .map(nel => ApplicationWithFieldDefinitionsRequest(nel, input))
        .toRight(play.api.mvc.Results.NotFound(errorHandler.notFoundTemplate))
      )
    }
  }

  def subscriptionFieldPageRefiner(pageNumber: Int)(implicit ec: ExecutionContext):
    ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPage]
      = new ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPage] {

    def refine[A](input: ApplicationWithFieldDefinitionsRequest[A]): Future[Either[Result, ApplicationWithSubscriptionFieldPage[A]]] = {
      implicit val implicitRequest: Request[A] = input.applicationRequest.request

        val details = input.fieldDefinitions
          .map(toDetails)
          .foldLeft(Seq.empty[ApiDetails])((acc, item) => item.toSeq ++ acc)

        // TODO: Sort?
        val ofPage = details.size

      if (pageNumber > 0 && pageNumber <= ofPage) {
        Future.successful(Right(ApplicationWithSubscriptionFieldPage(pageNumber, input.fieldDefinitions, input.applicationRequest)))
      } else {
          Future.successful(Left(NotFound(errorHandler.notFoundTemplate)))
      }
    }
  }

  private def forbiddenWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
      Some(Forbidden(errorHandler.badRequestTemplate))
    }
  }

  private def badRequestWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
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

  def capabilityFilter(capability: Capability) = {
    val capabilityCheck: ApplicationRequest[_] => Boolean = req => capability.hasCapability(req.application)
    capability match {
      case c : LikePermission => forbiddenWhenNotFilter(capabilityCheck)
      case c : Capability => badRequestWhenNotFilter(capabilityCheck)
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

  def permissionFilter(permission: Permission) =
    forbiddenWhenNotFilter(req => permission.hasPermissions(req.application, req.user.developer))

}
