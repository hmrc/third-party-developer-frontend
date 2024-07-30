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

import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, SessionId => _, _}
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId

@Singleton
class ThirdPartyDeveloperSessionConnector @Inject() (
    http: HttpClient,
    config: ApplicationConfig,
    metrics: ConnectorMetrics
  )(implicit val ec: ExecutionContext
  ) extends CommonResponseHandlers with Logging {

  lazy val serviceBaseUrl: String = config.thirdPartyDeveloperSessionUrl
  val api: API                    = API("third-party-developer-session")

  def updateSessionLoggedInState(sessionId: SessionId, request: UpdateLoggedInStateRequest)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.PUT[String, Option[UserSession]](s"$serviceBaseUrl/session/$sessionId/loggedInState/${request.loggedInState}", "")
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def fetchSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[UserSession] = metrics.record(api) {
    http.GET[Option[UserSession]](s"$serviceBaseUrl/session/$sessionId")
      .map {
        case Some(session) => session
        case None          => throw new SessionInvalid
      }
  }

  def deleteSession(sessionId: SessionId)(implicit hc: HeaderCarrier): Future[Int] = metrics.record(api) {
    http.DELETE[ErrorOr[HttpResponse]](s"$serviceBaseUrl/session/$sessionId")
      .map {
        case Right(response)                                 => response.status
        // treat session not found as successfully destroyed
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => NO_CONTENT
        case Left(err)                                       => throw err
      }
  }
}
