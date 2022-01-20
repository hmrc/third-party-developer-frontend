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

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import domain.models.apidefinitions.{APISubscriptionStatusWithSubscriptionFields, APISubscriptionStatusWithWritableSubscriptionField, ApiContext, ApiVersion}
import domain.models.applications.{Application, ApplicationId, CheckInformation}
import domain.models.controllers.SaveSubsFieldsPageMode
import domain.models.subscriptions.ApiSubscriptionFields._
import domain.models.subscriptions.{FieldName, FieldValue}
import domain.models.controllers.EditManageSubscription._
import domain.models.controllers.NoSubscriptionFieldsRefinerBehaviour
import play.api.data.FormError
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, AuditService, SessionService, ApplicationActionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import domain.models.subscriptions.DevhubAccessLevel
import controllers.actions.SubscriptionFieldsActions

object ManageSubscriptions {

  case class Field(name: String, shortDescription: String, value: String, canWrite: Boolean)

  case class ApiDetails(name: String, context: ApiContext, version: ApiVersion, displayedStatus: String, subsValues: Seq[Field])

  def toFieldValue(accessLevel: DevhubAccessLevel)(sfv: SubscriptionFieldValue): Field = {
    def default(in: String, default: String) = if (in.isEmpty) default else in

    val canWrite = sfv.definition.access.devhub.satisfiesWrite(accessLevel)

    Field(sfv.definition.name.value, sfv.definition.shortDescription, default(sfv.value.value, "None"), canWrite)
  }

  def toDetails(accessLevel: DevhubAccessLevel)(in: APISubscriptionStatusWithSubscriptionFields): ApiDetails = {
    ApiDetails(
      name = in.name,
      context = in.context,
      version = in.apiVersion.version,
      displayedStatus = in.apiVersion.displayedStatus,
      subsValues = in.fields.fields.map(toFieldValue(accessLevel))
    )
  }
}

