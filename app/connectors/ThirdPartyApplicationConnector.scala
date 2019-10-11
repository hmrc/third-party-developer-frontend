/*
 * Copyright 2019 HM Revenue & Customs
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

import config.ApplicationConfig
import domain.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult}
import domain.DefinitionFormats._
import domain._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.metrics.API

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics) {
  protected val httpClient: HttpClient
  protected val proxiedHttpClient: ProxiedHttpClient
  implicit val ec: ExecutionContext
  val environment: Environment
  val serviceBaseUrl: String
  val useProxy: Boolean
  val bearerToken: String

  def http: HttpClient = if (useProxy) proxiedHttpClient.withHeaders(bearerToken) else httpClient

  val api = API("third-party-application")

  def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application", Json.toJson(request), Seq(CONTENT_TYPE -> JSON)) map { result =>
      ApplicationCreatedResponse((result.json \ "id").as[String])
    }
  }

  def update(applicationId: String, request: UpdateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/$applicationId", Json.toJson(request), Seq(CONTENT_TYPE -> JSON)) map { _ =>
      ApplicationUpdateSuccessful
    }
  }

  def fetchByTeamMemberEmail(email: String)(implicit hc: HeaderCarrier): Future[Seq[Application]] = metrics.record(api) {

    val url = s"$serviceBaseUrl/developer/applications"

    Logger.debug(s"fetchByTeamMemberEmail() - About to call $url for $email in ${environment.toString}")

    http.GET[Seq[Application]](url, Seq("emailAddress" -> email, "environment" -> environment.toString))
      .andThen {
        case Success(_) =>
          Logger.debug(s"fetchByTeamMemberEmail() - done call to $url for $email in ${environment.toString}")
        case _ =>
          Logger.debug(s"fetchByTeamMemberEmail() - done errored call to $url for $email in ${environment.toString}")
      }
  }

  def addTeamMember(applicationId: String, teamMember: AddTeamMemberRequest)(implicit hc: HeaderCarrier): Future[AddTeamMemberResponse] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/$applicationId/collaborator", teamMember, Seq(CONTENT_TYPE -> JSON)) map { result =>
      result.json.as[AddTeamMemberResponse]
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => throw new TeamMemberAlreadyExists
    } recover recovery
  }

  def removeTeamMember(applicationId: String,
                       teamMemberToDelete: String,
                       requestingEmail: String,
                       adminsToEmail: Seq[String])(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    val url = s"$serviceBaseUrl/application/$applicationId/collaborator/${urlEncode(teamMemberToDelete)}" +
      s"?admin=${urlEncode(requestingEmail)}&adminsToEmail=${urlEncode(adminsToEmail.mkString(","))}"
    http.DELETE(url) map { _ =>
      ApplicationUpdateSuccessful
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => throw new ApplicationNeedsAdmin
    } recover recovery
  }

  def fetchApplicationById(id: String)(implicit hc: HeaderCarrier): Future[Option[Application]] = metrics.record(api) {
    http.GET[Application](s"$serviceBaseUrl/application/$id") map {
      Some(_)
    } recover {
      case _: NotFoundException => None
    }
  }

  def fetchSubscriptions(id: String)(implicit hc: HeaderCarrier): Future[Seq[APISubscription]] = metrics.record(api) {
    http.GET[Seq[APISubscription]](s"$serviceBaseUrl/application/$id/subscription") recover {
      case _: Upstream5xxResponse => Seq.empty
      case _: NotFoundException => throw new ApplicationNotFound
    }
  }

  def subscribeToApi(applicationId: String,
                     apiIdentifier: APIIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/$applicationId/subscription", apiIdentifier, Seq(CONTENT_TYPE -> JSON)) map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def unsubscribeFromApi(applicationId: String,
                         context: String, version: String)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.DELETE(s"$serviceBaseUrl/application/$applicationId/subscription?context=$context&version=$version") map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def fetchCredentials(id: String)(implicit hc: HeaderCarrier): Future[ApplicationTokens] = metrics.record(api) {
    http.GET[ApplicationTokens](s"$serviceBaseUrl/application/$id/credentials") recover recovery
  }

  def requestUplift(applicationId: String,
                    upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/$applicationId/request-uplift", upliftRequest, Seq(CONTENT_TYPE -> JSON)) map {
      _ => ApplicationUpliftSuccessful
    } recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => throw new ApplicationAlreadyExists
    } recover recovery
  }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationSuccessful] = metrics.record(api) {
    http.POSTEmpty(s"$serviceBaseUrl/verify-uplift/$verificationCode") map {
      _ => ApplicationVerificationSuccessful
    } recover {
      case _: BadRequestException => throw new ApplicationVerificationFailed(verificationCode)
    } recover recovery
  }

  def updateApproval(id: String,
                     approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST(s"$serviceBaseUrl/application/$id/check-information", Json.toJson(approvalInformation)) map {
      _ => ApplicationUpdateSuccessful
    } recover recovery
  }

  def addClientSecrets(id: String,
                       clientSecretRequest: ClientSecretRequest)(implicit hc: HeaderCarrier): Future[ApplicationTokens] = metrics.record(api) {
    http.POST[ClientSecretRequest, ApplicationTokens](s"$serviceBaseUrl/application/$id/client-secret", clientSecretRequest) recover {
      case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => throw new ClientSecretLimitExceeded
    } recover recovery
  }

  def deleteClientSecrets(appId: String,
                          deleteClientSecretsRequest: DeleteClientSecretsRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    http.POST(s"$serviceBaseUrl/application/$appId/revoke-client-secrets", deleteClientSecretsRequest) map { _ =>
      ApplicationUpdateSuccessful
    } recover recovery
  }

  def validateName(name: String)(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    val body = ApplicationNameValidationRequest(name)

    http.POST[ApplicationNameValidationRequest, ApplicationNameValidationResult](s"$serviceBaseUrl/application/name/validate", body) map {
      ApplicationNameValidationResult.apply
    } recover recovery
  }

  private def urlEncode(str: String, encoding: String = "UTF-8") = {
    encode(str, encoding)
  }

  private def recovery: PartialFunction[Throwable, Nothing] = {
    case _: NotFoundException => throw new ApplicationNotFound
  }
}

@Singleton
class ThirdPartyApplicationSandboxConnector @Inject()(val httpClient: HttpClient,
                                                      val proxiedHttpClient: ProxiedHttpClient,
                                                      appConfig: ApplicationConfig,
                                                      metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
  extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment = Environment.SANDBOX
  val serviceBaseUrl = appConfig.thirdPartyApplicationSandboxUrl
  val useProxy = appConfig.thirdPartyApplicationSandboxUseProxy
  val bearerToken = appConfig.thirdPartyApplicationSandboxBearerToken
}

@Singleton
class ThirdPartyApplicationProductionConnector @Inject()(val httpClient: HttpClient,
                                                         val proxiedHttpClient: ProxiedHttpClient,
                                                         appConfig: ApplicationConfig,
                                                         metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
  extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment = Environment.PRODUCTION
  val serviceBaseUrl = appConfig.thirdPartyApplicationProductionUrl
  val useProxy = appConfig.thirdPartyApplicationProductionUseProxy
  val bearerToken = appConfig.thirdPartyApplicationProductionBearerToken
}
