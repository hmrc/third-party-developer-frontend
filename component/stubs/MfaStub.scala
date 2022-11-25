package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, delete, equalTo, equalToJson, post, stubFor, urlEqualTo, urlPathEqualTo}
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.Json
import steps.TestContext
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.{ChangeMfaNameRequest, CreateMfaSmsRequest}
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsResponse}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{AccessCodeAuthenticationRequest, VerifyMfaRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson

object MfaStub {

  private val accessCode = "123456"
  val nonce = "iamanoncevalue"

  def stubMfaAccessCodeSuccess(mfaId: MfaId)(implicit encryptedJson: EncryptedJson): Unit = {
    val session = Session(TestContext.sessionIdForloggedInDeveloper, TestContext.developer, LoggedInState.LOGGED_IN)

    stubFor(
      post(urlEqualTo("/authenticate-mfa"))
      .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(AccessCodeAuthenticationRequest("john.smith@example.com", accessCode, nonce, mfaId)).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(session).toString())
        )
    )
  }

  def stubMfaAuthAppNameChange(developer: Developer, mfaId: MfaId, authAppName: String): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/${mfaId.value}/name"))
        .withRequestBody(equalTo(Json.toJson(ChangeMfaNameRequest(authAppName)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT)
        ))
  }

  def setupVerificationOfAccessCode(developer: Developer, mfaId: MfaId): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/${mfaId.value}/verification"))
        .withRequestBody(equalTo(Json.toJson(VerifyMfaRequest(accessCode)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT)
        ))
  }

  def stubRemoveMfaById(developer: Developer, mfaId: MfaId): Unit = {
    stubFor(
      delete(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/${mfaId.value}"))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT)
        ))
  }

  def setupSmsAccessCode(developer: Developer, mfaId: MfaId, mobileNumber: String): Unit = {
    import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.registerSmsResponseFormat

    stubFor(
      post(urlEqualTo(s"/developer/${developer.userId.value}/mfa/sms"))
        .withRequestBody(equalToJson(Json.toJson(CreateMfaSmsRequest(mobileNumber)).toString()))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterSmsResponse(mfaId, mobileNumber)).toString())))
  }

  def setupGettingMfaSecret(developer: Developer, mfaId: MfaId): Unit = {
    import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.registerAuthAppResponseFormat

    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/auth-app"))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(RegisterAuthAppResponse(mfaId, "mySecret")).toString())))
  }


  def stubUpliftAuthSession(isMfaMandated: Boolean) ={
    val sessionId = if(isMfaMandated) TestContext.sessionIdForMfaMandatingUser else TestContext.sessionIdForloggedInDeveloper
    val session = Session(sessionId, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupPutRequest(s"/session/$sessionId/loggedInState/LOGGED_IN", OK, Json.toJson(session).toString())
  }

  def setupMfaMandated() ={
    val session = Session(TestContext.sessionIdForMfaMandatingUser, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK)
  }
}