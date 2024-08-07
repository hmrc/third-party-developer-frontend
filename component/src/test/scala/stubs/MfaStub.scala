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
import steps.TestContext

import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.{ChangeMfaNameRequest, CreateMfaSmsRequest}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson

object MfaStub {

  private val accessCode = "123456"
  val nonce              = "iamanoncevalue"

  def stubMfaAccessCodeSuccess(mfaId: MfaId)(implicit encryptedJson: EncryptedJson): Unit = {
    val session = UserSession(TestContext.sessionIdForloggedInDeveloper, LoggedInState.LOGGED_IN, TestContext.developer)

    stubFor(
      post(urlEqualTo("/authenticate-mfa"))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(AccessCodeAuthenticationRequest("john.smith@example.com".toLaxEmail, accessCode, nonce, mfaId)).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(session).toString())
        )
    )
  }

  def stubMfaAuthAppNameChange(developer: User, mfaId: MfaId, authAppName: String): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/name"))
        .withRequestBody(equalTo(Json.toJson(ChangeMfaNameRequest(authAppName)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def setupVerificationOfAccessCode(developer: User, mfaId: MfaId): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/verification"))
        .withRequestBody(equalTo(Json.toJson(VerifyMfaCodeRequest(accessCode)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def stubRemoveMfaById(developer: User, mfaId: MfaId): Unit = {
    stubFor(
      delete(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId"))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def stubSendSms(developer: User, mfaId: MfaId): Unit = {
    stubFor(
      post(urlEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/send-sms"))
        .willReturn(aResponse()
          .withStatus(OK))
    )
  }

  def setupSmsAccessCode(developer: User, mfaId: MfaId, mobileNumber: String): Unit = {

    stubFor(
      post(urlEqualTo(s"/developer/${developer.userId.value}/mfa/sms"))
        .withRequestBody(equalToJson(Json.toJson(CreateMfaSmsRequest(mobileNumber)).toString()))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterSmsResponse(mfaId, mobileNumber)).toString()))
    )
  }

  def setupGettingMfaSecret(developer: User, mfaId: MfaId): Unit = {

    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/auth-app"))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterAuthAppResponse("mySecret", mfaId)).toString()))
    )
  }

  def stubUpliftAuthSession(isMfaMandated: Boolean) = {
    val sessionId = if (isMfaMandated) TestContext.sessionIdForMfaMandatingUser else TestContext.sessionIdForloggedInDeveloper
    val session   = UserSession(sessionId, LoggedInState.LOGGED_IN, TestContext.developer)

    Stubs.setupPutRequest(s"/session/$sessionId/loggedInState/LOGGED_IN", OK, Json.toJson(session).toString())
  }

  def setupMfaMandated() = {
    val session = UserSession(TestContext.sessionIdForMfaMandatingUser, LoggedInState.LOGGED_IN, TestContext.developer)

    Stubs.setupRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK)
  }
}
