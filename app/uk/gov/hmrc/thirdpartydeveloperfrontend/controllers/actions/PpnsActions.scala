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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.actions

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{Action, ActionFilter, AnyContent, Result}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationController, ApplicationRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

trait PpnsActions {
  self: ApplicationController =>

  private def subscribedToApiWithPpnsFieldFilter(
      implicit ec: ExecutionContext
    ): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      if (hasPpnsFields(request)) {
        successful(None)
      } else {
        errorHandler.notFoundTemplate.map(x => Some(NotFound(x)))
      }
    }
  }

  def subscribedToApiWithPpnsFieldAction(
      applicationId: ApplicationId,
      capability: Capability,
      permissions: Permission
    )(
      block: ApplicationRequest[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
          applicationRequestRefiner(applicationId) andThen
          capabilityFilter(capability) andThen
          permissionFilter(permissions) andThen
          subscribedToApiWithPpnsFieldFilter
      )
        .invokeBlock(request, block)
    }
  }
}
