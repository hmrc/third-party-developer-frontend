/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.ManageSubscriptions.toDetails
import domain.models.apidefinitions.{ApiContext, APISubscriptionStatusWithSubscriptionFields,APISubscriptionStatusWithWritableSubscriptionField, ApiVersion}
import domain.models.applications.{ApplicationId, Capability, Permission, State}
import domain.models.developers.DeveloperSession
import domain.models.controllers.NoSubscriptionFieldsRefinerBehaviour
import play.api.mvc._
import play.api.mvc.Results._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import service.ApplicationActionService
import domain.models.subscriptions.DevhubAccessLevel

trait SimpleApplicationActionBuilders {

  val errorHandler: ErrorHandler
  val applicationActionService: ApplicationActionService

  implicit val appConfig: ApplicationConfig

  private implicit def hc(implicit request: Request[_]): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  def applicationAction(applicationId: ApplicationId, developerSession: DeveloperSession)(implicit ec: ExecutionContext): ActionRefiner[MessagesRequest, ApplicationRequest] =
    new ActionRefiner[MessagesRequest, ApplicationRequest] {
      override protected def executionContext: ExecutionContext = ec

      override def refine[A](request: MessagesRequest[A]): Future[Either[Result, ApplicationRequest[A]]] = {
        implicit val implicitRequest: MessagesRequest[A] = request
        import cats.implicits._

        applicationActionService.process(applicationId, developerSession)
        .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, developerSession)))).value
      }
    }

  def applicationRequestRefiner(applicationId: ApplicationId)(implicit ec: ExecutionContext): ActionRefiner[UserRequest, ApplicationRequest] =
    new ActionRefiner[UserRequest, ApplicationRequest] {
      override protected def executionContext: ExecutionContext = ec

      override def refine[A](request: UserRequest[A]): Future[Either[Result, ApplicationRequest[A]]] = {
        implicit val implicitRequest: UserRequest[A] = request
        import cats.implicits._

        applicationActionService.process(applicationId, request.developerSession)
        .toRight(NotFound(errorHandler.notFoundTemplate(Request(request, request.developerSession)))).value
      }
    }
}

trait ActionBuilders extends SimpleApplicationActionBuilders {
  
  def fieldDefinitionsExistRefiner(noFieldsBehaviour: NoSubscriptionFieldsRefinerBehaviour)(
      implicit ec: ExecutionContext
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

  def subscriptionFieldPageRefiner(pageNumber: Int)(implicit ec: ExecutionContext): ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPage] =
    new ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFieldPage] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithFieldDefinitionsRequest[A]): Future[Either[Result, ApplicationWithSubscriptionFieldPage[A]]] = {
        val accessLevel = DevhubAccessLevel.fromRole(request.role)

        val details = request.fieldDefinitions.map(toDetails(accessLevel)).toList

        Future.successful(
          if (pageNumber >= 1 && pageNumber <= details.size) {
            val apiDetails = details(pageNumber - 1)
            val apiSubscriptionStatus = request.fieldDefinitions.toList(pageNumber - 1)

            Right(ApplicationWithSubscriptionFieldPage(pageNumber, details.size, apiSubscriptionStatus, apiDetails, request.applicationRequest))
          } else {
            Left(NotFound(errorHandler.notFoundTemplate(request)))
          }
        )
      }
    }

def subscriptionFieldsRefiner(context: ApiContext, version: ApiVersion)(
      implicit ec: ExecutionContext
  ): ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFields] =
    new ActionRefiner[ApplicationWithFieldDefinitionsRequest, ApplicationWithSubscriptionFields] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithFieldDefinitionsRequest[A]): Future[Either[Result, ApplicationWithSubscriptionFields[A]]] = {

        Future.successful({
          val apiSubscription = request.fieldDefinitions.filter(d => { d.context == context && d.apiVersion.version == version })

          apiSubscription match {
            case Nil               => Left(NotFound(errorHandler.notFoundTemplate(request)))
            case apiDetails :: Nil => Right(ApplicationWithSubscriptionFields(apiDetails, request.applicationRequest))
            case _                 => throw new RuntimeException(s"Too many APIs match for; context: ${context.value} version: ${version.value}")
          }
        })
      }
    }

  def writeableSubscriptionFieldRefiner(fieldName: String)(
      implicit ec: ExecutionContext
  ): ActionRefiner[ApplicationWithSubscriptionFields, ApplicationWithWritableSubscriptionField] =
    new ActionRefiner[ApplicationWithSubscriptionFields, ApplicationWithWritableSubscriptionField] {
      override protected def executionContext: ExecutionContext = ec

      def refine[A](request: ApplicationWithSubscriptionFields[A]): Future[Either[Result, ApplicationWithWritableSubscriptionField[A]]] = {

        Future.successful({
          val subscriptionFieldValues = request.apiSubscription.fields.fields
            .filter(d => d.definition.name.value == fieldName)

          subscriptionFieldValues match {
            case Nil => Left(NotFound(errorHandler.notFoundTemplate(request)))
            case subscriptionFieldValue :: Nil => {
              val accessLevel = DevhubAccessLevel.fromRole(request.applicationRequest.role)
              val canWrite = subscriptionFieldValue.definition.access.devhub.satisfiesWrite(accessLevel)

              if (canWrite){
                Right(ApplicationWithWritableSubscriptionField(APISubscriptionStatusWithWritableSubscriptionField(
                  request.apiSubscription.name,
                  request.apiSubscription.context,
                  request.apiSubscription.apiVersion,
                  subscriptionFieldValue,
                  request.apiSubscription.fields), request.applicationRequest))
              } else {
                Left(Forbidden(errorHandler.badRequestTemplate(request)))
              }
            }
            case _ => throw new RuntimeException(s"Too many APIs match for; fieldName: ${fieldName}")
          }
        })
      }
    }

  def subscribedToApiWithPpnsFieldFilter(
    implicit ec: ExecutionContext
  ): ActionFilter[ApplicationRequest] = new ActionFilter[ApplicationRequest] {
    override protected def executionContext: ExecutionContext = ec

    override protected def filter[A](request: ApplicationRequest[A]): Future[Option[Result]] = {
      implicit val implicitRequest: ApplicationRequest[A] = request

      if (hasPpnsFields(request)) {
        Future.successful(None)
      } else {
        Future.successful(Some(NotFound(errorHandler.notFoundTemplate)))
      }
    }
  }

  def hasPpnsFields(request: ApplicationRequest[_]): Boolean = {
    request.subscriptions.exists(s => s.subscribed && s.fields.fields.exists(field => field.definition.`type` == "PPNSField"))
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
