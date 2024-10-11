/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.State
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Capability, Permission}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationActionService

trait ApplicationActionBuilders {
  self: TpdfeBaseController =>

  protected def applicationActionService: ApplicationActionService

  def applicationRequestRefiner(applicationId: ApplicationId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, ApplicationRequest] = {
    new ActionRefiner[UserRequest, ApplicationRequest] {
      override protected def executionContext: ExecutionContext = ec

      override def refine[A](request: UserRequest[A]): Future[Either[Result, ApplicationRequest[A]]] = {
        implicit val implicitRequest: UserRequest[A] = request

        ETR.fromOptionM(applicationActionService.process(applicationId, request), errorHandler.notFoundTemplate(Request(request, request.userSession)).map(NotFound(_)))
          .value
      }
    }
  }

  private def forbiddenWhenNot[A](cond: Boolean)(implicit ec: ExecutionContext, applicationRequest: ApplicationRequest[A]): Future[Option[Result]] = {
    if (cond) {
      successful(None)
    } else {
      errorHandler.forbiddenTemplate.map(x => Some(Forbidden(x)))
    }
  }

  private def badRequestWhenNot[A](cond: Boolean)(implicit ec: ExecutionContext, applicationRequest: ApplicationRequest[A]): Future[Option[Result]] = {
    if (cond) {
      successful(None)
    } else {
      errorHandler.badRequestTemplate.map(x => Some(BadRequest(x)))
    }
  }

  def forbiddenWhenNotFilter(cond: ApplicationRequest[_] => Boolean)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      forbiddenWhenNot(cond(request))
    }
  }

  def badRequestWhenNotFilter(cond: ApplicationRequest[_] => Boolean)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      badRequestWhenNot(cond(request))
    }
  }

  def capabilityFilter(capability: Capability)(implicit ec: ExecutionContext): ActionFilter[ApplicationRequest] = {
    println("Checking capability")
    val capabilityCheck: ApplicationRequest[_] => Boolean = req => capability.hasCapability(req.application)
    badRequestWhenNotFilter(capabilityCheck)
  }

  def approvalFilter(approvalPredicate: State => Boolean)(implicit ec: ExecutionContext) = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest = request

      if (approvalPredicate(request.application.state.name))
        successful(None)
      else {
        errorHandler.notFoundTemplate.map(x => Some(NotFound(x)))
      }
    }
  }

  def permissionFilter(permission: Permission)(implicit ec: ExecutionContext) = {
    println("Checking permissions")

    val test: ApplicationRequest[_] => Boolean = (req) => permission.hasPermissions(req.application, req.userSession.developer)
    forbiddenWhenNotFilter(req => { val result = test(req); println("Permisison " + result); result })
  }
}
