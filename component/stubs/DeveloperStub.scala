package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, equalToJson, get, post, postRequestedFor, stubFor, urlEqualTo, urlMatching, urlPathEqualTo, verify}
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.{FindUserIdRequest, FindUserIdResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector.JsonFormatters.FindUserIdRequestWrites
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.PasswordResetRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, Registration, UpdateProfileRequest, UserId}
import utils.ComponentTestDeveloperBuilder

object DeveloperStub extends ComponentTestDeveloperBuilder {


  def register(registration: Registration, status: Int)(implicit encryptedJson: EncryptedJson) =
    stubFor(
      post(urlMatching(s"/developer"))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(registration).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def update(userId: UserId, profile: UpdateProfileRequest, status: Int) =
    stubFor(
      post(urlMatching(s"/developer/${userId.value}"))
        .withRequestBody(equalToJson(Json.toJson(profile).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def setupResend(email: String, status: Int) = {
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
      post(urlPathEqualTo(s"/${userId.value}/resend-verification"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyResetPassword(request: PasswordResetRequest) = {
    verify(1, postRequestedFor(urlPathEqualTo("/password-reset-request")).withRequestBody(equalToJson(Json.toJson(request).toString())))
  }

  def findUserIdByEmailAddress(emailAddress: String) = {

    stubFor(
      post(urlEqualTo("/developers/find-user-id"))
        .withRequestBody(equalToJson(Json.toJson(FindUserIdRequest(emailAddress)).toString()))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(s"""{"userId":"${staticUserId.asText}"}""")
        )
    )
  }

  def stubResetPasswordJourney(email: String,code: String) {
    fetchEmailForResetCode(email, code)
    resetPassword()
  }

  def stubResetPasswordJourneyFail() {
    stubFor(
      get(urlPathEqualTo("/reset-password"))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
        )
    )
  }


  def setupGettingDeveloperByEmail(developer: Developer): Unit = {
    stubFor(get(urlPathEqualTo("/developer"))
      .withQueryParam("developerId", equalTo(developer.userId.asText))
      .willReturn(aResponse()
        .withStatus(OK)
        .withBody(Json.toJson(developer).toString())))
  }

  def setUpGetCombinedApis(): Unit = {
    stubFor(get(urlPathEqualTo("/api-categories/combined"))
      .willReturn(aResponse()
        .withStatus(OK)
        .withBody("[]")))
  }

  def fetchEmailForResetCode(email: String,code: String) = {
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
