/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, Capability, Permission, State}
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationActionService

trait BaseActionBuilders {
  val errorHandler: ErrorHandler
}

trait ApplicationActionBuilders extends BaseActionBuilders {
  val applicationActionService: ApplicationActionService

  implicit val appConfig: ApplicationConfig

  private implicit def hc(implicit request: Request[_]): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  def applicationRequestRefiner(applicationId: ApplicationId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, ApplicationRequest] =
    new ActionRefiner[UserRequest, ApplicationRequest] {
      override protected def executionContext: ExecutionContext = ec

      override def refine[A](request: UserRequest[A]): Future[Either[Result, ApplicationRequest[A]]] = {
        implicit val implicitRequest: UserRequest[A] = request
        import cats.implicits._

        applicationActionService.process(applicationId, request)
        .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, request.developerSession)))).value
      }
    }

  private def forbiddenWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
      Some(Forbidden(errorHandler.forbiddenTemplate))
    }
  }

  private def badRequestWhenNot[A](cond: Boolean)(implicit applicationRequest: ApplicationRequest[A]): Option[Result] = {
    if (cond) {
      None
    } else {
      Some(BadRequest(errorHandler.badRequestTemplate))
    }
  }

  def forbiddenWhenNotFilter(cond: ApplicationRequest[_] => Boolean)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      Future.successful(forbiddenWhenNot(cond(request)))
    }
  }

  def badRequestWhenNotFilter(cond: ApplicationRequest[_] => Boolean)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      Future.successful(badRequestWhenNot(cond(request)))
    }
  }

  def capabilityFilter(capability: Capability)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = {
    val capabilityCheck: ApplicationRequest[_] => Boolean = req => capability.hasCapability(req.application)
    badRequestWhenNotFilter(capabilityCheck)
  }

  def approvalFilter(approvalPredicate: State => Boolean)(implicit ec: ExecutionContext) = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]) = Future.successful {
      implicit val implicitRequest = request

      if (approvalPredicate(request.application.state.name)) None
      else {
        Some(NotFound(errorHandler.notFoundTemplate))
      }
    }
  }

  def permissionFilter(permission: Permission)(implicit ec: ExecutionContext) =
    forbiddenWhenNotFilter(req => permission.hasPermissions(req.application, req.developerSession.developer))
}

