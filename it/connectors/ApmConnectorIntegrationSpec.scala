package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import domain.models.connectors.ExtendedAPIDefinition
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier

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
  }

  "fetchAPIDefinition" should {
      "retrieve an ExtendedApiDefinition based on a serviceName" in new Setup {
          val serviceName = "api1"
          val name = "API 1"
         extendedAPIDefinitionByServiceName(serviceName, s"""{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context" }""")
         val result: ExtendedAPIDefinition = await(underTest.fetchAPIDefinition("api1"))
         result.serviceName shouldBe serviceName
         result.name shouldBe name
      }
  }
}
