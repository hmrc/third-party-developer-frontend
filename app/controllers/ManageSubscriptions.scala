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

import com.google.inject.{Inject, Singleton}
import config.{ApplicationConfig, ErrorHandler}
import domain.models.apidefinitions.{APISubscriptionStatusWithSubscriptionFields, APISubscriptionStatusWithWritableSubscriptionField, ApiContext, ApiVersion}
import domain.models.applications.{Application, ApplicationId, CheckInformation}
import domain.models.controllers.SaveSubsFieldsPageMode
import domain.models.subscriptions.ApiSubscriptionFields._
import domain.models.subscriptions.{FieldName, FieldValue}
import model.EditManageSubscription._
import model.NoSubscriptionFieldsRefinerBehaviour
import play.api.data.FormError
import play.api.libs.crypto.CookieSigner
import play.api.mvc.Results.NotFound
import play.api.mvc._
import play.twirl.api.Html
import service.{ApplicationService, AuditService, SessionService, ApplicationActionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import domain.models.subscriptions.DevhubAccessLevel

object ManageSubscriptions {

  case class Field(name: String, value: String)

  case class ApiDetails(name: String, context: ApiContext, version: ApiVersion, displayedStatus: String, subsValues: Seq[Field])

  def toFieldValue(sfv: SubscriptionFieldValue): Field = {
    def default(in: String, default: String) = if (in.isEmpty) default else in

    Field(sfv.definition.shortDescription, default(sfv.value.value, "None"))
  }

  def toDetails(in: APISubscriptionStatusWithSubscriptionFields): ApiDetails = {
    ApiDetails(
      name = in.name,
      context = in.context,
      version = in.apiVersion.version,
      displayedStatus = in.apiVersion.displayedStatus,
      subsValues = in.fields.fields.map(toFieldValue)
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
    with ApplicationHelper {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: ApplicationId): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val details = definitionsRequest.fieldDefinitions
        .map(toDetails)
        .toList

      successful(Ok(listApiSubscriptionsView(definitionsRequest.applicationRequest.application, details)))
    }

  def editApiMetadataPage(applicationId: ApplicationId, context: ApiContext, version: ApiVersion, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistActionByApi(applicationId, context, version) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val role = definitionsRequest.applicationRequest.role

      val apiSubscription: APISubscriptionStatusWithSubscriptionFields = definitionsRequest.apiSubscription

      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      successful(Ok(editApiMetadataView(appRQ.application, viewModel, mode)))
    }

  def saveSubscriptionFields(applicationId: ApplicationId, apiContext: ApiContext, apiVersion: ApiVersion, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistActionByApi(applicationId, apiContext, apiVersion) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      import SaveSubsFieldsPageMode._
      val successRedirectUrl = mode match {
        case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
        case CheckYourAnswers   => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
      }

      subscriptionConfigurationSave(
        apiContext,
        apiVersion,
        definitionsRequest.apiSubscription,
        successRedirectUrl,
        viewModel => {
          editApiMetadataView(definitionsRequest.applicationRequest.application, viewModel, mode)
        }
      )
    }

  // TODO: Use value class for FieldNameParam
  def editApiMetadataFieldPage(
      applicationId: ApplicationId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fieldNameParam: String) : Action[AnyContent] =
    singleSubFieldsWritableDefinitionActionByApi(applicationId, apiContext, apiVersion, fieldNameParam) { definitionRequest: ApplicationWithWritableSubscriptionField[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionRequest.applicationRequest

      val fieldName = FieldName(fieldNameParam)

      val fieldValue = definitionRequest.subscriptionWithSubscriptionField.subscriptionFieldValue
      val definition = fieldValue.definition
      
      val subscriptionViewModel = SubscriptionFieldViewModel(
        definition.name,
        definition.description,
        definition.hint,
        canWrite = true,
        fieldValue.value,
        Seq.empty
      )

      val viewModel = EditApiConfigurationFieldViewModel(
        definitionRequest.subscriptionWithSubscriptionField.name,
        definitionRequest.subscriptionWithSubscriptionField.apiVersion.version,
        definitionRequest.subscriptionWithSubscriptionField.context,
        definitionRequest.subscriptionWithSubscriptionField.apiVersion.displayedStatus,
        subscriptionViewModel
      )

      successful(Ok(editApiMetadataFieldView(definitionRequest.applicationRequest.application, viewModel)))
  }

   def saveApiMetadataFieldPage(
      applicationId: ApplicationId,
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      fieldNameParam: String) : Action[AnyContent] =
<<<<<<< HEAD
      //Do We need a mode param? Above it needs it for the redirect
=======
>>>>>>> 00a4369336c184824bca39bb11a5da47db86506d
    singleSubFieldsWritableDefinitionActionByApi(applicationId, apiContext, apiVersion, fieldNameParam) { definitionRequest: ApplicationWithWritableSubscriptionField[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionRequest.applicationRequest

      val fieldName = FieldName(fieldNameParam)

      val fieldValue = definitionRequest.subscriptionWithSubscriptionField.subscriptionFieldValue
      val definition = fieldValue.definition

<<<<<<< HEAD
      import SaveSubsFieldsPageMode._
      val successRedirectUrl = mode match {
        case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
        case CheckYourAnswers   => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
      }

      subscriptionConfigurationSave2(
        apiContext,
        apiVersion,
        //Change this to use the new subscirptionWithSubscriptionField within the new class
        definitionRequest.subscriptionWithSubscriptionField,
        successRedirectUrl,
        viewModel => {
        //Change this to be definitionRequest  
          editApiMetadataView(definitionRequest.applicationRequest.application, viewModel, mode)
        }
      )

=======
>>>>>>> 00a4369336c184824bca39bb11a5da47db86506d
      //
      // TODO: Validate and do the save!
      // TODO: Test me

      successful(Redirect(controllers.routes.ManageSubscriptions.listApiSubscriptions(applicationId)))
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

    private def subscriptionConfigurationSave2(
      apiContext: ApiContext,
      apiVersion: ApiVersion,
      //Change this apiSubscription param to our class
      apiSubscription: APISubscriptionStatusWithWritableSubscriptionField,
      successRedirect: Call,
      validationFailureView: EditApiConfigurationFieldViewModel => Html
  )(implicit hc: HeaderCarrier, applicationRequest: ApplicationRequest[AnyContent]): Future[Result] = {

    //Only one field value??
    val postedValuesAsMap = applicationRequest.body.asFormUrlEncoded.get.map(v => (FieldName(v._1), FieldValue(v._2.head)))

    //Change this to use subscriptionFieldValue
    val subscriptionFieldValues = apiSubscription.subscriptionFieldValue
    val role = applicationRequest.role
    val application = applicationRequest.application

    subFieldsService
    //rename saveFieldValues2 method
      .saveFieldValues2(role, application, apiContext, apiVersion, subscriptionFieldValues, postedValuesAsMap)
      .map({
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val formErrors = fieldErrors.map(error => FormError(error._1, Seq(error._2))).toSeq
          //duplicate viewModel2 and rename
          val viewModel = EditApiConfigurationViewModel.toViewModel2(apiSubscription, role, formErrors, postedValuesAsMap)

          BadRequest(validationFailureView(viewModel))
        case SaveSubscriptionFieldsAccessDeniedResponse => Forbidden(errorHandler.badRequestTemplate)
      })
  }

  def subscriptionConfigurationStart(applicationId: ApplicationId): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId, NoSubscriptionFieldsRefinerBehaviour.Redirect(routes.AddApplication.addApplicationSuccess(applicationId))) {

      definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
        {

          implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

          val details = definitionsRequest.fieldDefinitions
            .map(toDetails)
            .toList

          Future.successful(Ok(subscriptionConfigurationStartView(definitionsRequest.applicationRequest.application, details)))
        }
    }

  def subscriptionConfigurationPage(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val apiSubscription = definitionsRequest.apiSubscriptionStatus
      val role = definitionsRequest.applicationRequest.role

      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      Future.successful(Ok(subscriptionConfigurationPageView(definitionsRequest.applicationRequest.application, pageNumber, viewModel)))
    }

  def subscriptionConfigurationPagePost(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val applicationRequest: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId, pageNumber)

      subscriptionConfigurationSave(
        definitionsRequest.apiDetails.context,
        definitionsRequest.apiDetails.version,
        definitionsRequest.apiSubscriptionStatus,
        successRedirectUrl,
        viewModel => {
          subscriptionConfigurationPageView(definitionsRequest.applicationRequest.application, pageNumber, viewModel)
        }
      )
    }

  def subscriptionConfigurationStepPage(applicationId: ApplicationId, pageNumber: Int): Action[AnyContent] = {
    def doEndOfJourneyRedirect(application: Application)(implicit hc: HeaderCarrier) = {
      if (application.deployedTo.isSandbox) {
        Future.successful(Redirect(routes.AddApplication.addApplicationSuccess(application.id)))
      } else {
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionConfigurationsConfirmed = true)
        applicationService.updateCheckInformation(application, information) map { _ => Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(application.id)) }
      }
    }

    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val application = definitionsRequest.applicationRequest.application

      if (pageNumber == definitionsRequest.totalPages) {
        doEndOfJourneyRedirect(application)

      } else {
        Future.successful(Ok(subscriptionConfigurationStepPageView(application, pageNumber, definitionsRequest.totalPages)))
      }
    }
  }
}
