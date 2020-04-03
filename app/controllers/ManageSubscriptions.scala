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

import scala.concurrent.ExecutionContext
import com.google.inject.{Singleton, Inject}
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.Future.{successful, failed}
import play.api.i18n.MessagesApi
import config.ErrorHandler
import service.SessionService
import config.ApplicationConfig
import service.ApplicationService
import service.AuditService
import domain.APISubscriptionStatus
import domain.ApiSubscriptionFields.SubscriptionFieldValue
import domain.ApiSubscriptionFields.SubscriptionFieldsWrapper
import cats.data.NonEmptyList
import play.api.data.Form
import play.api.data.Forms._
import domain.Application

object ManageSubscriptions {
  case class ApiDetails(name: String, context: String, version: String, subsValues: NonEmptyList[SubscriptionFieldValue])

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

  def toDetails(in: APISubscriptionStatus): Option[ApiDetails] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
    } yield ApiDetails(name = in.name, context = in.context, version = in.apiVersion.version, subsValues = nelSFV)
  }

  def toForm(in: APISubscriptionStatus): Option[EditApiMetadata] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
    } yield EditApiMetadata(fields = nelSFV.toList)
  }

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
    val messagesApi: MessagesApi
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController {

  import ManageSubscriptions._

  def listApiSubscriptions(applicationId: String): Action[AnyContent] =
    whenTeamMemberOnApp(applicationId) { implicit request =>
      val futureDetails =
        for {
          subs <- applicationService.apisWithSubscriptions(request.application)
          filteredSubs = subs.filter(s => s.subscribed)
          details = filteredSubs.map(toDetails).foldLeft(Seq.empty[ApiDetails])((acc, item) => item.toSeq ++ acc)
        } yield details

      futureDetails map { details => Ok(views.html.managesubscriptions.listApiSubscriptions(request.application, details)) }
    }

  def editApiMetadataPage(applicationId: String, context: String, version: String) =
    whenTeamMemberOnApp(applicationId) { implicit request =>
      val futureViewModel =
        for {
          subs <- applicationService.apisWithSubscriptions(request.application)
          filteredSubs = subs.filter(s => s.context == context && s.apiVersion.version == version)
          oViewModel = filteredSubs.headOption.flatMap(toViewModel)
        } yield oViewModel

      futureViewModel.map(_.fold[Result](BadRequest)(vm => Ok(views.html.managesubscriptions.editApiMetadata(request.application, vm))))
    }
}
