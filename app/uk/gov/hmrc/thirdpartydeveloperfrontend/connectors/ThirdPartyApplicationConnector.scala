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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

import akka.pattern.FutureTimeoutSupport

import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiIdentifier
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.apiplatform.modules.developers.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService.ApplicationConnector

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics) extends ApplicationConnector
    with CommonResponseHandlers with ApplicationLogger with HttpErrorFunctions with ApplicationUpdateFormatters {

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
    http.POST[UpdateApplicationRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.text}", request).map(throwOr(ApplicationUpdateSuccessful))
  }

  def applicationUpdate(applicationId: ApplicationId, request: ApplicationUpdate)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.PATCH[ApplicationUpdate, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.text}", request).map(throwOr(ApplicationUpdateSuccessful))
  }

  def fetchByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptionIds]] =
    if (isEnabled) {
      metrics.record(api) {
        val url = s"$serviceBaseUrl/developer/applications"

        logger.info(s"fetchByTeamMember() - About to call $url for $userId in ${environment.toString}")

        http
          .GET[Seq[ApplicationWithSubscriptionIds]](url, Seq("userId" -> userId.asText, "environment" -> environment.toString))
          .andThen {
            case Success(_) =>
              logger.debug(s"fetchByTeamMember() - done call to $url for $userId in ${environment.toString}")
            case _          =>
              logger.debug(s"fetchByTeamMember() - done errored call to $url for $userId in ${environment.toString}")
          }
      }
    } else {
      Future.successful(Seq.empty)
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
      http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.text}/subscription?context=${apiIdentifier.context.value}&version=${apiIdentifier.version.value}")
        .map(throwOrOptionOf)
        .map {
          case Some(_) => ApplicationUpdateSuccessful
          case None    => throw new ApplicationNotFound
        }
    }

  def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken] = metrics.record(api) {
    http.GET[Option[ApplicationToken]](s"$serviceBaseUrl/application/${id.value}/credentials")
      .map {
        case Some(applicationToken) => applicationToken
        case None                   => throw new ApplicationNotFound
      }
  }

  def requestUplift(applicationId: ApplicationId, upliftRequest: UpliftRequest)(implicit hc: HeaderCarrier): Future[ApplicationUpliftSuccessful] = metrics.record(api) {
    http.POST[UpliftRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.text}/request-uplift", upliftRequest)
      .map {
        case Right(_)                                        => ApplicationUpliftSuccessful
        case Left(UpstreamErrorResponse(_, CONFLICT, _, _))  => throw new ApplicationAlreadyExists
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => throw new ApplicationNotFound
        case Left(err)                                       => throw err
      }
  }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    http.POSTEmpty[ErrorOrUnit](s"$serviceBaseUrl/verify-uplift/$verificationCode")
      .map {
        case Right(_)                                          => ApplicationVerificationSuccessful
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => ApplicationVerificationFailed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))   => throw new ApplicationNotFound
        case Left(err)                                         => throw err
      }
  }

  def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    http.POST[CheckInformation, ErrorOrUnit](s"$serviceBaseUrl/application/${id.value}/check-information", approvalInformation)
      .map(throwOrOptionOf)
      .map {
        case Some(_) => ApplicationUpdateSuccessful
        case None    => throw new ApplicationNotFound
      }
  }

  def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    val body = ApplicationNameValidationRequest(name, selfApplicationId)

    http.POST[ApplicationNameValidationRequest, Option[ApplicationNameValidationResult]](s"$serviceBaseUrl/application/name/validate", body)
      .map {
        case Some(x) => ApplicationNameValidation(x)
        case None    => throw new ApplicationNotFound
      }
  }

  def updateIpAllowlist(applicationId: ApplicationId, required: Boolean, ipAllowlist: Set[String])(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {
      http.PUT[UpdateIpAllowlistRequest, ErrorOrUnit](s"$serviceBaseUrl/application/${applicationId.text}/ipAllowlist", UpdateIpAllowlistRequest(required, ipAllowlist))
        .map(throwOrOptionOf)
        .map {
          case Some(_) => ApplicationUpdateSuccessful
          case None    => throw new ApplicationNotFound
        }
    }

  def fetchSubscription(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] = {
    http.GET[Set[ApiIdentifier]](s"$serviceBaseUrl/application/${applicationId.text}/subscription")
  }

  def fetchTermsOfUseInvitations()(implicit hc: HeaderCarrier): Future[List[TermsOfUseInvitation]] = {
    metrics.record(api) {
      http.GET[List[TermsOfUseInvitation]](s"$serviceBaseUrl/terms-of-use")
    }
  }

  def fetchTermsOfUseInvitation(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[TermsOfUseInvitation]] = {
    metrics.record(api) {
      http.GET[Option[TermsOfUseInvitation]](s"$serviceBaseUrl/terms-of-use/application/${applicationId.text}")
    }
  }
}

private[connectors] object ThirdPartyApplicationConnectorDomain {
  case class UpdateIpAllowlistRequest(required: Boolean, allowlist: Set[String])
}

@Singleton
class ThirdPartyApplicationSandboxConnector @Inject() (
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment    = Environment.SANDBOX
  val serviceBaseUrl = appConfig.thirdPartyApplicationSandboxUrl
  val useProxy       = appConfig.thirdPartyApplicationSandboxUseProxy
  val apiKey         = appConfig.thirdPartyApplicationSandboxApiKey

  override val isEnabled = appConfig.hasSandbox;
}

@Singleton
class ThirdPartyApplicationProductionConnector @Inject() (
    val httpClient: HttpClient,
    val proxiedHttpClient: ProxiedHttpClient,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends ThirdPartyApplicationConnector(appConfig, metrics) {
  val environment    = Environment.PRODUCTION
  val serviceBaseUrl = appConfig.thirdPartyApplicationProductionUrl
  val useProxy       = appConfig.thirdPartyApplicationProductionUseProxy
  val apiKey         = appConfig.thirdPartyApplicationProductionApiKey

  override val isEnabled = true
}
