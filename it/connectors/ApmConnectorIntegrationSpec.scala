package connectors

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.client.WireMock._
import domain.models.connectors.{ApiDefinition, ExtendedApiDefinition}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream5xxResponse}

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

    def fetchApiDefinitionsVisibleToUser(email: String, body: String) = {
      val encodedEmail = URLEncoder.encode(email, "UTF-8")
      stubFor(
        get(urlEqualTo(s"/combined-api-definitions?collaboratorEmail=$encodedEmail"))
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
         extendedAPIDefinitionByServiceName(serviceName, s"""{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context" """)
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

      intercept[Upstream5xxResponse] {
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

        intercept[NotFoundException](await(underTest.fetchAPIDefinition("unknownapi")))
      }
  }


  "fetchApiDefinitionsVisibleToUser" should {
    "retrieve a list of service a user can see" in new Setup {
      val userEmail = "bob.user@digital.hmrc.gov.uk"
      val serviceName = "api1"
      val name = "API 1"
      fetchApiDefinitionsVisibleToUser(userEmail, s"""[{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context", "categories": ["AGENT", "VAT"] }]""")
      val result: Seq[ApiDefinition] = await(underTest.fetchApiDefinitionsVisibleToUser(userEmail))
      result.head.serviceName shouldBe serviceName
      result.head.name shouldBe name
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      val userEmail = "bob.user@digital.hmrc.gov.uk"
      val encodedEmail = URLEncoder.encode(userEmail, "UTF-8")
      stubFor(
        get(urlEqualTo(s"/combined-api-definitions?collaboratorEmail=$encodedEmail"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      intercept[Upstream5xxResponse] {
        await(underTest.fetchApiDefinitionsVisibleToUser(userEmail))
      }
    }
  }
}
