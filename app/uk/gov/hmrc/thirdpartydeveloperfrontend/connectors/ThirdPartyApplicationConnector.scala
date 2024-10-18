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

import org.apache.pekko.pattern.FutureTimeoutSupport

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatformmicroservice.common.utils.EbridgeConfigurator
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithSubscriptions, CheckInformation}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{UserId, _}
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.{ApplicationNameValidationRequest, ApplicationNameValidationResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService.ApplicationConnector

abstract class ThirdPartyApplicationConnector(config: ApplicationConfig, metrics: ConnectorMetrics) extends ApplicationConnector
    with CommonResponseHandlers with ApplicationLogger with HttpErrorFunctions {

  protected val http: HttpClientV2
  implicit val ec: ExecutionContext
  val environment: Environment
  val serviceBaseUrl: String
  def isEnabled: Boolean

  def configureEbridgeIfRequired: RequestBuilder => RequestBuilder

  val api: API = API("third-party-application")

  def create(request: CreateApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationCreatedResponse] =
    metrics.record(api) {
      configureEbridgeIfRequired(
        http
          .post(url"$serviceBaseUrl/application")
          .withBody(Json.toJson(request))
      )
        .execute[ApplicationWithCollaborators]
        .map(a => ApplicationCreatedResponse(a.id))
    }

  def fetchByTeamMember(userId: UserId)(implicit hc: HeaderCarrier): Future[Seq[ApplicationWithSubscriptions]] =
    if (isEnabled) {
      metrics.record(api) {
        val url = s"$serviceBaseUrl/developer/applications"

        logger.info(s"fetchByTeamMember() - About to call $url for $userId in ${environment.toString}")

        configureEbridgeIfRequired(
          http
            .get(url"$url?${Seq("userId" -> userId.toString(), "environment" -> environment.toString)}")
        )
          .execute[Seq[ApplicationWithSubscriptions]]
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

  def fetchApplicationById(id: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[ApplicationWithCollaborators]] =
    if (isEnabled) {
      metrics.record(api) {
        configureEbridgeIfRequired(
          http.get(url"$serviceBaseUrl/application/${id.value}")
        )
          .execute[Option[ApplicationWithCollaborators]]
      }
    } else {
      Future.successful(None)
    }

  def unsubscribeFromApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] =
    metrics.record(api) {
      val url = s"$serviceBaseUrl/application/${applicationId}/subscription?context=${apiIdentifier.context.value}&version=${apiIdentifier.versionNbr.value}"
      configureEbridgeIfRequired(
        http.delete(url"$url")
      )
        .execute[ErrorOrUnit]
        .map(throwOrOptionOf)
        .map {
          case Some(_) => ApplicationUpdateSuccessful
          case None    => throw new ApplicationNotFound
        }
    }

  def fetchCredentials(id: ApplicationId)(implicit hc: HeaderCarrier): Future[ApplicationToken] = metrics.record(api) {
    configureEbridgeIfRequired(
      http.get(url"$serviceBaseUrl/application/${id.value}/credentials")
    )
      .execute[Option[ApplicationToken]]
      .map {
        case Some(applicationToken) => applicationToken
        case None                   => throw new ApplicationNotFound
      }
  }

  def verify(verificationCode: String)(implicit hc: HeaderCarrier): Future[ApplicationVerificationResponse] = metrics.record(api) {
    configureEbridgeIfRequired(
      http.post(url"$serviceBaseUrl/verify-uplift/$verificationCode")
    )
      .execute[ErrorOrUnit]
      .map {
        case Right(_)                                          => ApplicationVerificationSuccessful
        case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => ApplicationVerificationFailed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _))   => throw new ApplicationNotFound
        case Left(err)                                         => throw err
      }
  }

  def updateApproval(id: ApplicationId, approvalInformation: CheckInformation)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = metrics.record(api) {
    configureEbridgeIfRequired(
      http.post(url"$serviceBaseUrl/application/${id.value}/check-information")
        .withBody(Json.toJson(approvalInformation))
    )
      .execute[ErrorOrUnit]
      .map(throwOrOptionOf)
      .map {
        case Some(_) => ApplicationUpdateSuccessful
        case None    => throw new ApplicationNotFound
      }
  }

  def validateName(name: String, selfApplicationId: Option[ApplicationId])(implicit hc: HeaderCarrier): Future[ApplicationNameValidation] = {
    val body = ApplicationNameValidationRequest(name, selfApplicationId)

    configureEbridgeIfRequired(
      http.post(url"$serviceBaseUrl/application/name/validate")
        .withBody(Json.toJson(body))
    )
      .execute[Option[ApplicationNameValidationResult]]
      .map {
        case Some(x) => ApplicationNameValidation(x)
        case None    => throw new ApplicationNotFound
      }
  }

  def fetchSubscription(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Set[ApiIdentifier]] = {
    configureEbridgeIfRequired(
      http.get(url"$serviceBaseUrl/application/${applicationId}/subscription")
    )
      .execute[Set[ApiIdentifier]]
  }

  def fetchTermsOfUseInvitations()(implicit hc: HeaderCarrier): Future[List[TermsOfUseInvitation]] = {
    metrics.record(api) {
      configureEbridgeIfRequired(
        http.get(url"$serviceBaseUrl/terms-of-use")
      )
        .execute[List[TermsOfUseInvitation]]
    }
  }

  def fetchTermsOfUseInvitation(applicationId: ApplicationId)(implicit hc: HeaderCarrier): Future[Option[TermsOfUseInvitation]] = {
    metrics.record(api) {
      configureEbridgeIfRequired(
        http.get(url"$serviceBaseUrl/terms-of-use/application/${applicationId}")
      )
        .execute[Option[TermsOfUseInvitation]]
    }
  }
}

@Singleton
class ThirdPartyApplicationSandboxConnector @Inject() (
    val http: HttpClientV2,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends ThirdPartyApplicationConnector(appConfig, metrics) {

  val environment: Environment = Environment.SANDBOX
  val serviceBaseUrl: String   = appConfig.thirdPartyApplicationSandboxUrl
  val useProxy: Boolean        = appConfig.thirdPartyApplicationSandboxUseProxy
  val apiKey: String           = appConfig.thirdPartyApplicationSandboxApiKey

  lazy val configureEbridgeIfRequired: RequestBuilder => RequestBuilder =
    EbridgeConfigurator.configure(useProxy, apiKey)

  override val isEnabled: Boolean = appConfig.hasSandbox;
}

@Singleton
class ThirdPartyApplicationProductionConnector @Inject() (
    val http: HttpClientV2,
    val futureTimeout: FutureTimeoutSupport,
    val appConfig: ApplicationConfig,
    val metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends ThirdPartyApplicationConnector(appConfig, metrics) {
  val environment: Environment = Environment.PRODUCTION
  val serviceBaseUrl: String   = appConfig.thirdPartyApplicationProductionUrl

  val configureEbridgeIfRequired: RequestBuilder => RequestBuilder = identity

  override val isEnabled = true
}
