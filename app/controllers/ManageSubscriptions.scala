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
import domain.APISubscriptionStatus
import domain.ApiSubscriptionFields.SubscriptionFieldValue
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import play.api.mvc.Results.NotFound
import play.api.mvc._
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

object ManageSubscriptions {
  case class Field(name: String, value: String)
  case class ApiDetails(name: String, context: String, version: String, subsValues: NonEmptyList[Field])

  def toField(sfv: SubscriptionFieldValue): Field = {
    def default(in:String, default: String) = if(in.isEmpty) default else in

    Field(sfv.definition.shortDescription, default(sfv.value, "None"))
  }

  def toDetails(in: APISubscriptionStatus): Option[ApiDetails] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
      nelFields = nelSFV.map(toField)
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
    } yield EditApiMetadata(fields = nelSFV.toList)
  }

  case class EditApiMetadata(fields: List[SubscriptionFieldValue])

  object EditApiMetadata {
    val form = Form(
      mapping(
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

  class SubscriptionFieldDefinitionsAction(errorHandler: ErrorHandler, applicationService: ApplicationService)(implicit hc: HeaderCarrier, ec: ExecutionContext)
    extends ActionRefiner[ApplicationRequest, ApplicationWithFieldDefinitionsRequest] {

    def refine[A](input: ApplicationRequest[A]) = {
      implicit val rq = input.request

      for {
          subs <- applicationService.apisWithSubscriptions(input.application)
          filteredSubs = subs
            .filter(s => s.subscribed && s.fields.isDefined)
            .toList
        maybeNel = NonEmptyList.fromList(filteredSubs)
      } yield {
        maybeNel.map(nel => ApplicationWithFieldDefinitionsRequest(nel, input))
          .toRight(NotFound(errorHandler.notFoundTemplate(input)))
      }
    }
  }
}

case class ApplicationWithFieldDefinitionsRequest[A](fieldDefinitions: NonEmptyList[APISubscriptionStatus], applicationRequest: ApplicationRequest[A])
  extends WrappedRequest[A](applicationRequest)

@Singleton
class ManageSubscriptions @Inject() (
    val sessionService: SessionService,
    val auditService: AuditService,
    val applicationService: ApplicationService,
    val errorHandler: ErrorHandler,
    val messagesApi: MessagesApi
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController {

  import ManageSubscriptions._

  private def appfunc(implicit hc: HeaderCarrier): ActionFunction[ApplicationRequest, ApplicationWithFieldDefinitionsRequest]
    = new SubscriptionFieldDefinitionsAction(errorHandler, applicationService)

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit applicationRequest =>
      appfunc.invokeBlock(applicationRequest, { definitionsRequest: ApplicationWithFieldDefinitionsRequest[_] =>
        val details = definitionsRequest.fieldDefinitions
          .map(toDetails)
          .foldLeft(Seq.empty[ApiDetails])((acc, item) => item.toSeq ++ acc)

        successful(Ok(views.html.managesubscriptions.listApiSubscriptions(applicationRequest.application, details)))
        }
      )
    }

  def editApiMetadataPage(applicationId: String, context: String, version: String): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit applicationRequest =>
      appfunc.invokeBlock(applicationRequest, { definitionsRequest: ApplicationWithFieldDefinitionsRequest[_] =>
        definitionsRequest.fieldDefinitions
          .filter(s => s.context.equalsIgnoreCase(context) && s.apiVersion.version.equalsIgnoreCase(version))
          .headOption
          .flatMap(toViewModel)
          .map(vm => successful(Ok(views.html.managesubscriptions.editApiMetadata(applicationRequest.application, vm))))
          .getOrElse(successful(NotFound(errorHandler.notFoundTemplate(Request(applicationRequest, applicationRequest.user)))))
        }
      )
    }
}
