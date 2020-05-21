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

import cats.data.NonEmptyList
import com.google.inject.{Inject, Singleton}
import config.{ApplicationConfig, ErrorHandler}
import domain.{APISubscriptionStatusWithSubscriptionFields, CheckInformation, Environment}
import domain.ApiSubscriptionFields.{SaveSubscriptionFieldsFailureResponse, SaveSubscriptionFieldsResponse, SaveSubscriptionFieldsSuccessResponse, SubscriptionFieldValue}
import model.NoSubscriptionFieldsRefinerBehaviour
import play.api.data
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc._
import play.api.libs.crypto.CookieSigner
import play.twirl.api.Html
import service.{ApplicationService, AuditService, SessionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.managesubscriptions.editApiMetadata

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

object ManageSubscriptions {

  case class FieldValue(name: String, value: String)

  case class ApiDetails(name: String, context: String, version: String, displayedStatus: String, subsValues: NonEmptyList[FieldValue])

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

  def toForm(in: APISubscriptionStatusWithSubscriptionFields): EditApiMetadata = {
    EditApiMetadata(in.name,in.apiVersion.displayedStatus, fields = in.fields.fields.toList)
  }

  case class EditApiMetadata(apiName: String, displayedStatus: String, fields: List[SubscriptionFieldValue])

  object EditApiMetadata {
    val form: Form[EditApiMetadata] = Form(
      mapping(
        "apiName" -> text,
        "displayedStatus" -> text,
        "fields" -> list(
          mapping(
            "name" -> text,
            "description" -> text,
            "shortDescription" -> text,
            "hint" -> text,
            "type" -> text,
            "value" -> text
          )(SubscriptionFieldValue.fromFormValues)(SubscriptionFieldValue.toFormValues)
        )
      )(EditApiMetadata.apply)(EditApiMetadata.unapply)
    )
  }

  case class EditApiMetadataViewModel(
                                       name: String,
                                       apiContext: String,
                                       apiVersion: String,
                                       displayedStatus: String,
                                       fieldsForm: Form[EditApiMetadata]
                                     )

  def toViewModel(in: APISubscriptionStatusWithSubscriptionFields): EditApiMetadataViewModel = {
    val data = toForm(in)
    EditApiMetadataViewModel(in.name, in.context, in.apiVersion.version, in.apiVersion.displayedStatus, EditApiMetadata.form.fill(data))
  }
}

@Singleton
class ManageSubscriptions @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val applicationService: ApplicationService,
    val errorHandler: ErrorHandler,
    val messagesApi: MessagesApi,
    val subFieldsService: SubscriptionFieldsService,
    val cookieSigner : CookieSigner
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController
      with ApplicationHelper {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val details = definitionsRequest
        .fieldDefinitions
        .map(toDetails)
        .toList

      successful(Ok(views.html.managesubscriptions.listApiSubscriptions(definitionsRequest.applicationRequest.application, details)))
    }

  def editApiMetadataPage(applicationId: String, context: String, version: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      definitionsRequest.fieldDefinitions
        .filter(s => s.context.equalsIgnoreCase(context) && s.apiVersion.version.equalsIgnoreCase(version))
        .headOption
        .map(vm => successful(Ok(views.html.managesubscriptions.editApiMetadata(appRQ.application, toViewModel(vm)))))
        .getOrElse(successful(NotFound(errorHandler.notFoundTemplate)))
    }

  def saveSubscriptionFields(applicationId: String,
                             apiContext: String,
                             apiVersion: String) : Action[AnyContent]
    = whenTeamMemberOnApp(applicationId) { implicit request: ApplicationRequest[AnyContent] =>

    val successRedirectUrl = routes.ManageSubscriptions.listApiSubscriptions(applicationId)
    subscriptionConfigurationSave(apiContext, apiVersion, successRedirectUrl, vm =>
      editApiMetadata(request.application,vm)
    )
  }

  private def subscriptionConfigurationSave(apiContext: String,
                                            apiVersion: String,
                                            successRedirect: Call,
                                            validationFailureView : EditApiMetadataViewModel => Html)
                                           (implicit hc: HeaderCarrier, request: ApplicationRequest[_]): Future[Result] = {

    def handleValidForm(validForm: EditApiMetadata) = {
      def saveFields(validForm: EditApiMetadata)(implicit hc: HeaderCarrier): Future[SaveSubscriptionFieldsResponse] = {
        if (validForm.fields.nonEmpty) {
          subFieldsService.saveFieldValues(request.application.id, apiContext, apiVersion, Map(validForm.fields.map(f => f.definition.name -> f.value): _*))
        } else {
          Future.successful(SaveSubscriptionFieldsSuccessResponse)
        }
      }

      saveFields(validForm) map {
        case SaveSubscriptionFieldsSuccessResponse => Redirect(successRedirect.url)
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val errors = fieldErrors.map(fe => data.FormError(fe._1, fe._2)).toSeq
          val errorForm = EditApiMetadata.form.fill(validForm).copy(errors = errors)
          val vm = EditApiMetadataViewModel(validForm.apiName, apiContext, apiVersion, validForm.displayedStatus, errorForm)

          BadRequest(validationFailureView(vm))
      }
    }

    def handleInvalidForm(formWithErrors: Form[EditApiMetadata]) = {
      val displayedStatus = formWithErrors.data.getOrElse("displayedStatus", throw new Exception("Missing form field: displayedStatus"))

      val vm = EditApiMetadataViewModel(request.application.id, apiContext, apiVersion, displayedStatus, formWithErrors)
      Future.successful(BadRequest(validationFailureView(vm)))
    }

    EditApiMetadata.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def subscriptionConfigurationStart(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId,
      NoSubscriptionFieldsRefinerBehaviour.Redirect(routes.AddApplication.addApplicationSuccess(applicationId))) {

      definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] => {

        implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request
        implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

        val details = definitionsRequest
          .fieldDefinitions
          .map(toDetails)
          .toList

        Future.successful(Ok(views.html.createJourney.subscriptionConfigurationStart(definitionsRequest.applicationRequest.application, details)))
      }
    }

  def subscriptionConfigurationPage(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      Future.successful(Ok(views.html.createJourney.subscriptionConfigurationPage(
        definitionsRequest.applicationRequest.application,
        pageNumber,
        toViewModel(definitionsRequest.apiSubscriptionStatus))
      ))
    }

  def subscriptionConfigurationPagePost(applicationId: String, pageNumber: Int) : Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>

      implicit val applicationRequest: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val successRedirectUrl = routes.ManageSubscriptions.subscriptionConfigurationStepPage(applicationId,  pageNumber)

      subscriptionConfigurationSave(definitionsRequest.apiDetails.context, definitionsRequest.apiDetails.version, successRedirectUrl, viewModel => {
        views.html.createJourney.subscriptionConfigurationPage(definitionsRequest.applicationRequest.application, pageNumber, viewModel)
      })
    }

  def subscriptionConfigurationStepPage(applicationId: String, pageNumber: Int): Action[AnyContent] =
    subFieldsDefinitionsExistActionWithPageNumber(applicationId, pageNumber) { definitionsRequest: ApplicationWithSubscriptionFieldPage[AnyContent] =>
      implicit val rq: Request[AnyContent] = definitionsRequest.applicationRequest.request

      implicit val appRQ: ApplicationRequest[AnyContent] = definitionsRequest.applicationRequest

      val application = definitionsRequest.applicationRequest.application

      if (pageNumber == definitionsRequest.totalPages) {
        if (application.deployedTo == Environment.SANDBOX){
          Future.successful(Redirect(routes.AddApplication.addApplicationSuccess(application.id)))
        } else{
          // TODO: Test this branch
          val information = application.checkInformation.getOrElse(CheckInformation()).copy(apiSubscriptionConfigurationsConfirmed = true)
          applicationService.updateCheckInformation(application.id, information) map { _ =>
            Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(application.id))
          }
        }

      } else {
        Future.successful (Ok(views.html.createJourney.subscriptionConfigurationStepPage(application, pageNumber, definitionsRequest.totalPages)))
      }
    }
}
