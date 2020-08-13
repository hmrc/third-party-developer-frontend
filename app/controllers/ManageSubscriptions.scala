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
import domain.models.apidefinitions.{ApiContext,APISubscriptionStatusWithSubscriptionFields}
import domain.models.subscriptions.ApiSubscriptionFields._
import domain.models.applications.{Application, CheckInformation}
import domain.models.controllers.SaveSubsFieldsPageMode
import model.EditManageSubscription._
import model.NoSubscriptionFieldsRefinerBehaviour
import play.api.data.FormError
import play.api.libs.crypto.CookieSigner
import play.api.mvc._
import play.twirl.api.Html
import service.{ApplicationService, AuditService, SessionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.createJourney.{SubscriptionConfigurationPageView, SubscriptionConfigurationStartView, SubscriptionConfigurationStepPageView}
import views.html.managesubscriptions.{EditApiMetadataView, ListApiSubscriptionsView}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful
import domain.models.apidefinitions.ApiContext

object ManageSubscriptions {

  case class FieldValue(name: String, value: String)

  case class ApiDetails(name: String, context: ApiContext, version: String, displayedStatus: String, subsValues: Seq[FieldValue])

  def toFieldValue(sfv: SubscriptionFieldValue): FieldValue = {
    def default(in: String, default: String) = if (in.isEmpty) default else in

    FieldValue(sfv.definition.shortDescription, default(sfv.value, "None"))
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
class ManageSubscriptions @Inject() (val sessionService: SessionService,
                                     val auditService: AuditService,
                                     val applicationService: ApplicationService,
                                     val errorHandler: ErrorHandler,
                                     mcc: MessagesControllerComponents,
                                     val subFieldsService: SubscriptionFieldsService,
                                     val cookieSigner : CookieSigner,
                                     listApiSubscriptionsView: ListApiSubscriptionsView,
                                     editApiMetadataView: EditApiMetadataView,
                                     subscriptionConfigurationStartView: SubscriptionConfigurationStartView,
                                     subscriptionConfigurationPageView: SubscriptionConfigurationPageView,
                                     subscriptionConfigurationStepPageView: SubscriptionConfigurationStepPageView)
                                    (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) with ApplicationHelper {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val details = definitionsRequest
        .fieldDefinitions
        .map(toDetails)
        .toList

      successful(Ok(listApiSubscriptionsView(definitionsRequest.applicationRequest.application, details)))
    }

  def editApiMetadataPage(applicationId: String, context: ApiContext, version: String, mode: SaveSubsFieldsPageMode): Action[AnyContent] =
    subFieldsDefinitionsExistActionByApi(applicationId, context, version) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val role = definitionsRequest.applicationRequest.role

      val apiSubscription : APISubscriptionStatusWithSubscriptionFields =  definitionsRequest.apiSubscription
  
      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      successful(Ok(editApiMetadataView(appRQ.application, viewModel, mode)))
    }

  def saveSubscriptionFields(applicationId: String,
                             apiContext: ApiContext,
                             apiVersion: String,
                             mode: SaveSubsFieldsPageMode) : Action[AnyContent] =
      subFieldsDefinitionsExistActionByApi(applicationId, apiContext, apiVersion) { definitionsRequest: ApplicationWithSubscriptionFields[AnyContent] =>
 
    implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

    import SaveSubsFieldsPageMode._
    val successRedirectUrl = mode match {
      case LeftHandNavigation => routes.ManageSubscriptions.listApiSubscriptions(applicationId)
      case CheckYourAnswers => checkpages.routes.CheckYourAnswers.answersPage(applicationId).withFragment("configurations")
    }
    
    subscriptionConfigurationSave(apiContext, apiVersion,definitionsRequest.apiSubscription, successRedirectUrl, viewModel => {
        editApiMetadataView(definitionsRequest.applicationRequest.application, viewModel, mode)
      }
    )
  }

  private def subscriptionConfigurationSave(apiContext: ApiContext,
                                            apiVersion: String,
                                            apiSubscription: APISubscriptionStatusWithSubscriptionFields,
                                            successRedirect: Call,
                                            validationFailureView : EditApiConfigurationViewModel => Html)
                                           (implicit hc: HeaderCarrier, applicationRequest: ApplicationRequest[AnyContent]): Future[Result] = {

  val postedValuesAsMap = applicationRequest.body.asFormUrlEncoded.get.map(v => (v._1, v._2.head))

  val subscriptionFieldValues = apiSubscription.fields.fields
  val role = applicationRequest.role
  val application = applicationRequest.application

  subFieldsService
    .saveFieldValues(role, application, apiContext, apiVersion, subscriptionFieldValues, postedValuesAsMap)
    .map({
      case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect)
      case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
        val formErrors = fieldErrors.map(error => FormError(error._1,Seq(error._2))).toSeq
        val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors, postedValuesAsMap)

        BadRequest(validationFailureView(viewModel) )
      case SaveSubscriptionFieldsAccessDeniedResponse => Forbidden(errorHandler.badRequestTemplate)
    })
  }

  def subscriptionConfigurationStart(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId,
      NoSubscriptionFieldsRefinerBehaviour.Redirect(routes.AddApplication.addApplicationSuccess(applicationId))) {

      definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] => {

        implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

        val details = definitionsRequest
          .fieldDefinitions
          .map(toDetails)
          .toList

        Future.successful(Ok(subscriptionConfigurationStartView(definitionsRequest.applicationRequest.application, details)))
      }
    }

  def subscriptionConfigurationPage(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val apiSubscription = definitionsRequest.apiSubscriptionStatus
      val role = definitionsRequest.applicationRequest.role

      val viewModel = EditApiConfigurationViewModel.toViewModel(apiSubscription, role, formErrors = Seq.empty, postedFormValues = Map.empty)

      Future.successful(Ok(subscriptionConfigurationPageView(
        definitionsRequest.applicationRequest.application,
        pageNumber,
        viewModel)
      ))
    }

  def subscriptionConfigurationPagePost(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val applicationRequest: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId,  pageNumber)

      subscriptionConfigurationSave(
        definitionsRequest.apiDetails.context,
        definitionsRequest.apiDetails.version,
        definitionsRequest.apiSubscriptionStatus,
        successRedirectUrl,
        viewModel => {
          subscriptionConfigurationPageView(definitionsRequest.applicationRequest.application, pageNumber, viewModel)
        })
    }

  def subscriptionConfigurationStepPage(applicationId: String, pageNumber: Int): Action[AnyContent] = {
    def doEndOfJourneyRedirect(application: Application)(implicit hc: HeaderCarrier) = {
      if (application.deployedTo.isSandbox){
        Future.successful(Redirect(routes.AddApplication.addApplicationSuccess(application.id)))
      } else {
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionConfigurationsConfirmed = true)
        applicationService.updateCheckInformation(application, information) map { _ =>
          Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(application.id))
        }
      }
    }

    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val application = definitionsRequest.applicationRequest.application

      if (pageNumber == definitionsRequest.totalPages) {
        doEndOfJourneyRedirect(application)

      } else {
        Future.successful (Ok(subscriptionConfigurationStepPageView(application, pageNumber, definitionsRequest.totalPages)))
      }
    }
  }
}
