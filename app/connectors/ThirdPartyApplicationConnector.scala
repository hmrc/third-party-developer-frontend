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

package connectors

import java.net.URLEncoder.encode

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import domain.models.applications._
import domain.models.applications.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult}
import domain.models.connectors.{AddTeamMemberRequest, AddTeamMemberResponse}
import domain.models.subscriptions.APISubscription
import domain._
import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import helpers.Retries
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import service.ApplicationService.ApplicationConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics) extends ApplicationConnector with Retries {

  import ApplicationConnectorDomain._
  import ThirdPartyApplicationConnectorJsonFormatters._

  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  implicit val ec: ExecutionContext
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val bearerToken: String
  val apiKey: String
  def isEnabled: Boolean

  def http: HttpClient = if (useProxy) proxiedHttpClient.withHeaders(bearerToken, apiKey) else httpClient

  val api = API("third-party-application")

  def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application", Json.toJson(request), Seq(CONTENT_TYPE -> JSON)) map { result => ApplicationCreatedResponse((result.json \ "id").as[ApplicationId]) }
  }

  def update(applicationId: ApplicationId, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/${applicationId.value}", Json.toJson(request), Seq(CONTENT_TYPE -> JSON)) map { _ => ApplicationUpdateSuccessful }
  }

  def fetchByTeamMemberEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]] =
    if (isEnabled) {
      metrics.record(api) {
        retry {
          val url = s"$serviceBaseUrl/developer/applications"

          Logger.debug(s"fetchByTeamMemberEmail() - About to call $url for $email in ${environment.toString}")

          http
            .GET[Seq[Application]](url, Seq("emailAddress" -> email, "environment" -> environment.toString))
            .andThen {
              case Success(_) =>
                Logger.debug(s"fetchByTeamMemberEmail() - done call to $url for $email in ${environment.toString}")
              case _ =>
                Logger.debug(s"fetchByTeamMemberEmail() - done errored call to $url for $email in ${environment.toString}")
            }
        }
      }
    } else {
      Future.successful(Seq.empty)
    }

  def addTeamMember(applicationId: ApplicationId, teamMember: AddTeamMemberRequest)(implicit hc: HeaderCarrier): Future[AddTeamMemberResponse] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/${applicationId.value}/collaborator", teamMember, Seq(CONTENT_TYPE -> JSON)) map { result =>
      result.json.as[AddTeamMemberResponse]
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => throw new TeamMemberAlreadyExists
    } recover recovery
  }

  def removeTeamMember(applicationId: ApplicationId, teamMemberToDelete: String, requestingEmail: String, adminsToEmail: Seq[String])(
      implicit hc: HeaderCarrier
  ): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    val url = s"$serviceBaseUrl/application/${applicationId.value}/collaborator/${urlEncode(teamMemberToDelete)}" +
      s"?admin=${urlEncode(requestingEmail)}&adminsToEmail=${urlEncode(adminsToEmail.mkString(","))}"
    http.DELETE(url) map { _ =>
      ApplicationUpdateSuccessful
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => throw new ApplicationNeedsAdmin
    } recover recovery
  }

  def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[Application]] =
    if (isEnabled) {
      metrics.record(api) {
        retry {
          http.GET[Application](s"$serviceBaseUrl/application/${id.value}") map {
            Some(_)
          } recover {
            case _: NotFoundException => None
          }
        }
      }
    } else {
      Future.successful(None)
    }

  def fetchSubscriptions(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Seq[APISubscription]] =
    if (isEnabled) {
      metrics.record(api) {
        retry {
          http.GET[Seq[APISubscription]](s"$serviceBaseUrl/application/${id.value}/subscription") recover {
            case _: Upstream5xxResponse => Seq.empty
            case _: NotFoundException   => throw new ApplicationNotFound
          }
        }
      }
    } else {
      Future.successful(Seq.empty)
    }

  def subscribeToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/${applicationId.value}/subscription", apiIdentifier, Seq(CONTENT_TYPE -> JSON)) map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def unsubscribeFromApi(applicationId: ApplicationId, context: ApiContext, version: ApiVersion)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {
      http.DELETE(s"$serviceBaseUrl/application/${applicationId.value}/subscription?context=${context.value}&version=${version.value}") map { _ =>
        ApplicationUpdateSuccessful
      } recover recovery
    }

  def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken] = metrics.record(api) {
    retry {
      http.GET[ApplicationToken](s"$serviceBaseUrl/application/${id.value}/credentials") recover recovery
    }
  }

  def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/${applicationId.value}/request-uplift", upliftRequest, Seq(CONTENT_TYPE -> JSON)) map { _ =>
      ApplicationUpliftSuccessful
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => throw new ApplicationAlreadyExists
    } recover recovery
  }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    http.POSTEmpty(s"$serviceBaseUrl/verify-uplift/$verificationCode") map { _ =>
      ApplicationVerificationSuccessful
    } recover {
      case _: BadRequestException => ApplicationVerificationFailed
    } recover recovery
  }

  def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/${id.value}/check-information", Json.toJson(approvalInformation)) map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def addClientSecrets(id: ApplicationId, clientSecretRequest: ClientSecretRequest)(implicit hc: HeaderCarrier): Future[(String, String)] = metrics.record(api) {

    http.POST[ClientSecretRequest, AddClientSecretResponse](s"$serviceBaseUrl/application/${id.value}/client-secret", clientSecretRequest) map { response =>
      // API-4275: Once actual secret is only returned by TPA for new ones, will be able to find based on 'secret' field being defined
//      val newSecret: TPAClientSecret = response.clientSecrets.find(_.secret.isDefined).getOrElse(throw new NotFoundException("New Client Secret Not Found"))
      val newSecret: TPAClientSecret = response.clientSecrets.last
      (newSecret.id, newSecret.secret.get)
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => throw new ClientSecretLimitExceeded
    } recover recovery
  }

  def deleteClientSecret(applicationId: ApplicationId, clientSecretId: String, actorEmailAddress: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {

      http.POST(s"$serviceBaseUrl/application/${applicationId.value}/client-secret/$clientSecretId", DeleteClientSecretRequest(actorEmailAddress)) map { _ =>
        ApplicationUpdateSuccessful
      } recover recovery
    }

  def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    val body = ApplicationNameValidationRequest(name, selfApplicationId)

    retry {
      http.POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](s"$serviceBaseUrl/application/name/validate", body) map {
        ApplicationNameValidationResult.apply
      } recover recovery
    }
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = {
    encode(str, encoding)
  }

  private def recovery: PartialFunction[Throwable, Nothing] = {
    case _: NotFoundException => throw new ApplicationNotFound
  }

  def deleteApplication(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Unit] = {
    http
      .POSTEmpty[HttpResponse](s"$serviceBaseUrl/application/${applicationId.value}/delete")
      .map(response =>
        response.status match {
          case NO_CONTENT => ()
          case _          => throw new Exception("error deleting subordinate application")
        }
      )
  }
}

private[connectors] object ApplicationConnectorDomain {
  import domain.models.applications.{ClientId, ClientSecret}
  import org.joda.time.DateTime

  def toDomain(tpaClientSecret: TPAClientSecret): ClientSecret =
    ClientSecret(tpaClientSecret.id, tpaClientSecret.name, tpaClientSecret.createdOn, tpaClientSecret.lastAccess)

  case class AddClientSecretResponse(clientId: ClientId, accessToken: String, clientSecrets: List[TPAClientSecret])

  case class TPAClientSecret(id: String, name: String, secret: Option[String], createdOn: DateTime, lastAccess: Option[DateTime])

  case class DeleteClientSecretRequest(actorEmailAddress: String)
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
  val bearerToken = appConfig.thirdPartyApplicationSandboxBearerToken
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
  val bearerToken = appConfig.thirdPartyApplicationProductionBearerToken
  val apiKey = appConfig.thirdPartyApplicationProductionApiKey

  override val isEnabled = true
}
