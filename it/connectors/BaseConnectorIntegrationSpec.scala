package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.scalatest._
import org.scalatestplus.play.WsScalaTestClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

trait BaseConnectorIntegrationSpec
  extends WordSpec
    with Matchers
    with OptionValues
    with WsScalaTestClient
    with DefaultAwaitTimeout
    with FutureAwaits
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  val stubPort = sys.env.getOrElse("WIREMOCK", "22222").toInt
  val stubHost = "localhost"
  val wireMockUrl = s"http://$stubHost:$stubPort"
  val wireMockServer = new WireMockServer(wireMockConfig().port(stubPort))

  override def beforeAll() {
    wireMockServer.start()
    WireMock.configureFor(stubHost, stubPort)
  }

  override def afterAll() {
    wireMockServer.stop()
  }

  override def beforeEach() {
    wireMockServer.resetMappings()
  }

}
