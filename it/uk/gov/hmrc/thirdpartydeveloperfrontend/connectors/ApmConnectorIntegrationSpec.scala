package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.APICategoryDisplayDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.CombinedApiJsonFormatters
import play.api.libs.json.Json

class ApmConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions with CombinedApiJsonFormatters {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-microservice.port" -> stubPort
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()
    val underTest = app.injector.instanceOf[ApmConnector]

    def combinedApiByServiceName(serviceName: String, body: String) = {
      stubFor(
        get(urlEqualTo(s"/combined-rest-xml-apis/$serviceName"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader("content-type", "application/json")
          )
      )
    }

    def fetchApiDefinitionsVisibleToUser(userId: UserId, body: String) = {
      stubFor(
        get(urlEqualTo(s"/combined-api-definitions?developerId=${userId.asText}"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader("content-type", "application/json")
          )
      )
    }
  }

  "fetchAllAPICategories" should {
    val category1 = APICategoryDisplayDetails("CATEGORY_1", "Category 1")
    val category2 = APICategoryDisplayDetails("CATEGORY_2", "Category 2")

    "return all API Category details" in new Setup {
      stubFor(
        get(urlEqualTo("/api-categories"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(Seq(category1, category2))
          )
      )

      val result = await(underTest.fetchAllAPICategories())

      result.size should be(2)
      result should contain only (category1, category2)
    }
  }

  "fetchAllCombinedAPICategories" should {
    val category1 = APICategoryDisplayDetails("CATEGORY_1", "Category 1")
    val category2 = APICategoryDisplayDetails("CATEGORY_2", "Category 2")

    "return all API Category details" in new Setup {
      stubFor(
        get(urlEqualTo("/api-categories/combined"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(Seq(category1, category2))
          )
      )

      val result: Either[Throwable, List[APICategoryDisplayDetails]] = await(underTest.fetchAllCombinedAPICategories())
      result match {
        case Right(x) => x.size should be(2)
          x should contain only (category1, category2)
        case _        => fail()
      }

    }
  }

  "fetchCombinedApi" should {
    "retrieve an CombinedApi based on a serviceName" in new Setup {
      val serviceName = "api1"
      val displayName = "API 1"
      val expectedApi = CombinedApi(serviceName, displayName, List(CombinedApiCategory("VAT")), REST_API)
      combinedApiByServiceName(serviceName, Json.toJson(expectedApi).toString())
      val result: Either[Throwable, CombinedApi] = await(underTest.fetchCombinedApi("api1"))
      result match {
        case Right(x) => x.serviceName shouldBe serviceName
          x.displayName shouldBe displayName
        case _        => fail()
      }

    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {

      stubFor(
        get(urlEqualTo("/combined-rest-xml-apis/developer/api1"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      val result = await(underTest.fetchCombinedApi("api1"))
      result match {
        case Right(_)                       => fail()
        case Left(_: UpstreamErrorResponse) => succeed
        case _                              => fail()
      }
    }

    "throw notfound when the api is not found" in new Setup {
      stubFor(
        get(urlEqualTo("/combined-rest-xml-apis/developer/unknownapi"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
              .withHeader("Content-Type", "application/json")
          )
      )

      val result = await(underTest.fetchCombinedApi("unknownapi"))
      result match {
        case Right(_)                       => fail()
        case Left(e: UpstreamErrorResponse) => e.statusCode shouldBe NOT_FOUND
        case _                              => fail()
      }

    }
  }

  "fetchApiDefinitionsVisibleToUser" should {

    "retrieve a list of service a user can see" in new Setup {
      val userId = UserId.random
      val serviceName = "api1"
      val name = "API 1"
      fetchApiDefinitionsVisibleToUser(userId, s"""[{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context", "categories": ["AGENT", "VAT"] }]""")
      val result: Seq[ApiDefinition] = await(underTest.fetchApiDefinitionsVisibleToUser(userId))
      result.head.serviceName shouldBe serviceName
      result.head.name shouldBe name
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      val userId = UserId.random
      stubFor(
        get(urlEqualTo(s"/combined-api-definitions?developerId=${userId.asText}"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchApiDefinitionsVisibleToUser(userId))
      }
    }
  }

  "subscribe to api" should {
    val applicationId = ApplicationId.random
    val apiIdentifier = ApiIdentifier(ApiContext("app1"), ApiVersion("2.0"))
    import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._
    val url = s"/applications/${applicationId.value}/subscriptions"

    "subscribe application to an api" in new Setup {
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(apiIdentifier)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = await(underTest.subscribeToApi(applicationId, apiIdentifier))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      stubFor(
        post(urlPathEqualTo(url))
          .withJsonRequestBody(apiIdentifier)
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
      intercept[ApplicationNotFound](
        await(underTest.subscribeToApi(applicationId, apiIdentifier))
      )
    }
  }

}
