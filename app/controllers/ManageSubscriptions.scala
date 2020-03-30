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

object ManageSubscriptions {
  case class SubscriptionDetails(shortDescription: String, value: String)
  case class ApiDetails(name: String, version: String, subsValues: Seq[SubscriptionDetails])

  def toDetails(in: SubscriptionFieldValue): SubscriptionDetails = {
    SubscriptionDetails(in.definition.shortDescription, in.value)
  }

  def toDetails(in: SubscriptionFieldsWrapper): Seq[SubscriptionDetails] = {
    in.fields.map(toDetails)
  }

  def toDetails(in: APISubscriptionStatus): ApiDetails = {
    ApiDetails(
      name = in.name,
      version = in.apiVersion.version,
      subsValues = in.fields.map(toDetails).getOrElse(Seq.empty)
    )
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
          filteredSubs = subs.filter(s => s.subscribed && s.fields.isDefined)
          details = filteredSubs.map(toDetails)
        } yield details

      futureDetails map { details =>
        Ok(views.html.managesubscriptions.listApiSubscriptions(request.application, details))
      }
    }
}
