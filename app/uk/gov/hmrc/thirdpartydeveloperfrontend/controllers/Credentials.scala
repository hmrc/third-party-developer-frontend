/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Credentials.serverTokenCutoffDate
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, CollaboratorActor}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, ViewCredentials}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}

import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.http.ForbiddenException
import views.html.{ClientIdView, ClientSecretsGeneratedView, ClientSecretsView, CredentialsView, ServerTokenView}
import views.html.editapplication.DeleteClientSecretView

import java.time.LocalDateTime
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
    clientSecretsGeneratedView: ClientSecretsGeneratedView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc) {

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
      val developer = request.developerSession.developer
      applicationService.addClientSecret(request.application, CollaboratorActor(developer.email)).map { response =>
        Ok(clientSecretsGeneratedView(request.application, applicationId, response._2))
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
        .deleteClientSecret(request.application, CollaboratorActor(request.developerSession.email), clientSecretId)
        .map(_ => Redirect(routes.Credentials.clientSecrets(applicationId)))
    }
}

object Credentials {
  val serverTokenCutoffDate = LocalDateTime.of(2020, 4, 1, 0, 0) // scalastyle:ignore magic.number
}
