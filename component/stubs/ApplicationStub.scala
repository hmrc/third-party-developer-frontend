package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, delete, equalTo, equalToJson, get, post, stubFor, urlEqualTo, urlPathEqualTo}
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ApplicationToken, ApplicationWithSubscriptionIds, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

object ApplicationStub {
  def setupApplicationNameValidation() = {
    val validNameResult = ApplicationNameValidationResult(None)

    Stubs.setupPostRequest("/application/name/validate", OK, Json.toJson(validNameResult).toString)
  }

  def setUpFetchApplication(id: ApplicationId, status: Int, response: String = "") = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}"))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
  }

  def setUpFetchEmptySubscriptions(id: ApplicationId, status: Int) = {
    stubFor(
      get(urlEqualTo(s"/application/${id.value}/subscription"))
        .willReturn(aResponse().withStatus(status).withBody("[]"))
    )
  }

  def setUpDeleteSubscription(id: ApplicationId, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      delete(urlEqualTo(s"/application/${id.value}/subscription?context=$api&version=${version.value}"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpExecuteSubscription(id: ApplicationId, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/subscription"))
        .withRequestBody(equalToJson(Json.toJson(ApiIdentifier(ApiContext(api), version)).toString()))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpUpdateApproval(id: ApplicationId) = {
    stubFor(
      post(urlEqualTo(s"/application/${id.value}/check-information"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  def configureUserApplications(userId: UserId, applications: List[ApplicationWithSubscriptionIds] = Nil, status: Int = OK) = {
    import play.api.libs.json.Json

    import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._

    implicit val writes = Json.writes[ApplicationWithSubscriptionIds]

    def stubResponse(environment: Environment, applications: List[ApplicationWithSubscriptionIds]) = {
      stubFor(
        get(urlPathEqualTo("/developer/applications"))
          .withQueryParam("userId", equalTo(userId.asText))
          .withQueryParam("environment", equalTo(environment.toString))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(applications).toString())
          )
      )
    }

    val (prodApps, sandboxApps) = applications.partition(_.deployedTo == Environment.PRODUCTION)

    stubResponse(Environment.PRODUCTION, prodApps)
    stubResponse(Environment.SANDBOX, sandboxApps)

    stubFor(
      get(urlPathEqualTo("/api-definitions/all"))
        .withQueryParam("environment", equalTo("SANDBOX"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody("{}")
        )
    )

    val apisUpliftable = Set.empty[ApiIdentifier]

    stubFor(
      get(urlPathEqualTo("/api-definitions/upliftable"))
        .willReturn(
          aResponse()
            .withBody(Json.toJson(apisUpliftable).toString)
        )
    )
  }

  def configureApplicationCredentials(tokens: Map[String, ApplicationToken], status: Int = OK) = {
    tokens.foreach { entry =>
      stubFor(
        get(urlEqualTo(s"/application/${entry._1}/credentials"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(entry._2).toString())
              .withHeader("content-type", "application/json")
          )
      )
    }
  }
}

