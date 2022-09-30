/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.RegisterAuthAppResponse
import uk.gov.hmrc.apiplatform.modules.mfa.models.{DeviceSession, DeviceSessionInvalid, MfaId, SmsMfaDetailSummary}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.metrics.common.API
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{CommonResponseHandlers, ConnectorMetrics}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.VerifyMfaRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers._
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaDetailFormats.smsMfaDetailSummaryFormat

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}



object ThirdPartyDeveloperMfaConnector {

  case class RegisterAuthAppResponse(secret: String, mfaId: MfaId)
  implicit val registerAuthAppResponseFormat = Json.format[RegisterAuthAppResponse]

}

@Singleton
class ThirdPartyDeveloperMfaConnector @Inject()(http: HttpClient, config: ApplicationConfig, metrics: ConnectorMetrics
                                            )(implicit val ec: ExecutionContext) extends CommonResponseHandlers with Logging {

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api = API("third-party-developer")

  def createMfaAuthApp(userId: UserId)(implicit hc: HeaderCarrier): Future[RegisterAuthAppResponse] = {
    metrics.record(api) {
      http.POSTEmpty[RegisterAuthAppResponse](s"$serviceBaseUrl/developer/${userId.value}/mfa/auth-app")
    }
  }

  def createMfaSms(userId: UserId, mobileNumber: String)(implicit hc: HeaderCarrier): Future[SmsMfaDetailSummary] = {
    metrics.record(api) {
      http.POST[CreateMfaSmsRequest, SmsMfaDetailSummary](s"$serviceBaseUrl/developer/${userId.value}/mfa/sms", CreateMfaSmsRequest(mobileNumber))
    }
  }

  def verifyMfa(userId: UserId, mfaId: MfaId, code: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http.POST[VerifyMfaRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/${mfaId.value}/verification", VerifyMfaRequest(code))
        .map {
          case Right(()) => true
          case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => false
          case Left(err) => throw err
        }
    }
  }

  def removeMfaById(userId: UserId, mfaId: MfaId)(implicit hc: HeaderCarrier): Future[Unit] = {
    metrics.record(api) {
      http.DELETE[ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/${mfaId.value}")
        .map(throwOrUnit)
    }
  }

  def changeName(userId: UserId, mfaId: MfaId, updatedName: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    metrics.record(api) {
      http.POST[ChangeMfaNameRequest, ErrorOrUnit](s"$serviceBaseUrl/developer/${userId.value}/mfa/${mfaId.value}/name", ChangeMfaNameRequest(updatedName))
        .map {
          case Right(()) => true
          case Left(UpstreamErrorResponse(_,BAD_REQUEST,_,_)) => false
          case Left(err) => throw err
        }
    }
  }

  def createDeviceSession(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[DeviceSession]] = metrics.record(api) {
      http.POST[String ,ErrorOr[DeviceSession]](s"$serviceBaseUrl/device-session/user/${userId.value}", "")
      .map {
        case Right(response) => Some(response)
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => {
          logger.error(s"Error creating Device Session - NOT FOUND returned from TPD")
          None
        }
        case Left(err) => {
          logger.error(s"Error creating Device Session - ${err.getMessage()}")
          throw err
        }

      }
  }

  def fetchDeviceSession(deviceSessionId: String, userId: UserId)(implicit hc: HeaderCarrier): Future[DeviceSession] = metrics.record(api) {
    http.GET[Option[DeviceSession]](s"$serviceBaseUrl/device-session/$deviceSessionId/user/${userId.value}")
      .map {
        case Some(deviceSession) => deviceSession
        case None => throw new DeviceSessionInvalid
      }
  }

  def deleteDeviceSession(deviceSessionId: String)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE[ErrorOr[HttpResponse]](s"$serviceBaseUrl/device-session/$deviceSessionId")
      .map {
        case Right(response) => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_,NOT_FOUND,_,_)) => NO_CONTENT
        case Left(err) => throw err
      }
  }

}

