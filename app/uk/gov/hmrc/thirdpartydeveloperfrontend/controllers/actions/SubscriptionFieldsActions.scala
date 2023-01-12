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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationWithSubscriptionFieldsRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationWithSubscriptionFieldPageRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationWithFieldDefinitionsRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationWithWritableSubscriptionField
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.NoSubscriptionFieldsRefinerBehaviour
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import play.api.mvc.{Action, ActionRefiner, AnyContent, Result}
import scala.concurrent.Future
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import scala.concurrent.ExecutionContext
import cats.data.NonEmptyList
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.DevhubAccessLevel
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageSubscriptions.toDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationRequest

trait SubscriptionFieldsActions {
  self: ApplicationController =>

  private def subscriptionsBaseActions(applicationId: ApplicationId, noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour) =
    loggedInActionRefiner() andThen
      applicationRequestRefiner(applicationId) andThen
      capabilityFilter(Capabilities.EditSubscriptionFields) andThen
      fieldDefinitionsExistRefiner(noFieldsBehaviour)

  def subFieldsDefinitionsExistAction(
      applicationId: ApplicationId,
      noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour = NoSubscriptionFieldsRefinerBehaviour.BadRequest
    )(
      block: ApplicationWithFieldDefinitionsRequest[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, noFieldsBehaviour)
      )
        .invokeBlock(request, block)
    }
  }

  def subFieldsDefinitionsExistActionWithPageNumber(
      applicationId: ApplicationId,
      pageNumber: Int
    )(
      block: ApplicationWithSubscriptionFieldPageRequest[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
          subscriptionFieldPageRefiner(pageNumber)
      )
        .invokeBlock(request, block)
    }
  }

  def subFieldsDefinitionsExistActionByApi(
      applicationId: ApplicationId,
      context: ApiContext,
      version: ApiVersion
    )(
      block: ApplicationWithSubscriptionFieldsRequest[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
          subscriptionFieldsRefiner(context, version)
      )
        .invokeBlock(request, block)
    }
  }

  def singleSubFieldsWritableDefinitionActionByApi(
      applicationId: ApplicationId,
      context: ApiContext,
      version: ApiVersion,
      fieldName: String
    )(
      block: ApplicationWithWritableSubscriptionField[AnyContent] => Future[Result]
    ): Action[AnyContent] = {
    Action.async { implicit request =>
      (
        subscriptionsBaseActions(applicationId, NoSubscriptionFieldsRefinerBehaviour.BadRequest) andThen
          subscriptionFieldsRefiner(context, version) andThen
          writeableSubscriptionFieldRefiner(fieldName)
      )
        .invokeBlock(request, block)
    }
  }

  private def fieldDefinitionsExistRefiner(
      noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour
    )(implicit ec: ExecutionContext
    ): ActionRefiner[ApplicationRequest, ApplicationWithFieldDefinitionsRequest] = new ActionRefiner[ApplicationRequest, ApplicationWithFieldDefinitionsRequest] {
    override protected def executionContext: ExecutionContext = ec

    def refine[A](appRequest: ApplicationRequest[A]): Future[Either[Result, ApplicationWithFieldDefinitionsRequest[A]]] = {
      val noFieldsResult = noFieldsBehaviour match {
        case NoSubscriptionFieldsRefinerBehaviour.BadRequest    => play.api.mvc.Results.NotFound(errorHandler.notFoundTemplate(appRequest))
        case NoSubscriptionFieldsRefinerBehaviour.Redirect(url) => play.api.mvc.Results.Redirect(url)
      }

      val apiSubscriptionStatuses = appRequest.subscriptions.filter(s => s.subscribed)

      val apiSubStatusesWithFieldDefinitions = NonEmptyList
        .fromList(APISubscriptionStatusWithSubscriptionFields(apiSubscriptionStatuses).toList)

      Future.successful(
        apiSubStatusesWithFieldDefinitions
          .fold[Either[Result, ApplicationWithFieldDefinitionsRequest[A]]](Left(noFieldsResult))(withDefinitions =>
            Right(new ApplicationWithFieldDefinitionsRequest(withDefinitions, appRequest))
          )
      )
    }
  }

  private def subscriptionFieldPageRefiner(
      pageNumber: Int
    )(implicit ec: ExecutionContext
    ): ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPageRequest] =
    new ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPageRequest] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithFieldDefinitionsRequest[A]): Future[Either[Result, ApplicationWithSubscriptionFieldPageRequest[A]]] = {
        val accessLevel = DevhubAccessLevel.fromRole(request.role)

        val details = request.fieldDefinitions.map(toDetails(accessLevel)).toList

        Future.successful(
          if (pageNumber >= 1 && pageNumber <= details.size) {
            val apiDetails            = details(pageNumber - 1)
            val apiSubscriptionStatus = request.fieldDefinitions.toList(pageNumber - 1)

            Right(new ApplicationWithSubscriptionFieldPageRequest(pageNumber, details.size, apiSubscriptionStatus, apiDetails, request))
          } else {
            Left(NotFound(errorHandler.notFoundTemplate(request)))
          }
        )
      }
    }

  private def subscriptionFieldsRefiner(
      context: ApiContext,
      version: ApiVersion
    )(implicit ec: ExecutionContext
    ): ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldsRequest] =
    new ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldsRequest] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithFieldDefinitionsRequest[A]): Future[Either[Result, ApplicationWithSubscriptionFieldsRequest[A]]] = {

        Future.successful({
          val apiSubscription = request.fieldDefinitions.filter(d => { d.context == context && d.apiVersion.version == version })

          apiSubscription match {
            case Nil               => Left(NotFound(errorHandler.notFoundTemplate(request)))
            case apiDetails :: Nil => Right(new ApplicationWithSubscriptionFieldsRequest(apiDetails, request))
            case _                 => throw new RuntimeException(s"Too many APIs match for; context: ${context.value} version: ${version.value}")
          }
        })
      }
    }

  private def writeableSubscriptionFieldRefiner(
      fieldName: String
    )(implicit ec: ExecutionContext
    ): ActionRefiner[ApplicationWithSubscriptionFieldsRequest, ApplicationWithWritableSubscriptionField] =
    new ActionRefiner[ApplicationWithSubscriptionFieldsRequest, ApplicationWithWritableSubscriptionField] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithSubscriptionFieldsRequest[A]): Future[Either[Result, ApplicationWithWritableSubscriptionField[A]]] = {

        Future.successful({
          val subscriptionFieldValues = request.apiSubscription.fields.fields
            .filter(d => d.definition.name.value == fieldName)

          subscriptionFieldValues match {
            case Nil                           => Left(NotFound(errorHandler.notFoundTemplate(request)))
            case subscriptionFieldValue :: Nil => {
              val accessLevel = DevhubAccessLevel.fromRole(request.role)
              val canWrite    = subscriptionFieldValue.definition.access.devhub.satisfiesWrite(accessLevel)

              if (canWrite) {
                Right(new ApplicationWithWritableSubscriptionField(
                  APISubscriptionStatusWithWritableSubscriptionField(
                    request.apiSubscription.name,
                    request.apiSubscription.context,
                    request.apiSubscription.apiVersion,
                    subscriptionFieldValue,
                    request.apiSubscription.fields
                  ),
                  request
                ))
              } else {
                Left(Forbidden(errorHandler.badRequestTemplate(request)))
              }
            }
            case _                             => throw new RuntimeException(s"Too many APIs match for; fieldName: ${fieldName}")
          }
        })
      }
    }

}
