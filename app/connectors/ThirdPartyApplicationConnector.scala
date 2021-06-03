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

package connectors

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import domain._
import domain.models.applications.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult}
import domain.models.applications._
import domain.models.connectors.DeleteCollaboratorRequest
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
import service.ApplicationService.ApplicationConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import domain.models.developers.UserId
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.http.HttpReads.Implicits._

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics) extends ApplicationConnector with CommonResponseHandlers {

  import ThirdPartyApplicationConnectorDomain._
  import ThirdPartyApplicationConnectorJsonFormatters._

  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  implicit val ec: ExecutionContext
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val apiKey: String
  def isEnabled: Boolean

  def http: HttpClient = if (useProxy) proxiedHttpClient.withHeaders(apiKey) else httpClient

  val api = API("third-party-application")


  def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    metrics.record(api) {
      http.POST[CreateApplicationRequest, Application](s"$serviceBaseUrl/application", request).map(a => ApplicationCreatedResponse(a.id))
    }

  def update(applicationId: ApplicationId, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST[UpdateApplicationRequest,ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.value}", request).map(throwOr(ApplicationUpdateSuccessful))
  }
  
  def fetchByTeamMemberUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[Application]] =
    if (isEnabled) {
      metrics.record(api) {
        val url = s"$serviceBaseUrl/developer/applications"

        Logger.debug(s"fetchByTeamMemberUserId() - About to call $url for $userId in ${environment.toString}")

        http
          .GET[Seq[Application]](url, Seq("userId" -> userId.asText, "environment" -> environment.toString))
          .andThen {
            case Success(_) =>
              Logger.debug(s"fetchByTeamMemberUserId() - done call to $url for $userId in ${environment.toString}")
            case _ =>
              Logger.debug(s"fetchByTeamMemberUserId() - done errored call to $url for $userId in ${environment.toString}")
          }
      }
    } else {
      Future.successful(Seq.empty)
    }

  def removeTeamMember(applicationId: ApplicationId, teamMemberToDelete: String, requestingEmail: String, adminsToEmail: Set[String])(implicit hc: HeaderCarrier ): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) { 
      val url = s"$serviceBaseUrl/application/${applicationId.value}/collaborator/delete"
      val request = DeleteCollaboratorRequest(teamMemberToDelete, adminsToEmail, true)

      http.POST[DeleteCollaboratorRequest, ErrorOrUnit](url, request)
      .map(_ match {
        case Right(_)                                               => ApplicationUpdateSuccessful
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _))        => throw new ApplicationNeedsAdmin
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))        => throw new ApplicationNotFound
        case Left(err)                                              => throw err
      })
    }
  
  def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Application]] =
    if (isEnabled) {
      metrics.record(api) {
        http.GET[Option[Application]](s"$serviceBaseUrl/application/${id.value}")
      }
    } else {
      Future.successful(None)
    }

  def unsubscribeFromApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {
      http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.value}/subscription?context=${apiIdentifier.context.value}&version=${apiIdentifier.version.value}")
     .map(throwOrOptionOf)
      .map(_ match {
        case Some(_)  => ApplicationUpdateSuccessful
        case None     => throw new ApplicationNotFound
      })
    }

  def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken] = metrics.record(api) {
      http.GET[Option[ApplicationToken]](s"$serviceBaseUrl/application/${id.value}/credentials")
      .map(_ match {
        case Some(applicationToken)  => applicationToken
        case None     => throw new ApplicationNotFound
      })
  }
  
  def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = metrics.record(api) {
    http.POST[UpliftRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.value}/request-uplift", upliftRequest)
    .map(_ match {
      case Right(_) => ApplicationUpliftSuccessful
      case Left(UpstreamErrorResponse(_, CONFLICT, _, _))   => throw new ApplicationAlreadyExists
      case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))  => throw new ApplicationNotFound
      case Left(err) => throw err
    })
  }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    http.POSTEmpty[ErrorOrUnit](s"$serviceBaseUrl/verify-uplift/$verificationCode")
    .map(_ match {
      case Right(_)                                        => ApplicationVerificationSuccessful
      case Left(UpstreamErrorResponse(_,BAD_REQUEST, _,_)) => ApplicationVerificationFailed
      case Left(UpstreamErrorResponse(_,NOT_FOUND, _, _))  => throw new ApplicationNotFound
      case Left(err)                                       => throw err
    })
  }

  def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST[CheckInformation, ErrorOrUnit](s"$serviceBaseUrl/application/${id.value}/check-information", approvalInformation)
     .map(throwOrOptionOf)
      .map(_ match {
        case Some(_)  => ApplicationUpdateSuccessful
        case None     => throw new ApplicationNotFound
      })
  }

  def addClientSecrets(id: ApplicationId, clientSecretRequest: ClientSecretRequest)(implicit hc: HeaderCarrier): Future[(String, String)] = metrics.record(api) {
   http.POST[ClientSecretRequest, Either[UpstreamErrorResponse, AddClientSecretResponse]](s"$serviceBaseUrl/application/${id.value}/client-secret", clientSecretRequest)
    .map( _ match {
      case Right(response) => { 
        val newSecret: TPAClientSecret = response.clientSecrets.last
        ((newSecret.id, newSecret.secret.get))
      }
      case Left(UpstreamErrorResponse(_,FORBIDDEN,_,_))   => throw new ClientSecretLimitExceeded
      case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_))   => throw new ApplicationNotFound
      case Left(err) => throw err
    })
  }


  def deleteClientSecret(applicationId: ApplicationId, clientSecretId: String, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {

      http.POST[DeleteClientSecretRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.value}/client-secret/$clientSecretId", DeleteClientSecretRequest(actorEmailAddress))
      .map(throwOrOptionOf)
      .map(_ match {
        case Some(_)  => ApplicationUpdateSuccessful
        case None     => throw new ApplicationNotFound
      })
    }

  def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    val body = ApplicationNameValidationRequest(name, selfApplicationId)

    http.POST[ApplicationNameValidationRequest, Option[ApplicationNameValidationResult]](s"$serviceBaseUrl/application/name/validate", body)
      .map(_ match {
        case Some(x)  => ApplicationNameValidation(x)
        case None     => throw new ApplicationNotFound
      })
  }

  def updateIpAllowlist(applicationId: ApplicationId, required: Boolean, ipAllowlist: Set[String])(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.PUT[UpdateIpAllowlistRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.value}/ipAllowlist", UpdateIpAllowlistRequest(required, ipAllowlist))
      .map(throwOrOptionOf)
      .map(_ match {
        case Some(_)  => ApplicationUpdateSuccessful
        case None     => throw new ApplicationNotFound
      })
  }

  def deleteApplication(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Unit] = {
    http
      .POSTEmpty[HttpResponse](s"$serviceBaseUrl/application/${applicationId.value}/delete")
      .map(_.status match {
          case NO_CONTENT => ()
          case _          => throw new Exception("error deleting subordinate application")
        }
      )
  }
}

