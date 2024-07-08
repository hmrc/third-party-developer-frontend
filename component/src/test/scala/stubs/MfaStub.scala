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
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsSuccessResponse}
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.{ChangeMfaNameRequest, CreateMfaSmsRequest}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.Developer
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{AccessCodeAuthenticationRequest, VerifyMfaRequest}

object MfaStub {

  private val accessCode = "123456"
  val nonce              = "iamanoncevalue"

  def stubMfaAccessCodeSuccess(mfaId: MfaId)(implicit encryptedJson: EncryptedJson): Unit = {
    val session = Session(TestContext.sessionIdForloggedInDeveloper, TestContext.developer, LoggedInState.LOGGED_IN)

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

  def stubMfaAuthAppNameChange(developer: Developer, mfaId: MfaId, authAppName: String): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/name"))
        .withRequestBody(equalTo(Json.toJson(ChangeMfaNameRequest(authAppName)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def setupVerificationOfAccessCode(developer: Developer, mfaId: MfaId): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/verification"))
        .withRequestBody(equalTo(Json.toJson(VerifyMfaRequest(accessCode)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def stubRemoveMfaById(developer: Developer, mfaId: MfaId): Unit = {
    stubFor(
      delete(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId"))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT))
    )
  }

  def stubSendSms(developer: Developer, mfaId: MfaId): Unit = {
    stubFor(
      post(urlEqualTo(s"/developer/${developer.userId.value}/mfa/$mfaId/send-sms"))
        .willReturn(aResponse()
          .withStatus(OK))
    )
  }

  def setupSmsAccessCode(developer: Developer, mfaId: MfaId, mobileNumber: String): Unit = {
    import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.registerSmsSuccessResponseFormat

    stubFor(
      post(urlEqualTo(s"/developer/${developer.userId.value}/mfa/sms"))
        .withRequestBody(equalToJson(Json.toJson(CreateMfaSmsRequest(mobileNumber)).toString()))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterSmsSuccessResponse(mfaId, mobileNumber)).toString()))
    )
  }

  def setupGettingMfaSecret(developer: Developer, mfaId: MfaId): Unit = {
    import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.registerAuthAppResponseFormat

    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/auth-app"))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterAuthAppResponse(mfaId, "mySecret")).toString()))
    )
  }

  def stubUpliftAuthSession(isMfaMandated: Boolean) = {
    val sessionId = if (isMfaMandated) TestContext.sessionIdForMfaMandatingUser else TestContext.sessionIdForloggedInDeveloper
    val session   = Session(sessionId, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupPutRequest(s"/session/$sessionId/loggedInState/LOGGED_IN", OK, Json.toJson(session).toString())
  }

  def setupMfaMandated() = {
    val session = Session(TestContext.sessionIdForMfaMandatingUser, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK)
  }
}
