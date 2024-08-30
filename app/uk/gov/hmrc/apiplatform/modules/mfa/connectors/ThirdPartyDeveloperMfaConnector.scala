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

package uk.gov.hmrc.apiplatform.modules.mfa.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{DeviceSession, MfaId}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{CommonResponseHandlers, ConnectorMetrics}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session.DeviceSessionInvalid

@Singleton
class ThirdPartyDeveloperMfaConnector @Inject() (
    http: HttpClientV2,
    config: ApplicationConfig,
    metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends CommonResponseHandlers with Logging {

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api: API                    = API("third-party-developer")

  def createMfaAuthApp(userId: UserId)(implicit hc: HeaderCarrier): Future[RegisterAuthAppResponse] = {
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/developer/$userId/mfa/auth-app")
        .execute[RegisterAuthAppResponse]
    }
  }

  def createMfaSms(userId: UserId, mobileNumber: String)(implicit hc: HeaderCarrier): Future[Option[RegisterSmsResponse]] = {
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/developer/$userId/mfa/sms")
        .withBody(Json.toJson(CreateMfaSmsRequest(mobileNumber)))
        .execute[RegisterSmsResponse]
        .map(Some(_))
        .recover {
          case _ => None
        }
    }
  }

  def verifyMfa(userId: UserId, mfaId: MfaId, code: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/developer/$userId/mfa/$mfaId/verification")
        .withBody(Json.toJson(VerifyMfaCodeRequest(code)))
        .execute[ErrorOrUnit]
        .map {
          case Right(())                                         => true
          case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => false
          case Left(err)                                         => throw err
        }
    }
  }

  def sendSms(userId: UserId, mfaId: MfaId)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http
        .post(url"$serviceBaseUrl/developer/$userId/mfa/$mfaId/send-sms")
        .execute[ErrorOrUnit]
        .map {
          case Right(())                                         => true
          case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => false
          case Left(err)                                         => throw err
        }
    }
  }

  def removeMfaById(userId: UserId, mfaId: MfaId)(implicit hc: HeaderCarrier): Future[Unit] = {
    metrics.record(api) {
      http.delete(url"$serviceBaseUrl/developer/$userId/mfa/$mfaId")
        .execute[ErrorOrUnit]
        .map(throwOrUnit)
    }
  }

  def changeName(userId: UserId, mfaId: MfaId, updatedName: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http.post(url"$serviceBaseUrl/developer/$userId/mfa/$mfaId/name")
        .withBody(Json.toJson(ChangeMfaNameRequest(updatedName)))
        .execute[ErrorOrUnit]
        .map {
          case Right(())                                         => true
          case Left(UpstreamErrorResponse(_, BAD_REQUEST, _, _)) => false
          case Left(err)                                         => throw err
        }
    }
  }

  def createDeviceSession(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[DeviceSession]] = metrics.record(api) {
    http.post(url"$serviceBaseUrl/device-session/user/$userId")
      .execute[ErrorOr[DeviceSession]]
      .map {
        case Right(response)                                 => Some(response)
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => {
          logger.error(s"Error creating Device Session - NOT FOUND returned from TPD")
          None
        }
        case Left(err)                                       => {
          logger.error(s"Error creating Device Session - ${err.getMessage()}")
          throw err
        }
      }
  }

  def fetchDeviceSession(deviceSessionId: String, userId: UserId)(implicit hc: HeaderCarrier): Future[DeviceSession] = metrics.record(api) {
    http.get(url"$serviceBaseUrl/device-session/$deviceSessionId/user/$userId")
      .execute[Option[DeviceSession]]
      .map {
        case Some(deviceSession) => deviceSession
        case None                => throw new DeviceSessionInvalid
      }
  }

  def deleteDeviceSession(deviceSessionId: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.delete(url"$serviceBaseUrl/device-session/$deviceSessionId")
      .execute[ErrorOr[HttpResponse]]
      .map {
        case Right(response)                                 => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => NO_CONTENT
        case Left(err)                                       => throw err
      }
  }
}
