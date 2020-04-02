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

object ManageSubscriptions {
  case class SubscriptionDetails(shortDescription: String, value: String)
  case class ApiDetails(name: String, version: String, subsValues: NonEmptyList[SubscriptionDetails])

  def toDetails(value: String): String = {
    if (value.isEmpty) "None" else value
  }

  def toDetails(in: SubscriptionFieldValue): SubscriptionDetails = {
    SubscriptionDetails(in.definition.shortDescription, toDetails(in.value))
  }

  def toDetails(in: SubscriptionFieldsWrapper): NonEmptyList[SubscriptionDetails] = {
    in.fields.map(toDetails)
  }

  def toDetails(in: APISubscriptionStatus): Option[ApiDetails] = {
    for {
      wrapper <- in.fields
      nelSFV <- NonEmptyList.fromList(wrapper.fields.toList)
      nelDetails = nelSFV.map(toDetails)

    } yield ApiDetails(name = in.name, version = in.apiVersion.version, subsValues = nelDetails)
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
}
