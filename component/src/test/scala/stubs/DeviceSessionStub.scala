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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._

import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{DeviceSession, DeviceSessionId}

object DeviceSessionStub {
  val staticDeviceSessionId = DeviceSessionId(UUID.fromString("69fc10f6-9193-42b4-97f2-87886c972ad4"))

  def getDeviceSessionForSessionIdAndUserId(userId: UserId): Any = {
    stubFor(
      get(urlMatching(s"/device-session/$staticDeviceSessionId/user/$userId"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(DeviceSession(deviceSessionId = staticDeviceSessionId, userId)).toString())
        )
    )
  }

  def getDeviceSessionNotFound(userId: UserId): Any = {
    stubFor(
      get(urlMatching(s"/device-session/$staticDeviceSessionId/user/$userId"))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
        )
    )
  }

  def createDeviceSession(userId: UserId, status: Int) =
    stubFor(
      post(urlMatching(s"/device-session/user/$userId"))
        .willReturn(aResponse()
          .withBody(Json.toJson(DeviceSession(deviceSessionId = staticDeviceSessionId, userId)).toString())
          .withStatus(status))
    )

}