@Singleton
class ManageSubscriptions @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    mcc: MessagesControllerComponents,
    val subFieldsService: SubscriptionFieldsService,
    val cookieSigner: CookieSigner,
    listApiSubscriptionsView: ListApiSubscriptionsView,
    editApiMetadataView: EditApiMetadataView,
    editApiMetadataFieldView: EditApiMetadataFieldView,
    subscriptionConfigurationStartView: SubscriptionConfigurationStartView,
    subscriptionConfigurationPageView: SubscriptionConfigurationPageView,
    subscriptionConfigurationStepPageView: SubscriptionConfigurationStepPageView
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc)
    with ApplicationHelper
    with SubscriptionFieldsActions {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: ApplicationId): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { implicit request: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      val accessLevel = DevhubAccessLevel.fromRole(request.role)

      val details = request.fieldDefinitions
        .map(toDetails(accessLevel))
        .toList

      successful(Ok(listApiSubscriptionsView(request.application, details)))
    }

  def editApiMetadataPage(applicationId: ApplicationId, context: ApiContext, version: ApiVersion, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistActionByApi(applicationId, context, version) { implicit request: ApplicationWithSubscriptionFieldsRequest[AnyContent] =>

      val role = request.role

      val apiSubscription: APISubscriptionStatusWithSubscriptionFields = request.apiSubscription

      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      successful(Ok(editApiMetadataView(request.application, viewModel, mode)))
    }

  def saveSubscriptionFields(applicationId: ApplicationId, apiContext: ApiContext, apiVersion: ApiVersion, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistActionByApi(applicationId, apiContext, apiVersion) { implicit request: ApplicationWithSubscriptionFieldsRequest[AnyContent] =>

      import SaveSubsFieldsPageMode._
      val successRedirectUrl = mode match {
        case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
        case CheckYourAnswers   => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
      }

      subscriptionConfigurationSave(
        apiContext,
        apiVersion,
        request.apiSubscription,
        successRedirectUrl,
        viewModel => {
          editApiMetadataView(request.application, viewModel, mode)
        }
      )
    }

  // TODO Use value class for FieldNameParam
  def editApiMetadataFieldPage(
      applicationId: ApplicationId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fieldNameParam: String,
      mode: SaveSubsFieldsPageMode) : Action[AnyContent] =    // TODO - make this FieldName type
    singleSubFieldsWritableDefinitionActionByApi(applicationId, apiContext, apiVersion, fieldNameParam) { implicit definitionRequest: ApplicationWithWritableSubscriptionField[AnyContent] =>

      val fieldName = FieldName(fieldNameParam)
      val fieldValue = definitionRequest.subscriptionWithSubscriptionField.subscriptionFieldValue.value
      val viewModel = EditApiConfigurationFieldViewModel.toViewModel(definitionRequest.subscriptionWithSubscriptionField, definitionRequest.role, Seq(), Map(fieldName -> fieldValue))

      successful(Ok(editApiMetadataFieldView(definitionRequest.application, viewModel, mode)))
  }

   def saveApiMetadataFieldPage(
      applicationId: ApplicationId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fieldNameParam: String,
      mode: SaveSubsFieldsPageMode) : Action[AnyContent] =

      singleSubFieldsWritableDefinitionActionByApi(applicationId, apiContext, apiVersion, fieldNameParam) { implicit definitionRequest: ApplicationWithWritableSubscriptionField[AnyContent] =>

      import SaveSubsFieldsPageMode._
      val successRedirectUrl = mode match {
        case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
        case CheckYourAnswers   => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
      }

      subscriptionConfigurationFieldSave(
        apiContext,
        apiVersion,
        definitionRequest.subscriptionWithSubscriptionField,
        successRedirectUrl,
        viewModel => {
          editApiMetadataFieldView(definitionRequest.application, viewModel, mode)
        }
      )
  }

  private def subscriptionConfigurationSave(
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      apiSubscription: APISubscriptionStatusWithSubscriptionFields,
      successRedirect: Call,
      validationFailureView: EditApiConfigurationViewModel => Html
  )(implicit hc: HeaderCarrier, applicationRequest: ApplicationRequest[AnyContent]): Future[Result] = {

    val postedValuesAsMap = applicationRequest.body.asFormUrlEncoded.get.map(v => (FieldName(v._1), FieldValue(v._2.head)))

    val subscriptionFieldValues = apiSubscription.fields.fields
    val role = applicationRequest.role
    val application = applicationRequest.application

    subFieldsService
      .saveFieldValues(role, application, apiContext, apiVersion, subscriptionFieldValues, postedValuesAsMap)
      .map({
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val formErrors = fieldErrors.map(error => FormError(error._1, Seq(error._2))).toSeq
          val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors, postedValuesAsMap)

          BadRequest(validationFailureView(viewModel))
        case SaveSubscriptionFieldsAccessDeniedResponse => Forbidden(errorHandler.badRequestTemplate)
      })
  }

    private def subscriptionConfigurationFieldSave(
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      apiSubscription: APISubscriptionStatusWithWritableSubscriptionField,
      successRedirect: Call,
      validationFailureView: EditApiConfigurationFieldViewModel => Html
  )(implicit hc: HeaderCarrier, applicationRequest: ApplicationRequest[AnyContent]): Future[Result] = {

    val postedValuesAsMap = applicationRequest.body.asFormUrlEncoded.get.map(v => (FieldName(v._1), FieldValue(v._2.head)))

    val role = applicationRequest.role
    val application = applicationRequest.application

    subFieldsService
      .saveFieldValues(role, application, apiContext, apiVersion, apiSubscription.oldValues.fields, postedValuesAsMap)
      .map({
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val formErrors = fieldErrors.map(error => FormError(error._1, Seq(error._2))).toSeq
          val viewModel = EditApiConfigurationFieldViewModel.toViewModel(apiSubscription, role, formErrors, postedValuesAsMap)

          BadRequest(validationFailureView(viewModel))
        case SaveSubscriptionFieldsAccessDeniedResponse => Forbidden(errorHandler.badRequestTemplate)
      })
  }

  def subscriptionConfigurationStart(applicationId: ApplicationId): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId, NoSubscriptionFieldsRefinerBehaviour.Redirect(addapplication.routes.AddApplication.addApplicationSuccess(applicationId))) {
      implicit request: ApplicationWithFieldDefinitionsRequest[AnyContent] => {

          val accessLevel = DevhubAccessLevel.fromRole(request.role)

          val details = request.fieldDefinitions
            .map(toDetails(accessLevel))
            .toList

          Future.successful(Ok(subscriptionConfigurationStartView(request.application, details)))
        }
    }

  def subscriptionConfigurationPage(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { implicit request: ApplicationWithSubscriptionFieldPageRequest[AnyContent] =>
      val apiSubscription = request.apiSubscriptionStatus
      val role = request.role

      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      Future.successful(Ok(subscriptionConfigurationPageView(request.application, pageNumber, viewModel)))
    }

  def subscriptionConfigurationPagePost(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { implicit request: ApplicationWithSubscriptionFieldPageRequest[AnyContent] =>

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId, pageNumber)

      subscriptionConfigurationSave(
        request.apiDetails.context,
        request.apiDetails.version,
        request.apiSubscriptionStatus,
        successRedirectUrl,
        viewModel => {
          subscriptionConfigurationPageView(request.application, pageNumber, viewModel)
        }
      )
    }

  def subscriptionConfigurationStepPage(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] = {
    def doEndOfJourneyRedirect(application: Application)(implicit hc: HeaderCarrier) = {
      if (application.deployedTo.isSandbox) {
        Future.successful(Redirect(addapplication.routes.AddApplication.addApplicationSuccess(application.id)))
      } else {
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionConfigurationsConfirmed = true)
        applicationService.updateCheckInformation(application, information) map { _ => Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(application.id)) }
      }
    }

    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { implicit request: ApplicationWithSubscriptionFieldPageRequest[AnyContent] =>

      if (pageNumber == request.totalPages) {
        doEndOfJourneyRedirect(request.application)

      } else {
        Future.successful(Ok(subscriptionConfigurationStepPageView(request.application, pageNumber, request.totalPages)))
      }
    }
  }
}
