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

package controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService
import domain.models.controllers.ApplicationViewModel
import play.api.mvc.{Action, AnyContent, Result, MessagesControllerComponents}
import domain.models.applications._
import scala.concurrent.Future


abstract class ApplicationController(mcc: MessagesControllerComponents) 
    extends LoggedInController(mcc)
    with ApplicationActionBuilders {
      
  val applicationService: ApplicationService


  def hasPpnsFields(request: ApplicationRequest[_]): Boolean = {
    request.subscriptions.exists(in => in.subscribed && in.fields.fields.exists(field => field.definition.`type` == "PPNSField"))
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
  )(capability: Capability, permissions: Permission)(applicationId: ApplicationId)(block: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        loggedInActionRefiner() andThen
        applicationRequestRefiner(applicationId) andThen
        capabilityFilter(capability) andThen
        permissionFilter(permissions) andThen
        approvalFilter(stateCheck)
      ).invokeBlock(request, block)
    }
  }

  def checkActionForAllStates = checkActionWithStateCheck(stateCheck = _ => true) _

  def checkActionForApprovedApps = checkActionWithStateCheck(_.isApproved) _

  def checkActionForApprovedOrTestingApps = checkActionWithStateCheck(state => state.isApproved || state.isInTesting) _

  def checkActionForTesting = checkActionWithStateCheck(_.isInTesting) _
}