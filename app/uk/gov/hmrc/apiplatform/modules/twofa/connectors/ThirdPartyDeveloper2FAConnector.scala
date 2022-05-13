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

package uk.gov.hmrc.apiplatform.modules.twofa.connectors

import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.http.metrics.common.API
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{CommonResponseHandlers, ConnectorMetrics}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ThirdPartyDeveloper2FAConnector @Inject()(http: HttpClient, config: ApplicationConfig, metrics: ConnectorMetrics
                                            )(implicit val ec: ExecutionContext) extends CommonResponseHandlers with Logging {

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperUrl
  val api = API("third-party-developer")

  def createDeviceSession(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[DeviceSession]] = metrics.record(api) {
      http.POST[String ,ErrorOr[DeviceSession]](s"$serviceBaseUrl/device-session/user/${userId.toString}", "")
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
    http.GET[Option[DeviceSession]](s"$serviceBaseUrl/device-session/$deviceSessionId/user/${userId.toString}")
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

