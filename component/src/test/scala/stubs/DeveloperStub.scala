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

package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import utils.ComponentTestDeveloperBuilder

import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.Developer
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.{Registration, UpdateProfileRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.JsonFormatters.FindUserIdRequestWrites
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.{FindUserIdRequest, FindUserIdResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.PasswordResetRequest

object DeveloperStub extends ComponentTestDeveloperBuilder {

  def register(registration: Registration, status: Int)(implicit encryptedJson: EncryptedJson) =
    stubFor(
      post(urlMatching(s"/developer"))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(registration).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def update(userId: UserId, profile: UpdateProfileRequest, status: Int) =
    stubFor(
      post(urlMatching(s"/developer/$userId"))
        .withRequestBody(equalToJson(Json.toJson(profile).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def setupResend(email: LaxEmailAddress, status: Int) = {
    val userId = staticUserId

    implicit val writes = Json.writes[FindUserIdResponse]

    stubFor(
      post(urlEqualTo("/developers/find-user-id"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(FindUserIdResponse(userId)).toString)
            .withHeader("Content-Type", "application/json")
        )
    )

    stubFor(
      post(urlPathEqualTo(s"/$userId/resend-verification"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyResetPassword(request: PasswordResetRequest) = {
    verify(1, postRequestedFor(urlPathEqualTo("/password-reset-request")).withRequestBody(equalToJson(Json.toJson(request).toString())))
  }

  def findUserIdByEmailAddress(emailAddress: LaxEmailAddress) = {

    stubFor(
      post(urlEqualTo("/developers/find-user-id"))
        .withRequestBody(equalToJson(Json.toJson(FindUserIdRequest(emailAddress)).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{"userId":"${staticUserId.toString()}"}""")
        )
    )
  }

  def stubResetPasswordJourney(email: LaxEmailAddress, code: String): Unit = {
    fetchEmailForResetCode(email, code)
    resetPassword()
  }

  def stubResetPasswordJourneyFail(): Unit = {
    stubFor(
      get(urlPathEqualTo("/reset-password"))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
        )
    )
  }

  def setupGettingDeveloperByUserId(developer: Developer): Unit = {
    stubFor(get(urlPathEqualTo("/developer"))
      .withQueryParam("developerId", equalTo(developer.userId.toString()))
      .willReturn(aResponse()
        .withStatus(OK)
        .withBody(Json.toJson(developer).toString())))
  }

  def fetchEmailForResetCode(email: LaxEmailAddress, code: String) = {
    stubFor(
      get(urlPathEqualTo("/reset-password"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{ "email": "$email" }"""")
        )
    )
  }

  def resetPassword() = {
    stubFor(
      post(urlEqualTo("/reset-password"))
        .willReturn(
          aResponse()
            .withStatus(OK)
        )
    )
  }
}
