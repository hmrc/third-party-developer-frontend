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

import java.util.UUID

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.Credentials.serverTokenCutoffDate
import domain.Capabilities.{ChangeClientSecret, ViewCredentials}
import domain.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import domain._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service._
import uk.gov.hmrc.http.ForbiddenException

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Credentials @Inject()(val applicationService: ApplicationService,
                            val developerConnector: ThirdPartyDeveloperConnector,
                            val auditService: AuditService,
                            val sessionService: SessionService,
                            val errorHandler: ErrorHandler,
                            val messagesApi: MessagesApi
                            )
                           (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController {

  private def canViewClientCredentialsPage(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(ViewCredentials, TeamMembersOnly)(applicationId)(fun)

  private def canChangeClientSecrets(applicationId: String)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(ChangeClientSecret, SandboxOrAdmin)(applicationId)(fun)

  def credentials(applicationId: String): Action[AnyContent] =
    canViewClientCredentialsPage(applicationId) { implicit request =>
      successful(Ok(views.html.credentials(request.application)))
  }

  def clientId(applicationId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      successful(Ok(views.html.clientId(request.application)))
  }

  def clientSecrets(applicationId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService.fetchCredentials(applicationId).map { tokens =>
        Ok(views.html.clientSecrets(request.application, tokens.clientSecrets))
      }
  }

  def serverToken(applicationId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      if (request.application.createdOn.isBefore(serverTokenCutoffDate)) {
        applicationService.fetchCredentials(applicationId).map { tokens =>
          Ok(views.html.serverToken(request.application, tokens.accessToken))
        }
      } else {
        successful(NotFound(errorHandler.notFoundTemplate))
      }
    }

  def addClientSecret(applicationId: String): Action[AnyContent] = canChangeClientSecrets(applicationId) { implicit request =>
    applicationService.addClientSecret(applicationId, request.user.email).map { response =>
      Redirect(controllers.routes.Credentials.clientSecrets(applicationId))
        .flashing("newSecretId" -> response._1, "newSecret" -> response._2)
    } recover {
        case _: ApplicationNotFound => NotFound(errorHandler.notFoundTemplate)
        case _: ForbiddenException => Forbidden(errorHandler.badRequestTemplate)
        case _: ClientSecretLimitExceeded => UnprocessableEntity(errorHandler.badRequestTemplate)
    }
  }

  def deleteClientSecret(applicationId: UUID, clientSecretId: String): Action[AnyContent] = canChangeClientSecrets(applicationId.toString) { implicit request =>
    applicationService.fetchCredentials(applicationId.toString).map { tokens =>
      tokens.clientSecrets.find(_.id == clientSecretId)
        .fold(NotFound(errorHandler.notFoundTemplate))(secret => Ok(views.html.editapplication.deleteClientSecret(request.application, secret)))
    }
  }

  def deleteClientSecretAction(applicationId: UUID, clientSecretId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId.toString) { implicit request =>
      applicationService.deleteClientSecret(applicationId, clientSecretId, request.user.email)
        .map(_ => Redirect(controllers.routes.Credentials.clientSecrets(applicationId.toString)))
  }
}

object Credentials {
  val serverTokenCutoffDate = new DateTime(2020, 4, 1, 0, 0) // scalastyle:ignore magic.number
}
