package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, equalToJson, get, post, put, stubFor, urlEqualTo, urlPathEqualTo}
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.Json
import steps.{MfaSecret, TestContext}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{TotpAuthenticationRequest, VerifyMfaRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session}

object MfaStub {

  private val accessCode = "123456"

  def stubAuthenticateTotpSuccess(): Unit = {
    val session = Session(TestContext.sessionIdForloggedInDeveloper, TestContext.developer, LoggedInState.LOGGED_IN)

    stubFor(
      post(urlEqualTo("/authenticate-totp"))
        .withRequestBody(equalToJson(Json.toJson(TotpAuthenticationRequest("emailAddress", accessCode, "nonce")).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(session).toString())
        )
    )
  }

  def setupVerificationOfAccessCode(developer: Developer): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/verification"))
        .withRequestBody(equalTo(Json.toJson(VerifyMfaRequest(accessCode)).toString()))
        .willReturn(aResponse()
          .withStatus(NO_CONTENT)
        ))
  }

  def setupEnablingMfa(developer: Developer): Unit = {
    stubFor(
      put(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa/enable"))
        .willReturn(aResponse()
          .withStatus(OK)
        ))
  }

  def setupGettingMfaSecret(developer: Developer): Unit = {
    stubFor(
      post(urlPathEqualTo(s"/developer/${developer.userId.value}/mfa"))
        .willReturn(aResponse()
          .withStatus(OK)
          .withBody(Json.toJson(MfaSecret("mySecret")).toString())))
  }

  def setupGettingDeveloperByEmail(developer: Developer): Unit = {
    stubFor(get(urlPathEqualTo("/developer"))
      .withQueryParam("developerId", equalTo(developer.userId.asText))
      .willReturn(aResponse()
        .withStatus(OK)
        .withBody(Json.toJson(developer).toString())))
  }

  def setupMfaMandated() ={
    val session = Session(TestContext.sessionIdForMfaMandatingUser, TestContext.developer, LoggedInState.LOGGED_IN)

    Stubs.setupRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/${TestContext.sessionIdForMfaMandatingUser}", OK)
  }
}