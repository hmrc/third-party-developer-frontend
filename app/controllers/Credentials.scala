/*
 * Copyright 2021 HM Revenue & Customs
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

import config.{ApplicationConfig, ErrorHandler, FraudPreventionConfigProvider}
import connectors.ThirdPartyDeveloperConnector
import controllers.Credentials.serverTokenCutoffDate
import domain._
import domain.models.applications.ApplicationId
import domain.models.applications.Capabilities.{ChangeClientSecret, ViewCredentials}
import domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service._
import uk.gov.hmrc.http.ForbiddenException
import views.html.{ClientIdView, ClientSecretsView, CredentialsView, ServerTokenView}
import views.html.editapplication.DeleteClientSecretView

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.successful

@Singleton
class Credentials @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val developerConnector: ThirdPartyDeveloperConnector,
    val auditService: AuditService,
    val sessionService: SessionService,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    credentialsView: CredentialsView,
    clientIdView: ClientIdView,
    clientSecretsView: ClientSecretsView,
    serverTokenView: ServerTokenView,
    deleteClientSecretView: DeleteClientSecretView,
    val fraudPreventionConfigProvider: FraudPreventionConfigProvider
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
    extends ApplicationController(mcc) {

  private def canViewClientCredentialsPage(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(ViewCredentials, TeamMembersOnly)(applicationId)(fun)

  private def canChangeClientSecrets(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    checkActionForApprovedApps(ChangeClientSecret, SandboxOrAdmin)(applicationId)(fun)

  def credentials(applicationId: ApplicationId): Action[AnyContent] =
    canViewClientCredentialsPage(applicationId) { implicit request => successful(Ok(credentialsView(request.application))) }

  def clientId(applicationId: ApplicationId): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request => successful(Ok(clientIdView(request.application))) }

  def clientSecrets(applicationId: ApplicationId): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService.fetchCredentials(request.application).map { tokens => Ok(clientSecretsView(request.application, tokens.clientSecrets)) }
    }

  def serverToken(applicationId: ApplicationId): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      if (request.application.createdOn.isBefore(serverTokenCutoffDate)) {
        applicationService.fetchCredentials(request.application).map { tokens => Ok(serverTokenView(request.application, tokens.accessToken)) }
      } else {
        successful(NotFound(errorHandler.notFoundTemplate))
      }
    }

  def addClientSecret(applicationId: ApplicationId): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService.addClientSecret(request.application, request.user.email).map { response =>
        Redirect(routes.Credentials.clientSecrets(applicationId))
          .flashing("newSecretId" -> response._1, "newSecret" -> response._2)
      } recover {
        case _: ApplicationNotFound       => NotFound(errorHandler.notFoundTemplate)
        case _: ForbiddenException        => Forbidden(errorHandler.badRequestTemplate)
        case _: ClientSecretLimitExceeded => UnprocessableEntity(errorHandler.badRequestTemplate)
      }
    }

  def deleteClientSecret(applicationId: ApplicationId, clientSecretId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService.fetchCredentials(request.application).map { tokens =>
        tokens.clientSecrets
          .find(_.id == clientSecretId)
          .fold(NotFound(errorHandler.notFoundTemplate))(secret => Ok(deleteClientSecretView(request.application, secret)))
      }
    }

  def deleteClientSecretAction(applicationId: ApplicationId, clientSecretId: String): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService
        .deleteClientSecret(request.application, clientSecretId, request.user.email)
        .map(_ => Redirect(routes.Credentials.clientSecrets(applicationId)))
    }
}

object Credentials {
  val serverTokenCutoffDate = new DateTime(2020, 4, 1, 0, 0) // scalastyle:ignore magic.number
}