private[connectors] object ThirdPartyApplicationConnectorDomain {
  import domain.models.applications.{ClientId, ClientSecret}
  import org.joda.time.DateTime

  def toDomain(tpaClientSecret: TPAClientSecret): ClientSecret =
    ClientSecret(tpaClientSecret.id, tpaClientSecret.name, tpaClientSecret.createdOn, tpaClientSecret.lastAccess)

  case class AddClientSecretResponse(clientId: ClientId, accessToken: String, clientSecrets: List[TPAClientSecret])

  case class TPAClientSecret(id: String, name: String, secret: Option[String], createdOn: DateTime, lastAccess: Option[DateTime])

  case class DeleteClientSecretRequest(actorEmailAddress: String)

  case class UpdateIpAllowlistRequest(required: Boolean, allowlist: Set[String])
}

@Singleton
class ThirdPartyApplicationSandboxConnector @Inject() (
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
)(implicit val ec: ExecutionContext)
    extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment = Environment.SANDBOX
  val serviceBaseUrl = appConfig.thirdPartyApplicationSandboxUrl
  val useProxy = appConfig.thirdPartyApplicationSandboxUseProxy
  val apiKey = appConfig.thirdPartyApplicationSandboxApiKey

  override val isEnabled = appConfig.hasSandbox;
}

@Singleton
class ThirdPartyApplicationProductionConnector @Inject() (
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val actorSystem: ActorSystem,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
)(implicit val ec: ExecutionContext)
    extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment = Environment.PRODUCTION
  val serviceBaseUrl = appConfig.thirdPartyApplicationProductionUrl
  val useProxy = appConfig.thirdPartyApplicationProductionUseProxy
  val apiKey = appConfig.thirdPartyApplicationProductionApiKey

  override val isEnabled = true
}
