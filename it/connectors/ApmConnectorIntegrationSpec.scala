package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import domain.models.connectors.ApiDefinition
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.connectors.ExtendedApiDefinition
import uk.gov.hmrc.http.UpstreamErrorResponse
import domain.models.developers.UserId

class ApmConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {
  private val stubConfig = Configuration(
    "Test.microservice.services.api-platform-microservice.port" -> stubPort
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

    def extendedAPIDefinitionByServiceName(serviceName: String, body: String) = {
        stubFor(
            get(urlEqualTo(s"/combined-api-definitions/$serviceName"))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withBody(body)
            .withHeader("content-type", "application/json")))
    }

    def fetchApiDefinitionsVisibleToUser(userId: UserId, body: String) = {
      stubFor(
        get(urlEqualTo(s"/combined-api-definitions?developerId=${userId.asText}"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(body)
              .withHeader("content-type", "application/json")))
    }
  }

  "fetchAPIDefinition" should {
      "retrieve an ApiDefinition based on a serviceName" in new Setup {
          val serviceName = "api1"
          val name = "API 1"
         extendedAPIDefinitionByServiceName(serviceName, s"""{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context", "categories": [ "VAT" ] }""")
         val result: ExtendedApiDefinition = await(underTest.fetchAPIDefinition("api1"))
         result.serviceName shouldBe serviceName
         result.name shouldBe name
      }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {

      stubFor(
        get(urlEqualTo("/combined-api-definitions/api1"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchAPIDefinition("api1"))
      }
    }

      "throw notfound when the api is not found" in new Setup {
        stubFor(
          post(urlEqualTo("/combined-api-definitions/unknownapi"))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withHeader("Content-Type", "application/json")
            )
        )

        intercept[UpstreamErrorResponse](await(underTest.fetchAPIDefinition("unknownapi"))).statusCode shouldBe NOT_FOUND
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
}
