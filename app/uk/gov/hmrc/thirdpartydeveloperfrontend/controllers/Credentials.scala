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

import java.time.{Clock, LocalDate}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.NonEmptyList
import views.html.editapplication.DeleteClientSecretView
import views.html.{ClientIdView, ClientSecretsGeneratedView, ClientSecretsView, CredentialsView, ServerTokenView}

import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result => PlayResult}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ClientSecret
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, CommandFailure, CommandFailures, CommandHandlerTypes}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.common.services.DateTimeHelper.LocalDateConversionSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApplicationCommandConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Credentials.serverTokenCutoffDate
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, ViewCredentials}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.{SandboxOrAdmin, TeamMembersOnly}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators

@Singleton
class Credentials @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    clientSecretHashingService: ClientSecretHashingService,
    appCmdDispatcher: ApplicationCommandConnector,
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
    clientSecretsGeneratedView: ClientSecretsGeneratedView,
    val clock: Clock
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with ClockNow
    with CommandHandlerTypes[ApplicationWithCollaborators] {

  private def canViewClientCredentialsPage(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[PlayResult]): Action[AnyContent] =
    checkActionForApprovedApps(ViewCredentials, TeamMembersOnly)(applicationId)(fun)

  private def canChangeClientSecrets(applicationId: ApplicationId)(fun: ApplicationRequest[AnyContent] => Future[PlayResult]): Action[AnyContent] =
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
      if (request.application.details.createdOn.isBefore(serverTokenCutoffDate)) {
        applicationService.fetchCredentials(request.application).map { tokens => Ok(serverTokenView(request.application, tokens.accessToken)) }
      } else {
        errorHandler.notFoundTemplate.map(NotFound(_))
      }
    }

  private def fails(applicationId: ApplicationId)(e: Failures): PlayResult = {
    val details = e.toList.map(CommandFailures.describe)

    logger.warn(s"Command Process failed for $applicationId because ${details.mkString("[", ",", "]")}")
    BadRequest(Json.toJson(e.toList))
  }

  def addClientSecret(applicationId: ApplicationId): Action[AnyContent] = canChangeClientSecrets(applicationId) { implicit request =>
    val developer                   = request.userSession.developer
    val (secretValue, hashedSecret) = clientSecretHashingService.generateSecretAndHash()
    val cmd                         = ApplicationCommands.AddClientSecret(
      actor = Actors.AppCollaborator(developer.email),
      name = secretValue.takeRight(4),
      id = ClientSecret.Id.random,
      hashedSecret = hashedSecret,
      instant()
    )

    appCmdDispatcher.dispatch(applicationId, cmd, Set.empty).flatMap {
      case Right(response)                                                                                          => successful(Ok(clientSecretsGeneratedView(response.applicationResponse, applicationId, secretValue)))
      case Left(NonEmptyList(CommandFailures.ApplicationNotFound, Nil))                                             => errorHandler.notFoundTemplate.map(NotFound(_))
      case Left(NonEmptyList(CommandFailures.GenericFailure("App is in PRODUCTION so User must be an ADMIN"), Nil)) => errorHandler.badRequestTemplate.map(Forbidden(_))
      case Left(NonEmptyList(CommandFailures.ClientSecretLimitExceeded, Nil))                                       => errorHandler.badRequestTemplate.map(UnprocessableEntity(_))
      case Left(failures: NonEmptyList[CommandFailure])                                                             => successful(fails(applicationId)(failures))
    }
  }

  def deleteClientSecret(applicationId: ApplicationId, clientSecretId: ClientSecret.Id): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      applicationService.fetchCredentials(request.application).flatMap { tokens =>
        tokens.clientSecrets
          .find(_.id == clientSecretId)
          .fold(errorHandler.notFoundTemplate.map(NotFound(_)))(secret => successful(Ok(deleteClientSecretView(request.application, secret))))
      }
    }

  def deleteClientSecretAction(applicationId: ApplicationId, clientSecretId: ClientSecret.Id): Action[AnyContent] =
    canChangeClientSecrets(applicationId) { implicit request =>
      val developer = request.userSession.developer
      val cmd       = ApplicationCommands.RemoveClientSecret(
        actor = Actors.AppCollaborator(developer.email),
        clientSecretId,
        instant()
      )
      appCmdDispatcher.dispatch(applicationId, cmd, Set.empty)
        .map(_ => Redirect(routes.Credentials.clientSecrets(applicationId)))
    }
}

object Credentials {
  val serverTokenCutoffDate = LocalDate.of(2020, 4, 1).asInstant // scalastyle:ignore magic.number
}
