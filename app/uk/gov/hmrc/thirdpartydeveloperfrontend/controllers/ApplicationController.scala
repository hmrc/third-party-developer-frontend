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

import scala.concurrent.Future

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.State
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.subscriptionfields.domain.models.FieldDefinitionType
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService

abstract class ApplicationController(mcc: MessagesControllerComponents)
    extends LoggedInController(mcc)
    with ApplicationActionBuilders {

  def applicationService: ApplicationService

  def hasPpnsFields(request: ApplicationRequest[_]): Boolean = {
    request.subscriptions.exists(in => in.subscribed && in.fields.fields.exists(field => field.definition.`type` == FieldDefinitionType.PPNS_FIELD))
  }

  def applicationViewModelFromApplicationRequest()(implicit request: ApplicationRequest[_]): ApplicationViewModel =
    ApplicationViewModel(request.application, request.hasSubscriptionFields, hasPpnsFields(request))

  def whenTeamMemberOnApp(applicationId: ApplicationId)(block: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
          applicationRequestRefiner(applicationId)
      ).invokeBlock(request, block)
    }

  private def checkActionWithStateCheck(
      stateCheck: State => Boolean
    )(
      capability: Capability,
      permissions: Permission
    )(
      applicationId: ApplicationId
    )(
      block: ApplicationRequest[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen                    // Log In Redirect
          applicationRequestRefiner(applicationId) andThen // NOT_FOUND
          capabilityFilter(capability) andThen             // BAD_REQUEST
          permissionFilter(permissions) andThen            // FORBIDDEN
          approvalFilter(stateCheck)                       // NOT_FOUND
      ).invokeBlock(request, block)
    }
  }

  def checkActionForAllStates = checkActionWithStateCheck(stateCheck = _ => true) _

  def checkActionForProduction = checkActionWithStateCheck(_.isProduction) _

  def checkActionForPreProduction = checkActionWithStateCheck(_.isPreProduction) _

  def checkActionForApprovedApps = checkActionWithStateCheck(_.isApproved) _

  def checkActionForApprovedOrTestingApps = checkActionWithStateCheck(state => state.isApproved || state.isTesting) _

  def checkActionForTesting = checkActionWithStateCheck(_.isTesting) _
}
