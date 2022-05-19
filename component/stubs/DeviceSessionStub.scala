package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status.{OK, NOT_FOUND}
import play.api.libs.json.Json
import uk.gov.hmrc.apiplatform.modules.mfa.models.DeviceSession

import java.util.UUID
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

object DeviceSessionStub {
  val staticDeviceSessionId = UUID.fromString("69fc10f6-9193-42b4-97f2-87886c972ad4")

  def getDeviceSessionForSessionIdAndUserId(userId: UserId): Any = {
    stubFor(
      get(urlMatching(s"/device-session/$staticDeviceSessionId/user/${userId.value}"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(DeviceSession(deviceSessionId = staticDeviceSessionId, userId)).toString())
        ))
  }

    def getDeviceSessionNotFound(userId: UserId): Any = {
    stubFor(
      get(urlMatching(s"/device-session/$staticDeviceSessionId/user/${userId.value}"))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
        ))
  }


}
