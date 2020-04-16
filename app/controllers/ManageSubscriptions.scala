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
import domain.{APISubscriptionStatus, Application}
import domain.ApiSubscriptionFields.{SaveSubscriptionFieldsFailureResponse, SaveSubscriptionFieldsResponse, SaveSubscriptionFieldsSuccessResponse, SubscriptionFieldValue}
import play.api.data
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc._
import service.{ApplicationService, AuditService, SessionService, SubscriptionFieldsService}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.include.subscriptionFields
import views.html.managesubscriptions.editApiMetadata

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

object ManageSubscriptions {

  case class FieldValue(name: String, value: String)

  case class ApiDetails(name: String, context: String, version: String, subsValues: NonEmptyList[FieldValue])

  def toFieldValue(sfv: SubscriptionFieldValue): FieldValue = {
    def default(in: String, default: String) = if (in.isEmpty) default else in

    FieldValue(sfv.definition.shortDescription, default(sfv.value, "None"))
  }

  def toDetails(in: APISubscriptionStatus): Option[ApiDetails] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
      nelFields = nelSFV.map(toFieldValue)
    } yield ApiDetails(
      name = in.name,
      context = in.context,
      version = in.apiVersion.version,
      subsValues = nelFields
    )
  }

  def toForm(in: APISubscriptionStatus): Option[EditApiMetadata] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
    } yield EditApiMetadata(in.name, fields = nelSFV.toList)
  }

  case class EditApiMetadata(apiName: String, fields: List[SubscriptionFieldValue])

  object EditApiMetadata {
    val form = Form(
      mapping(
        "apiName" -> text,
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
                                       fieldsForm: Form[EditApiMetadata]
                                     )

  def toViewModel(in: APISubscriptionStatus): Option[EditApiMetadataViewModel] = {
    toForm(in).map(data => EditApiMetadataViewModel(in.name, in.context, in.apiVersion.version, EditApiMetadata.form.fill(data)))
  }
}

@Singleton
class ManageSubscriptions @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val applicationService: ApplicationService,
    val errorHandler: ErrorHandler,
    val messagesApi: MessagesApi,
    val subFieldsService: SubscriptionFieldsService
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController
      with ApplicationHelper {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq = definitionsRequest.applicationRequest.request
      implicit val appRQ = definitionsRequest.applicationRequest

      val details = definitionsRequest.fieldDefinitions
        .map(toDetails)
        .foldLeft(Seq.empty[ApiDetails])((acc, item) => item.toSeq ++ acc)

      successful(Ok(views.html.managesubscriptions.listApiSubscriptions(definitionsRequest.applicationRequest.application, details)))
    }

  def editApiMetadataPage(applicationId: String, context: String, version: String): Action[AnyContent] =
    subFieldsDefinitionsExistAction(applicationId) { definitionsRequest: ApplicationWithFieldDefinitionsRequest[AnyContent] =>
      implicit val rq = definitionsRequest.applicationRequest.request
      implicit val appRQ = definitionsRequest.applicationRequest

      definitionsRequest.fieldDefinitions
        .filter(s => s.context.equalsIgnoreCase(context) && s.apiVersion.version.equalsIgnoreCase(version))
        .headOption
        .flatMap(toViewModel)
      .map(vm => successful(Ok(views.html.managesubscriptions.editApiMetadata(appRQ.application, vm))))
        .getOrElse(successful(NotFound(errorHandler.notFoundTemplate)))
    }

  def saveSubscriptionFields(applicationId: String, apiContext: String, apiVersion: String, subscriptionRedirect: String): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    def handleValidForm(validForm: EditApiMetadata) = {
      def saveFields(validForm: EditApiMetadata)(implicit hc: HeaderCarrier): Future[SaveSubscriptionFieldsResponse] = {
        if (validForm.fields.nonEmpty) {
          subFieldsService.saveFieldValues(applicationId, apiContext, apiVersion, Map(validForm.fields.map(f => f.definition.name -> f.value): _*))
        } else {
          Future.successful(SaveSubscriptionFieldsSuccessResponse)
        }
      }

      saveFields(validForm) map {
        case SaveSubscriptionFieldsSuccessResponse => Redirect(routes.ManageSubscriptions.listApiSubscriptions(applicationId))
        case SaveSubscriptionFieldsFailureResponse(fieldErrors) =>
          val errors = fieldErrors.map(fe => data.FormError(fe._1, fe._2)).toSeq
          val errorForm = EditApiMetadata.form.fill(validForm).copy(errors = errors)

          Ok(editApiMetadata(request.application, EditApiMetadataViewModel(validForm.apiName, apiContext, apiVersion, errorForm)))
      }
    }

    def handleInvalidForm(formWithErrors: Form[EditApiMetadata]) = {
      Future.successful(BadRequest(editApiMetadata(request.application, EditApiMetadataViewModel(applicationId, apiContext, apiVersion, formWithErrors))))
    }

    EditApiMetadata.form.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}
